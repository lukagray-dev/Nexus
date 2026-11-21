/**
 * @file reconnect-protocol.test.js
 * @description Comprehensive unit and integration test suite for the Automated Reconnection Mechanism.
 *
 * Hey friend! Here is what this test suite validates thoroughly:
 * 1. Configuration & Delay Progression: Validates the 9-attempt limit and increased delay sequence
 *    (10s, 30s, 60s, 90s, 120s, 150s, 180s, 210s, 240s).
 * 2. State Machine Lifecycle: Tests IDLE -> WAITING -> CONNECTING transitions and countdown ticking.
 * 3. Backoff Escalation: Simulates successive failures to ensure delays increase monotonically on each retry.
 * 4. Cancellation Semantics: Ensures clicking "Cancel" immediately stops all timers and returns to clean empty state.
 * 5. Successful Recovery: Verifies that successful peer reconnection restores connected state and resets retries.
 * 6. 9-Attempt Exhaustion: Ensures the system gracefully stops after 9 failed retries without infinite loops.
 * 7. DeviceState Integration:
 *    - Unexpected connection drops (network glitch) automatically launch auto-reconnect.
 *    - Intentional user disconnects (clicking "Disconnect Device") do NOT trigger auto-reconnect.
 *    - In-flight cancellations prevent delayed connection callbacks from corrupting state.
 */

import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert';
import {
  ReconnectManager,
  reconnectManager,
  MAX_RECONNECT_ATTEMPTS,
  DEFAULT_RECONNECT_DELAYS_SECONDS
} from '../src/js/shared/reconnect-manager.js';
import { deviceState } from '../src/js/shared/device-state.js';
import { webrtcManager } from '../src/js/webrtc/webrtc-manager.js';
import { sidebarState } from '../src/js/left-sidebar/state.js';

describe('Automated Reconnection Protocol & State Machine Tests', () => {

  beforeEach(() => {
    reconnectManager.resetForTesting();
    deviceState.attachReconnectHandlers();
    deviceState.disconnect();
  });

  afterEach(() => {
    reconnectManager.resetForTesting();
    deviceState.attachReconnectHandlers();
    deviceState.disconnect();
  });

  // 1. Configuration & Delay Progression
  it('Constants must enforce 9 attempts and increasing delay progression', () => {
    assert.strictEqual(MAX_RECONNECT_ATTEMPTS, 9, 'Maximum reconnect attempts must be exactly 9');
    assert.strictEqual(DEFAULT_RECONNECT_DELAYS_SECONDS.length, 9, 'Must have delay defined for all 9 attempts');

    // Expected progression specified by user: 10s, 30s, 60s, etc.
    const expected = [10, 30, 60, 90, 120, 150, 180, 210, 240];
    for (let i = 0; i < expected.length; i++) {
      assert.strictEqual(
        DEFAULT_RECONNECT_DELAYS_SECONDS[i],
        expected[i],
        `Attempt ${i + 1} delay must be ${expected[i]}s`
      );
    }

    // Delays must strictly increase
    for (let i = 1; i < DEFAULT_RECONNECT_DELAYS_SECONDS.length; i++) {
      assert.ok(
        DEFAULT_RECONNECT_DELAYS_SECONDS[i] > DEFAULT_RECONNECT_DELAYS_SECONDS[i - 1],
        `Delay for attempt ${i + 1} (${DEFAULT_RECONNECT_DELAYS_SECONDS[i]}s) must be greater than attempt ${i} (${DEFAULT_RECONNECT_DELAYS_SECONDS[i - 1]}s)`
      );
    }
  });

  // 2. Initial State Machine
  it('ReconnectManager starts in a pristine IDLE state', () => {
    const manager = new ReconnectManager();
    const state = manager.getState();

    assert.strictEqual(state.status, 'IDLE');
    assert.strictEqual(state.isActive, false);
    assert.strictEqual(state.currentAttempt, 0);
    assert.strictEqual(state.secondsRemaining, 0);
    assert.strictEqual(state.targetDevice, null);
    assert.strictEqual(manager.isReconnecting(), false);
    assert.strictEqual(manager.getTargetDevice(), null);
  });

  // 3. Starting Reconnection
  it('Starting reconnection initiates Attempt 1 with 10-second delay', () => {
    const manager = new ReconnectManager();
    const target = {
      rawId: '482194021104',
      formattedId: '4821 9402 1104',
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro'
    };

    manager.start(target);

    const state = manager.getState();
    assert.strictEqual(state.status, 'WAITING');
    assert.strictEqual(state.isActive, true);
    assert.strictEqual(state.currentAttempt, 1);
    assert.strictEqual(state.secondsRemaining, 10);
    assert.strictEqual(state.totalDelaySeconds, 10);
    assert.deepStrictEqual(state.targetDevice, target);
    assert.strictEqual(manager.isReconnecting(), true);

    // Clean up timer
    manager.cancel();
  });

  // 4. Duplicate Start Guard
  it('Calling start while reconnection is already running does not duplicate or reset state', () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    manager.start(target);
    manager.tick(); // secondsRemaining drops from 10 to 9
    assert.strictEqual(manager.getState().secondsRemaining, 9);

    // Call start again with same device
    manager.start(target);
    // Should still be at 9, not reset back to 10!
    assert.strictEqual(manager.getState().secondsRemaining, 9);
    assert.strictEqual(manager.getState().currentAttempt, 1);

    manager.cancel();
  });

  // 5. Countdown Ticking
  it('tick() decrements secondsRemaining and notifies listeners', () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let notifyCount = 0;
    manager.subscribe(() => {
      notifyCount++;
    });

    manager.start(target);
    const initialNotifyCount = notifyCount;

    manager.tick(); // 10 -> 9
    assert.strictEqual(manager.getState().secondsRemaining, 9);
    assert.strictEqual(notifyCount, initialNotifyCount + 1);

    manager.tick(); // 9 -> 8
    assert.strictEqual(manager.getState().secondsRemaining, 8);
    assert.strictEqual(notifyCount, initialNotifyCount + 2);

    manager.cancel();
  });

  // 6. Countdown Expiry Triggers Connection Handshake
  it('When countdown timer reaches 0, transitions to CONNECTING and executes attempt', async () => {
    const manager = new ReconnectManager({ delaysSeconds: [2, 4], maxAttempts: 2 });
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let connectionAttempted = false;
    manager.setConnectHandler(async (rawId) => {
      assert.strictEqual(rawId, '482194021104');
      connectionAttempted = true;
      return true;
    });

    manager.start(target);
    assert.strictEqual(manager.getState().secondsRemaining, 2);

    manager.tick(); // 2 -> 1
    assert.strictEqual(manager.getState().secondsRemaining, 1);
    assert.strictEqual(connectionAttempted, false);

    // Tick to 0: triggers executeAttempt!
    manager.tick(); // 1 -> triggers
    await new Promise((resolve) => setTimeout(resolve, 10));

    assert.strictEqual(connectionAttempted, true);
    // Because it returned true, reconnection succeeded and state reset to IDLE
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.isReconnecting(), false);
  });

  // 7. Backoff Progression Across Multiple Failures
  it('Successive failures escalate attempts and backoff delays monotonically', async () => {
    const customDelays = [10, 30, 60, 90, 120, 150, 180, 210, 240];
    const manager = new ReconnectManager({ delaysSeconds: customDelays, maxAttempts: 9 });
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let attemptCount = 0;
    manager.setConnectHandler(async () => {
      attemptCount++;
      return false; // simulate failure
    });

    manager.start(target);
    assert.strictEqual(manager.getState().currentAttempt, 1);
    assert.strictEqual(manager.getState().secondsRemaining, 10);

    // Trigger failure on attempt 1
    await manager.executeAttempt();
    assert.strictEqual(attemptCount, 1);
    assert.strictEqual(manager.getState().currentAttempt, 2);
    assert.strictEqual(manager.getState().secondsRemaining, 30);
    assert.strictEqual(manager.getState().status, 'WAITING');

    // Trigger failure on attempt 2
    await manager.executeAttempt();
    assert.strictEqual(attemptCount, 2);
    assert.strictEqual(manager.getState().currentAttempt, 3);
    assert.strictEqual(manager.getState().secondsRemaining, 60);

    // Trigger failure on attempt 3
    await manager.executeAttempt();
    assert.strictEqual(attemptCount, 3);
    assert.strictEqual(manager.getState().currentAttempt, 4);
    assert.strictEqual(manager.getState().secondsRemaining, 90);

    manager.cancel();
  });

  // 8. Exhaustion After 9 Attempts
  it('Exhausts and resets cleanly after 9 failed reconnection attempts', async () => {
    const manager = new ReconnectManager({
      delaysSeconds: [1, 1, 1, 1, 1, 1, 1, 1, 1],
      maxAttempts: 9
    });
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let exhaustedCalled = false;
    let exhaustedTarget = null;
    manager.onExhausted((tgt) => {
      exhaustedCalled = true;
      exhaustedTarget = tgt;
    });

    manager.setConnectHandler(async () => {
      return false;
    });

    manager.start(target);

    // Fail all 9 attempts
    for (let i = 1; i <= 9; i++) {
      assert.strictEqual(manager.getState().currentAttempt, i);
      await manager.executeAttempt();
    }

    assert.strictEqual(exhaustedCalled, true);
    assert.ok(exhaustedTarget);
    assert.strictEqual(exhaustedTarget.rawId, '482194021104');
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.getState().isActive, false);
    assert.strictEqual(manager.isReconnecting(), false);
  });

  // 9. Cancellation Semantics
  it('cancel() immediately clears timers, resets state to IDLE, and notifies callback', () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let cancelFired = false;
    manager.onCancel(() => {
      cancelFired = true;
    });

    manager.start(target);
    assert.strictEqual(manager.isReconnecting(), true);

    manager.cancel();
    assert.strictEqual(cancelFired, true);
    assert.strictEqual(manager.isReconnecting(), false);
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.getTargetDevice(), null);
  });

  // 10. Immediate Retry Action
  it('retryNow() bypasses remaining delay and initiates immediate connection', async () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let connected = false;
    manager.setConnectHandler(async () => {
      connected = true;
      return true;
    });

    manager.start(target);
    assert.strictEqual(manager.getState().secondsRemaining, 10);

    const result = await manager.retryNow();
    assert.strictEqual(result, true);
    assert.strictEqual(connected, true);
    assert.strictEqual(manager.isReconnecting(), false);
  });

  // 11. Real-World Scenario: Unexpected WebRTC Dropout Triggers Auto-Reconnect
  it('Real-World Scenario: Unexpected WebRTC disconnection automatically triggers auto-reconnect', () => {
    // Parent connects to child phone
    deviceState.connect('482194021104');
    assert.strictEqual(deviceState.isConnected(), true);
    assert.strictEqual(deviceState.isReconnecting(), false);

    // Suddenly connection is lost due to network dropout
    // Notify WebRTC peer connection dropped
    const pc = webrtcManager.getPeerConnection();
    // Simulate onConnectionStateChange handler in deviceState
    // Fire WebRTC state change directly through webrtcManager
    webrtcManager['notifyConnectionStateChange']('disconnected');

    // Device state should be disconnected AND auto-reconnecting!
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.isReconnecting(), true);
    assert.strictEqual(reconnectManager.isReconnecting(), true);

    const target = reconnectManager.getTargetDevice();
    assert.ok(target);
    assert.strictEqual(target.rawId, '482194021104');
    assert.strictEqual(reconnectManager.getState().currentAttempt, 1);
    assert.strictEqual(reconnectManager.getState().secondsRemaining, 10);

    // Cancel cleanup
    reconnectManager.cancel();
  });

  // 12. Real-World Scenario: Manual Disconnect Does NOT Trigger Auto-Reconnect
  it('Real-World Scenario: Intentional user disconnect does NOT trigger auto-reconnect', () => {
    // Parent connects to child phone
    deviceState.connect('482194021104');
    assert.strictEqual(deviceState.isConnected(), true);

    // Parent deliberately clicks "Disconnect Device"
    deviceState.disconnect();

    // Must be disconnected and NOT reconnecting
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.isReconnecting(), false);
    assert.strictEqual(reconnectManager.isReconnecting(), false);
  });

  // 13. Real-World Scenario: In-Flight Cancellation Safety
  it('Real-World Scenario: In-flight cancellation prevents delayed async responses from corrupting state', async () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104' };

    let resolveConnect;
    manager.setConnectHandler(() => {
      return new Promise((res) => {
        resolveConnect = res;
      });
    });

    manager.start(target);

    // Trigger attempt execution
    const attemptPromise = manager.executeAttempt();
    assert.strictEqual(manager.getState().status, 'CONNECTING');

    // User clicks Cancel while connection is in-flight!
    manager.cancel();
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.isReconnecting(), false);

    // Late async resolution arrives
    resolveConnect(true);
    await attemptPromise;

    // State MUST remain IDLE, not resurrected!
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.isReconnecting(), false);
    assert.strictEqual(manager.getTargetDevice(), null);
  });

  // 14. Real-World Scenario: Auto-Reconnect Success Resets Attempts and Restores Connected State
  it('Real-World Scenario: Successful reconnection restores connected state and resets attempt count', async () => {
    const manager = new ReconnectManager();
    const target = { rawId: '482194021104', formattedId: '4821 9402 1104', name: 'Pixel 8 Pro' };

    manager.setConnectHandler(async () => {
      // Connects device in deviceState
      deviceState.connect('482194021104');
      return true;
    });

    manager.start(target);
    assert.strictEqual(manager.isReconnecting(), true);

    const success = await manager.executeAttempt();
    assert.strictEqual(success, true);

    assert.strictEqual(manager.isReconnecting(), false);
    assert.strictEqual(manager.getState().status, 'IDLE');
    assert.strictEqual(manager.getState().currentAttempt, 0);
    assert.strictEqual(deviceState.isConnected(), true);
  });

  // 15. Real-World Scenario: User stays where they were during reconnection; cancel takes them to empty state
  it('Real-World Scenario: Active feature stays preserved during reconnection, and cancellation clears to empty state', () => {
    // 1. Connect child device
    deviceState.connect('482194021104');
    assert.strictEqual(deviceState.isConnected(), true);

    // 2. Unexpected disconnection occurs
    webrtcManager['notifyConnectionStateChange']('disconnected');
    assert.strictEqual(reconnectManager.isReconnecting(), true);

    // 3. User is still in reconnecting state (not redirected to dedicated page)
    assert.strictEqual(reconnectManager.getState().currentAttempt, 1);
    assert.strictEqual(reconnectManager.getState().targetDevice?.rawId, '482194021104');

    // 4. Clicking cancel cancels attempts and resets to empty state
    reconnectManager.cancel();
    assert.strictEqual(reconnectManager.isReconnecting(), false);
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.getConnectedDevice(), null);
  });

  // 16. Real-World Scenario: Active feature selection stays intact during reconnection
  it('Real-World Scenario: Active feature selection stays intact during reconnection', () => {
    // 1. Connect child device and navigate to Camera view
    deviceState.connect('482194021104');
    sidebarState.setActiveFeatureId('camera');
    assert.strictEqual(sidebarState.getActiveFeatureId(), 'camera');

    // 2. Drop connection
    webrtcManager['notifyConnectionStateChange']('disconnected');
    assert.strictEqual(reconnectManager.isReconnecting(), true);

    // 3. Active feature is STILL camera (user was NOT kicked to connection page)
    assert.strictEqual(sidebarState.getActiveFeatureId(), 'camera');

    // 4. Manual disconnect explicitly resets to empty state
    deviceState.disconnect();
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(reconnectManager.isReconnecting(), false);
  });
});
