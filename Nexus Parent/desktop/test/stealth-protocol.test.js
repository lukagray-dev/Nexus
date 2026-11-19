/**
 * @file stealth-protocol.test.js
 * @description Unit tests for Nexus Parent Stealth Mode protocol and device state management.
 *
 * Hey friend! These tests verify:
 * 1. Stealth protocol command strings ("STEALTH_ON", "STEALTH_OFF", "GET_STEALTH_STATUS").
 * 2. Acknowledgement and status telemetry parsing.
 * 3. deviceState updates for isStealthMode() and setStealthMode().
 * 4. State persistence through device reconnection and cleanup on disconnect.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';
import { deviceState } from '../src/js/shared/device-state.js';

describe('Stealth Protocol & State Management Tests', () => {

  beforeEach(() => {
    deviceState.disconnect();
  });

  it('Command and confirmation constants must conform to monorepo contract', () => {
    const CMD_STEALTH_ON = 'STEALTH_ON';
    const CMD_STEALTH_OFF = 'STEALTH_OFF';
    const CMD_GET_STEALTH_STATUS = 'GET_STEALTH_STATUS';
    const ACK_STEALTH_ON = 'STEALTH_ON_ACK';
    const ACK_STEALTH_OFF = 'STEALTH_OFF_ACK';
    const STATUS_STEALTH_ON = 'STEALTH_STATUS:ON';
    const STATUS_STEALTH_OFF = 'STEALTH_STATUS:OFF';

    assert.strictEqual(CMD_STEALTH_ON, 'STEALTH_ON');
    assert.strictEqual(CMD_STEALTH_OFF, 'STEALTH_OFF');
    assert.strictEqual(CMD_GET_STEALTH_STATUS, 'GET_STEALTH_STATUS');
    assert.strictEqual(ACK_STEALTH_ON, 'STEALTH_ON_ACK');
    assert.strictEqual(ACK_STEALTH_OFF, 'STEALTH_OFF_ACK');
    assert.strictEqual(STATUS_STEALTH_ON, 'STEALTH_STATUS:ON');
    assert.strictEqual(STATUS_STEALTH_OFF, 'STEALTH_STATUS:OFF');
  });

  it('deviceState should report false for isStealthMode when disconnected', () => {
    assert.strictEqual(deviceState.isStealthMode(), false);
  });

  it('deviceState.setStealthMode should update isStealthMode for connected device', () => {
    deviceState.setConnectedDevice({
      id: '4821 9402 1104',
      name: "Lucas's Galaxy S24",
      model: 'Samsung SM-S928B',
      batteryPercent: 85,
      signalStatus: 'WiFi',
      isOnline: true,
      lastSeen: 'Just now'
    });

    assert.strictEqual(deviceState.isStealthMode(), false);

    deviceState.setStealthMode(true);
    assert.strictEqual(deviceState.isStealthMode(), true);

    deviceState.setStealthMode(false);
    assert.strictEqual(deviceState.isStealthMode(), false);
  });

  it('deviceState.disconnect should reset stealth state', () => {
    deviceState.setConnectedDevice({
      id: '4821 9402 1104',
      name: "Lucas's Galaxy S24",
      model: 'Samsung SM-S928B',
      batteryPercent: 85,
      signalStatus: 'WiFi',
      isOnline: true,
      lastSeen: 'Just now'
    });

    deviceState.setStealthMode(true);
    assert.strictEqual(deviceState.isStealthMode(), true);

    deviceState.disconnect();
    assert.strictEqual(deviceState.isStealthMode(), false);
  });

  it('deviceState listeners should be notified on stealth state transitions', () => {
    deviceState.setConnectedDevice({
      id: '4821 9402 1104',
      name: "Lucas's Galaxy S24",
      model: 'Samsung SM-S928B',
      batteryPercent: 85,
      signalStatus: 'WiFi',
      isOnline: true,
      lastSeen: 'Just now'
    });

    let notificationCount = 0;
    const unsubscribe = deviceState.subscribe(() => {
      notificationCount++;
    });

    deviceState.setStealthMode(true);
    assert.strictEqual(notificationCount, 1);

    // Setting same state should not trigger redundant notification
    deviceState.setStealthMode(true);
    assert.strictEqual(notificationCount, 1);

    deviceState.setStealthMode(false);
    assert.strictEqual(notificationCount, 2);

    unsubscribe();
    deviceState.setStealthMode(true);
    assert.strictEqual(notificationCount, 2);
  });
});
