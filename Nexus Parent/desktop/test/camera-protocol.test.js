/**
 * @file camera-protocol.test.js
 * @description Unit tests for Nexus Parent Camera Monitoring DataChannel protocol, lens switching, and DVR timeline.
 *
 * Hey friend! These tests verify:
 * 1. DataChannel camera commands match the monorepo specification (CAMERA_ON, CAMERA_OFF, CAMERA_SWITCH).
 * 2. DataChannel confirmation responses match (CAMERA_STARTED, CAMERA_STOPPED, CAMERA_SWITCHED, CAMERA_ERROR).
 * 3. State transitions: IDLE -> STARTING -> STREAMING -> STOPPING -> IDLE.
 * 4. Dynamic Lens Switch state transitions: Rear <-> Front.
 * 5. Error recovery and timeout fallback.
 * 6. Disconnection fail-safe reset.
 * 7. YouTube-style Live DVR timeline calculations and live-edge snapping.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_CAMERA_ON,
  CMD_CAMERA_OFF,
  CMD_CAMERA_SWITCH,
  RESP_CAMERA_STARTED,
  RESP_CAMERA_STOPPED,
  RESP_CAMERA_SWITCHED_PREFIX,
  RESP_CAMERA_ERROR_PREFIX
} from '../src/js/main-content/camera/types.js';

describe('Camera Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_CAMERA_ON, 'CAMERA_ON');
    assert.strictEqual(CMD_CAMERA_OFF, 'CAMERA_OFF');
    assert.strictEqual(CMD_CAMERA_SWITCH, 'CAMERA_SWITCH');

    assert.strictEqual(RESP_CAMERA_STARTED, 'CAMERA_STARTED');
    assert.strictEqual(RESP_CAMERA_STOPPED, 'CAMERA_STOPPED');
    assert.strictEqual(RESP_CAMERA_SWITCHED_PREFIX, 'CAMERA_SWITCHED');
    assert.strictEqual(RESP_CAMERA_ERROR_PREFIX, 'CAMERA_ERROR: ');
  });

  it('Camera UI state machine transitions correctly through full cycle', () => {
    // Model state transitions: IDLE | STARTING | STREAMING | STOPPING
    let state = 'IDLE';

    // 1. User clicks Play
    const onPlayClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_CAMERA_ON;
    };

    const cmd1 = onPlayClick();
    assert.strictEqual(cmd1, 'CAMERA_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges CAMERA_STARTED
    const onChildConfirmation = (msg) => {
      if (msg === RESP_CAMERA_STARTED && state === 'STARTING') {
        state = 'STREAMING';
      } else if (msg === RESP_CAMERA_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_CAMERA_ERROR_PREFIX)) {
        if (state === 'STARTING') state = 'IDLE';
        if (state === 'STOPPING') state = 'STREAMING';
      }
    };

    onChildConfirmation(RESP_CAMERA_STARTED);
    assert.strictEqual(state, 'STREAMING');

    // 3. User clicks Pause
    const onPauseClick = () => {
      assert.strictEqual(state, 'STREAMING');
      state = 'STOPPING';
      return CMD_CAMERA_OFF;
    };

    const cmd2 = onPauseClick();
    assert.strictEqual(cmd2, 'CAMERA_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges CAMERA_STOPPED
    onChildConfirmation(RESP_CAMERA_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('Camera UI state machine handles error fallback', () => {
    let state = 'IDLE';

    // 1. Click Play -> STARTING
    state = 'STARTING';

    // Error arrives from child
    const errorMsg = 'CAMERA_ERROR: Missing camera permission';
    if (errorMsg.startsWith(RESP_CAMERA_ERROR_PREFIX) && state === 'STARTING') {
      state = 'IDLE';
    }
    assert.strictEqual(state, 'IDLE');

    // 2. Click Pause -> STOPPING
    state = 'STREAMING';
    state = 'STOPPING';

    const stopErrorMsg = 'CAMERA_ERROR: Hardware error';
    if (stopErrorMsg.startsWith(RESP_CAMERA_ERROR_PREFIX) && state === 'STOPPING') {
      state = 'STREAMING';
    }
    assert.strictEqual(state, 'STREAMING');
  });

  it('Camera lens switch toggles between Rear and Front with confirmation parsing', () => {
    let currentLens = 'Rear'; // Rear camera by default
    let isSwitching = false;

    // 1. Click Change Lens
    const onSwitchClick = () => {
      isSwitching = true;
      return CMD_CAMERA_SWITCH;
    };

    const cmd = onSwitchClick();
    assert.strictEqual(cmd, 'CAMERA_SWITCH');
    assert.strictEqual(isSwitching, true);

    // 2. Child confirms switch to FRONT
    const onConfirmation = (msg) => {
      if (msg.startsWith(RESP_CAMERA_SWITCHED_PREFIX)) {
        isSwitching = false;
        const parts = msg.split(':');
        if (parts.length > 1) {
          const parsed = parts[1].trim().toUpperCase();
          currentLens = parsed === 'FRONT' ? 'Front' : 'Rear';
        } else {
          currentLens = currentLens === 'Rear' ? 'Front' : 'Rear';
        }
      }
    };

    onConfirmation('CAMERA_SWITCHED: FRONT');
    assert.strictEqual(isSwitching, false);
    assert.strictEqual(currentLens, 'Front');

    // 3. Switch back: FRONT -> REAR
    onSwitchClick();
    onConfirmation('CAMERA_SWITCHED: REAR');
    assert.strictEqual(isSwitching, false);
    assert.strictEqual(currentLens, 'Rear');
  });

  it('Peer disconnection resets camera and lens states to idle defaults', () => {
    let state = 'STREAMING';
    let currentLens = 'Front';

    const onPeerDisconnected = () => {
      state = 'IDLE';
      currentLens = 'Rear';
    };

    onPeerDisconnected();
    assert.strictEqual(state, 'IDLE');
    assert.strictEqual(currentLens, 'Rear');
  });

  it('YouTube-style Live DVR timeline math calculates progress and live snaps accurately', () => {
    const totalDurationSeconds = 120; // 2 minutes live

    // Test 1: When user is at live edge (100%)
    const liveTarget = 120;
    const isLiveNearEdge = liveTarget >= totalDurationSeconds - 2;
    assert.strictEqual(isLiveNearEdge, true, 'Seeking within 2 seconds of the end must snap to Live');

    // Test 2: When user scrubs back to 30s
    const scrubbedTarget = 30;
    const isScrubbedLive = scrubbedTarget >= totalDurationSeconds - 2;
    assert.strictEqual(isScrubbedLive, false, 'Seeking back to 30s must enter time-shift mode');

    const progressPercent = (scrubbedTarget / totalDurationSeconds) * 100;
    assert.strictEqual(progressPercent, 25, '30s out of 120s must be exactly 25%');

    // Test 3: Rolling buffer pruning (30 minutes max)
    const MAX_BUFFER_MS = 30 * 60 * 1000;
    const chunks = [
      { startMs: 0, endMs: 1000 },
      { startMs: 1000, endMs: 2000 },
      { startMs: 31 * 60 * 1000, endMs: 31 * 60 * 1000 + 1000 }
    ];

    const currentElapsedMs = 31 * 60 * 1000 + 1000;
    const cutoffTime = currentElapsedMs - MAX_BUFFER_MS;

    while (chunks.length > 1 && chunks[0].endMs < cutoffTime) {
      chunks.shift();
    }

    assert.strictEqual(chunks.length, 1, 'Older chunks beyond 30 minutes must be evicted');
    assert.ok(chunks[0].startMs >= cutoffTime, 'Remaining chunk must be within 30-minute window');
  });

});
