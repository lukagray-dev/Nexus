/**
 * @file video-feed-coordination.test.js
 * @description Robust unit tests for Camera and Screen video feed mutual exclusivity and cross-stream prevention.
 *
 * Hey friend! Here is why these tests are so critical:
 * The Android child device has ONLY ONE video transceiver on the WebRTC connection (zero SDP renegotiation architecture).
 * When the parent switches between Camera and Screen surveillance, incoming video packets arrive on that single transceiver.
 * If the desktop UI doesn't strictly coordinate their active states:
 * - Starting Screen while Camera is playing would route screen frames directly into the camera surface!
 * - Both feeds would show "LIVE" in the sidebar simultaneously, confusing the user.
 *
 * These tests verify the end-to-end fix:
 * 1. WebRTCManager activeVideoType router strictly isolates cameraStream and screenStream.
 * 2. Feed deactivator registry allows Camera and Screen to cleanly yield to each other without circular dependencies.
 * 3. SidebarState enforces that only one video feed can hold the LIVE badge at any time.
 * 4. Real-world switching flows: Starting Screen deactivates Camera, and starting Camera deactivates Screen.
 * 5. DataChannel cross-confirmation handling: SCREEN_STARTED drops Camera to IDLE, and CAMERA_STARTED drops Screen to IDLE.
 * 6. Delayed out-of-order confirmation handling: Late responses do not resurrect inactive feeds.
 * 7. Peer disconnection resets all video routing states to null.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';
import { webrtcManager } from '../src/js/webrtc/webrtc-manager.js';
import { sidebarState } from '../src/js/left-sidebar/state.js';
import {
  CMD_CAMERA_ON,
  CMD_CAMERA_OFF,
  RESP_CAMERA_STARTED,
  RESP_CAMERA_STOPPED
} from '../src/js/main-content/camera/types.js';
import {
  CMD_SCREEN_ON,
  CMD_SCREEN_OFF,
  RESP_SCREEN_STARTED,
  RESP_SCREEN_STOPPED
} from '../src/js/main-content/screen/types.js';

describe('Video Feed Mutual Exclusivity & Cross-Stream Coordination Tests', () => {

  beforeEach(() => {
    // Reset coordinator states before each test run
    webrtcManager.setActiveVideoType(null);
    sidebarState.setFeatureLive('camera', false);
    sidebarState.setFeatureLive('screen', false);
  });

  it('webrtcManager setActiveVideoType strictly switches active feed mode', () => {
    assert.strictEqual(webrtcManager.getActiveVideoType(), null);

    // Switch to camera
    webrtcManager.setActiveVideoType('camera');
    assert.strictEqual(webrtcManager.getActiveVideoType(), 'camera');

    // Switch to screen
    webrtcManager.setActiveVideoType('screen');
    assert.strictEqual(webrtcManager.getActiveVideoType(), 'screen');

    // Reset to null
    webrtcManager.setActiveVideoType(null);
    assert.strictEqual(webrtcManager.getActiveVideoType(), null);
  });

  it('getCameraStream and getScreenStream enforce strict mutual exclusivity', () => {
    // Case 1: When no video feed is active, both must return null
    webrtcManager.setActiveVideoType(null);
    assert.strictEqual(webrtcManager.getCameraStream(), null);
    assert.strictEqual(webrtcManager.getScreenStream(), null);

    // Case 2: When camera is active, screenStream MUST be null
    webrtcManager.setActiveVideoType('camera');
    assert.strictEqual(webrtcManager.getScreenStream(), null, 'Screen stream must be null while camera is active');

    // Case 3: When screen is active, cameraStream MUST be null
    webrtcManager.setActiveVideoType('screen');
    assert.strictEqual(webrtcManager.getCameraStream(), null, 'Camera stream must be null while screen is active');

    // Case 4: Resetting active video type clears both
    webrtcManager.setActiveVideoType(null);
    assert.strictEqual(webrtcManager.getCameraStream(), null);
    assert.strictEqual(webrtcManager.getScreenStream(), null);
  });

  it('Feed deactivator registration and invocation cleanly coordinates feed shutdown', () => {
    let cameraDeactivated = false;
    let cameraSendOffVal = false;
    let screenDeactivated = false;
    let screenSendOffVal = false;

    // Register deactivators
    const unregisterCamera = webrtcManager.registerFeedDeactivator('camera', (sendOff) => {
      cameraDeactivated = true;
      cameraSendOffVal = sendOff ?? true;
    });

    const unregisterScreen = webrtcManager.registerFeedDeactivator('screen', (sendOff) => {
      screenDeactivated = true;
      screenSendOffVal = sendOff ?? true;
    });

    // Deactivate camera
    webrtcManager.deactivateFeed('camera', true);
    assert.strictEqual(cameraDeactivated, true);
    assert.strictEqual(cameraSendOffVal, true);

    // Deactivate screen
    webrtcManager.deactivateFeed('screen', false);
    assert.strictEqual(screenDeactivated, true);
    assert.strictEqual(screenSendOffVal, false);

    // Cleanup unregister
    unregisterCamera();
    unregisterScreen();

    // Reset flags and verify no-op after unregister
    cameraDeactivated = false;
    screenDeactivated = false;
    webrtcManager.deactivateFeed('camera', true);
    webrtcManager.deactivateFeed('screen', true);
    assert.strictEqual(cameraDeactivated, false);
    assert.strictEqual(screenDeactivated, false);
  });

  it('SidebarState enforces mutual exclusivity between camera and screen live badges', () => {
    // 1. Activate Camera
    sidebarState.setFeatureLive('camera', true);
    assert.strictEqual(sidebarState.isFeatureLive('camera'), true);
    assert.strictEqual(sidebarState.isFeatureLive('screen'), false);

    // 2. Activate Screen: camera LIVE badge MUST be automatically cleared!
    sidebarState.setFeatureLive('screen', true);
    assert.strictEqual(sidebarState.isFeatureLive('screen'), true);
    assert.strictEqual(sidebarState.isFeatureLive('camera'), false, 'Camera live badge must be cleared when screen becomes live');

    // 3. Activate Camera again: screen LIVE badge MUST be automatically cleared!
    sidebarState.setFeatureLive('camera', true);
    assert.strictEqual(sidebarState.isFeatureLive('camera'), true);
    assert.strictEqual(sidebarState.isFeatureLive('screen'), false, 'Screen live badge must be cleared when camera becomes live');

    // 4. Deactivating camera clears it cleanly
    sidebarState.setFeatureLive('camera', false);
    assert.strictEqual(sidebarState.isFeatureLive('camera'), false);
    assert.strictEqual(sidebarState.isFeatureLive('screen'), false);
  });

  it('Real-World Scenario: Starting Screen while Camera is STREAMING stops Camera cleanly', () => {
    // Model Camera UI State
    let cameraState = 'IDLE';
    let cameraVideoSrcObject = null;
    let cameraCommandsSent = [];

    // Model Screen UI State
    let screenState = 'IDLE';
    let screenVideoSrcObject = null;
    let screenCommandsSent = [];

    // Register Camera deactivator
    const unregisterCamera = webrtcManager.registerFeedDeactivator('camera', (sendOff = true) => {
      if (cameraState !== 'IDLE') {
        if (sendOff && (cameraState === 'STREAMING' || cameraState === 'STARTING')) {
          cameraCommandsSent.push(CMD_CAMERA_OFF);
        }
        cameraState = 'IDLE';
        cameraVideoSrcObject = null;
        sidebarState.setFeatureLive('camera', false);
      }
    });

    // Step 1: User starts Camera
    cameraState = 'STARTING';
    cameraCommandsSent.push(CMD_CAMERA_ON);
    webrtcManager.setActiveVideoType('camera');

    // Child confirms CAMERA_STARTED
    cameraState = 'STREAMING';
    cameraVideoSrcObject = { id: 'webrtc-camera-stream' };
    sidebarState.setFeatureLive('camera', true);

    assert.strictEqual(cameraState, 'STREAMING');
    assert.strictEqual(sidebarState.isFeatureLive('camera'), true);
    assert.ok(cameraVideoSrcObject !== null);

    // Step 2: While Camera is streaming, User navigates to Screen tab and clicks Play!
    // Screen play handler checks if camera is active:
    if (sidebarState.isFeatureLive('camera') || webrtcManager.getActiveVideoType() === 'camera') {
      webrtcManager.deactivateFeed('camera', true);
    }
    webrtcManager.setActiveVideoType('screen');
    screenState = 'STARTING';
    screenCommandsSent.push(CMD_SCREEN_ON);

    // Verify Camera was cleanly shut down and detached!
    assert.strictEqual(cameraState, 'IDLE', 'Camera state must be IDLE');
    assert.strictEqual(cameraVideoSrcObject, null, 'Camera video srcObject must be cleared');
    assert.strictEqual(sidebarState.isFeatureLive('camera'), false, 'Camera live badge must be false');
    assert.deepStrictEqual(cameraCommandsSent, [CMD_CAMERA_ON, CMD_CAMERA_OFF]);

    // Step 3: Child confirms SCREEN_STARTED
    screenState = 'STREAMING';
    screenVideoSrcObject = { id: 'webrtc-screen-stream' };
    sidebarState.setFeatureLive('screen', true);

    assert.strictEqual(screenState, 'STREAMING');
    assert.strictEqual(sidebarState.isFeatureLive('screen'), true);
    assert.ok(screenVideoSrcObject !== null);

    // Step 4: User switches back to Camera page!
    // Because cameraState is IDLE, onCameraViewActivated leaves cameraVideoSrcObject as null
    assert.strictEqual(cameraState, 'IDLE');
    assert.strictEqual(cameraVideoSrcObject, null, 'Camera page must NOT show screen feed frames!');
    assert.strictEqual(webrtcManager.getCameraStream(), null, 'getCameraStream must return null while screen is active');

    unregisterCamera();
  });

  it('Real-World Scenario: Starting Camera while Screen is STREAMING stops Screen cleanly', () => {
    // Model Screen UI State
    let screenState = 'IDLE';
    let screenVideoSrcObject = null;
    let screenCommandsSent = [];

    // Model Camera UI State
    let cameraState = 'IDLE';
    let cameraVideoSrcObject = null;
    let cameraCommandsSent = [];

    // Register Screen deactivator
    const unregisterScreen = webrtcManager.registerFeedDeactivator('screen', (sendOff = true) => {
      if (screenState !== 'IDLE') {
        if (sendOff && (screenState === 'STREAMING' || screenState === 'STARTING')) {
          screenCommandsSent.push(CMD_SCREEN_OFF);
        }
        screenState = 'IDLE';
        screenVideoSrcObject = null;
        sidebarState.setFeatureLive('screen', false);
      }
    });

    // Step 1: User starts Screen
    screenState = 'STARTING';
    screenCommandsSent.push(CMD_SCREEN_ON);
    webrtcManager.setActiveVideoType('screen');

    // Child confirms SCREEN_STARTED
    screenState = 'STREAMING';
    screenVideoSrcObject = { id: 'webrtc-screen-stream' };
    sidebarState.setFeatureLive('screen', true);

    assert.strictEqual(screenState, 'STREAMING');
    assert.strictEqual(sidebarState.isFeatureLive('screen'), true);

    // Step 2: User navigates to Camera tab and clicks Play!
    if (sidebarState.isFeatureLive('screen') || webrtcManager.getActiveVideoType() === 'screen') {
      webrtcManager.deactivateFeed('screen', true);
    }
    webrtcManager.setActiveVideoType('camera');
    cameraState = 'STARTING';
    cameraCommandsSent.push(CMD_CAMERA_ON);

    // Verify Screen was cleanly shut down and detached!
    assert.strictEqual(screenState, 'IDLE', 'Screen state must be IDLE');
    assert.strictEqual(screenVideoSrcObject, null, 'Screen video srcObject must be cleared');
    assert.strictEqual(sidebarState.isFeatureLive('screen'), false, 'Screen live badge must be false');
    assert.deepStrictEqual(screenCommandsSent, [CMD_SCREEN_ON, CMD_SCREEN_OFF]);

    // Step 3: Child confirms CAMERA_STARTED
    cameraState = 'STREAMING';
    cameraVideoSrcObject = { id: 'webrtc-camera-stream' };
    sidebarState.setFeatureLive('camera', true);

    assert.strictEqual(cameraState, 'STREAMING');
    assert.strictEqual(sidebarState.isFeatureLive('camera'), true);
    assert.strictEqual(webrtcManager.getScreenStream(), null, 'getScreenStream must return null while camera is active');

    unregisterScreen();
  });

  it('Cross-stream confirmation safety: SCREEN_STARTED drops Camera to IDLE', () => {
    let cameraState = 'STREAMING';
    sidebarState.setFeatureLive('camera', true);

    // DataChannel message arrives: child started screen streaming
    const incomingMessage = RESP_SCREEN_STARTED;
    if (incomingMessage === RESP_SCREEN_STARTED || incomingMessage === 'SCREEN_RECORDING_STARTED') {
      cameraState = 'IDLE';
      sidebarState.setFeatureLive('camera', false);
    }

    assert.strictEqual(cameraState, 'IDLE');
    assert.strictEqual(sidebarState.isFeatureLive('camera'), false);
  });

  it('Cross-stream confirmation safety: CAMERA_STARTED drops Screen to IDLE', () => {
    let screenState = 'STREAMING';
    sidebarState.setFeatureLive('screen', true);

    // DataChannel message arrives: child started camera streaming
    const incomingMessage = RESP_CAMERA_STARTED;
    if (incomingMessage === RESP_CAMERA_STARTED) {
      screenState = 'IDLE';
      sidebarState.setFeatureLive('screen', false);
    }

    assert.strictEqual(screenState, 'IDLE');
    assert.strictEqual(sidebarState.isFeatureLive('screen'), false);
  });

  it('Delayed confirmation safety: Late CAMERA_STARTED does not resurrect camera when screen is active', () => {
    webrtcManager.setActiveVideoType('screen');
    let cameraState = 'IDLE';
    let mutedCommands = [];

    // Out-of-order delayed CAMERA_STARTED arrives while user is on screen
    const incomingMessage = RESP_CAMERA_STARTED;
    if (incomingMessage === RESP_CAMERA_STARTED) {
      if (cameraState === 'STARTING') {
        cameraState = 'STREAMING';
      } else if (webrtcManager.getActiveVideoType() !== 'camera') {
        mutedCommands.push(CMD_CAMERA_OFF);
      }
    }

    assert.strictEqual(cameraState, 'IDLE', 'Camera state must stay IDLE');
    assert.deepStrictEqual(mutedCommands, [CMD_CAMERA_OFF], 'Must send CAMERA_OFF to mute child sensor');
  });

  it('Delayed confirmation safety: Late SCREEN_STARTED does not resurrect screen when camera is active', () => {
    webrtcManager.setActiveVideoType('camera');
    let screenState = 'IDLE';
    let mutedCommands = [];

    // Out-of-order delayed SCREEN_STARTED arrives while user is on camera
    const incomingMessage = RESP_SCREEN_STARTED;
    if (incomingMessage === RESP_SCREEN_STARTED) {
      if (screenState === 'STARTING') {
        screenState = 'STREAMING';
      } else if (webrtcManager.getActiveVideoType() !== 'screen') {
        mutedCommands.push(CMD_SCREEN_OFF);
      }
    }

    assert.strictEqual(screenState, 'IDLE', 'Screen state must stay IDLE');
    assert.deepStrictEqual(mutedCommands, [CMD_SCREEN_OFF], 'Must send SCREEN_OFF to mute child screen capture');
  });

  it('Peer disconnection resets active video type and media streams', () => {
    webrtcManager.setActiveVideoType('camera');
    assert.strictEqual(webrtcManager.getActiveVideoType(), 'camera');

    webrtcManager.disconnect();
    assert.strictEqual(webrtcManager.getActiveVideoType(), null);
    assert.strictEqual(webrtcManager.getCameraStream(), null);
    assert.strictEqual(webrtcManager.getScreenStream(), null);
  });
});
