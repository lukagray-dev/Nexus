/**
 * @file webrtc-protocol.test.js
 * @description Unit tests for Nexus Parent WebRTC configuration and signaling logic.
 *
 * Hey friend! These tests verify:
 * 1. Firebase configuration integrity and HTTPS database URLs.
 * 2. STUN server endpoints conform to the WebRTC specification.
 * 3. Child ID normalization (stripping spaces, dashes, formatting).
 * 4. Device state formatting and initial offline states.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import { FIREBASE_CONFIG, STUN_SERVERS, DATA_CHANNEL_LABEL } from '../src/js/webrtc/firebase-config.js';
import { DesktopSignalingClient } from '../src/js/webrtc/signaling-client.js';
import { deviceState } from '../src/js/shared/device-state.js';

describe('WebRTC Configuration & Protocol Tests', () => {

  it('FIREBASE_CONFIG should contain valid project parameters', () => {
    assert.ok(FIREBASE_CONFIG.apiKey, 'API key must not be empty');
    assert.ok(FIREBASE_CONFIG.databaseURL.startsWith('https://'), 'Database URL must start with https://');
    assert.strictEqual(FIREBASE_CONFIG.projectId, 'nexus-33');
    assert.strictEqual(FIREBASE_CONFIG.databaseURL, 'https://nexus-33-default-rtdb.firebaseio.com');
  });

  it('STUN_SERVERS should define valid Google STUN endpoints', () => {
    assert.ok(Array.isArray(STUN_SERVERS), 'STUN_SERVERS must be an array');
    assert.ok(STUN_SERVERS.length >= 2, 'Must have at least 2 STUN mirrors');

    for (const server of STUN_SERVERS) {
      assert.ok(server.urls, 'Each entry must have a urls property');
      const urlStr = typeof server.urls === 'string' ? server.urls : server.urls[0];
      assert.ok(urlStr.startsWith('stun:'), `STUN URL must start with stun: - got ${urlStr}`);
    }
  });

  it('DATA_CHANNEL_LABEL must match the Android child configuration', () => {
    assert.strictEqual(DATA_CHANNEL_LABEL, 'nexus-control');
  });

  it('DesktopSignalingClient.normalizeChildId should clean spaces and hyphens', () => {
    const client = new DesktopSignalingClient();
    assert.strictEqual(client.normalizeChildId('4821 9402 1104'), '482194021104');
    assert.strictEqual(client.normalizeChildId('4821-9402-1104'), '482194021104');
    assert.strictEqual(client.normalizeChildId('  4821  9402  1104  '), '482194021104');
  });

  it('deviceState.formatId should correctly format 12-digit strings', () => {
    assert.strictEqual(deviceState.formatId('482194021104'), '4821 9402 1104');
    assert.strictEqual(deviceState.formatId('4821-9402-1104'), '4821 9402 1104');
    assert.strictEqual(deviceState.formatId('123'), '123');
  });

  it('deviceState should start in a disconnected state with null device', () => {
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.getConnectedDevice(), null);
  });

  it('deviceState.disconnect should cleanly reset state', () => {
    deviceState.connect('482194021104');
    assert.strictEqual(deviceState.isConnected(), true);
    assert.ok(deviceState.getConnectedDevice() !== null);

    deviceState.disconnect();
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.getConnectedDevice(), null);
  });

  it('deviceState.formatStorage should format byte values into gigabytes correctly', () => {
    const freeBytes = 64 * 1024 * 1024 * 1024; // 64 GB
    const totalBytes = 256 * 1024 * 1024 * 1024; // 256 GB
    const formatted = deviceState.formatStorage(freeBytes, totalBytes);
    assert.strictEqual(formatted, '64.0 GB free of 256.0 GB');
  });

  it('deviceState.updateTelemetry should incorporate real device name, OS, and storage', () => {
    deviceState.connect('482194021104');
    deviceState.updateTelemetry({
      deviceName: 'Lucas Galaxy S24',
      model: 'SM-S928B',
      manufacturer: 'Samsung',
      androidVersion: '14',
      batteryPercent: 92,
      isCharging: true,
      networkState: 'WiFi',
      freeStorageBytes: 64 * 1024 * 1024 * 1024,
      totalStorageBytes: 256 * 1024 * 1024 * 1024
    });

    const dev = deviceState.getConnectedDevice();
    assert.ok(dev);
    assert.strictEqual(dev.name, 'Lucas Galaxy S24');
    assert.strictEqual(dev.model, 'Samsung SM-S928B');
    assert.strictEqual(dev.androidVersion, '14');
    assert.strictEqual(dev.batteryPercent, 92);
    assert.strictEqual(dev.isCharging, true);
    assert.strictEqual(dev.networkState, 'WiFi');
    assert.strictEqual(dev.storageUsageText, '64.0 GB free of 256.0 GB');
    deviceState.disconnect();
  });

  it('DesktopSignalingClient should support Non-Trickle ICE methods and cleanup', () => {
    const client = new DesktopSignalingClient();
    assert.strictEqual(typeof client.sendOffer, 'function');
    assert.strictEqual(typeof client.listenForAnswer, 'function');
    assert.strictEqual(typeof client.cleanupAnswerListener, 'function');
    assert.strictEqual(typeof client.sendRenegotiationAnswer, 'function');
    assert.strictEqual(typeof client.clearSignalingSession, 'function');
  });

  it('deviceState should manage saving, retrieving, and clearing lastConnectedDevice', () => {
    // Start clean
    deviceState.clearLastConnectedDevice();
    assert.strictEqual(deviceState.getLastConnectedDevice(), null);

    // Save a device
    deviceState.saveLastConnectedDevice({
      rawId: '123456789012',
      formattedId: '1234 5678 9012',
      name: 'Child Galaxy',
      model: 'SM-G998B',
      lastConnectedAt: 1700000000000
    });

    const saved = deviceState.getLastConnectedDevice();
    assert.ok(saved);
    assert.strictEqual(saved.rawId, '123456789012');
    assert.strictEqual(saved.formattedId, '1234 5678 9012');
    assert.strictEqual(saved.name, 'Child Galaxy');
    assert.strictEqual(saved.model, 'SM-G998B');

    // Clear
    deviceState.clearLastConnectedDevice();
    assert.strictEqual(deviceState.getLastConnectedDevice(), null);
  });

  it('deviceState.connect should automatically persist the child device for quick reconnect', () => {
    deviceState.clearLastConnectedDevice();
    deviceState.connect('987654321098');

    const last = deviceState.getLastConnectedDevice();
    assert.ok(last);
    assert.strictEqual(last.rawId, '987654321098');
    assert.strictEqual(last.formattedId, '9876 5432 1098');

    // Disconnecting the active session should preserve the last connected record
    deviceState.disconnect();
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.getConnectedDevice(), null);

    const preserved = deviceState.getLastConnectedDevice();
    assert.ok(preserved);
    assert.strictEqual(preserved.rawId, '987654321098');

    deviceState.clearLastConnectedDevice();
  });

  it('deviceState.updateTelemetry should synchronize device name and model with the last connected device', () => {
    deviceState.connect('987654321098');
    deviceState.updateTelemetry({
      deviceName: 'Sophie Pixel 8',
      model: 'Pixel 8',
      manufacturer: 'Google'
    });

    const last = deviceState.getLastConnectedDevice();
    assert.ok(last);
    assert.strictEqual(last.name, 'Sophie Pixel 8');
    assert.strictEqual(last.model, 'Google Pixel 8');

    deviceState.disconnect();
    deviceState.clearLastConnectedDevice();
  });

  it('deviceState.updateTelemetry should store and persist child update availability flags', () => {
    deviceState.connect('987654321098');
    deviceState.updateTelemetry({
      appVersion: '0.1.0-alpha',
      updateAvailable: true,
      latestAvailableVersion: '0.2.0',
      updateReleaseUrl: 'https://github.com/lukagray-dev/Nexus/releases/tag/v0.2.0'
    });

    const active = deviceState.getConnectedDevice();
    assert.ok(active);
    assert.strictEqual(active.updateAvailable, true);
    assert.strictEqual(active.latestAvailableVersion, '0.2.0');
    assert.strictEqual(active.updateReleaseUrl, 'https://github.com/lukagray-dev/Nexus/releases/tag/v0.2.0');

    const last = deviceState.getLastConnectedDevice();
    assert.ok(last?.lastTelemetry);
    assert.strictEqual(last.lastTelemetry.updateAvailable, true);
    assert.strictEqual(last.lastTelemetry.latestAvailableVersion, '0.2.0');

    // Clearing updateAvailable resets cached flag cleanly
    deviceState.updateTelemetry({
      updateAvailable: false,
      latestAvailableVersion: null,
      updateReleaseUrl: null
    });
    assert.strictEqual(deviceState.getConnectedDevice()?.updateAvailable, false);
    assert.strictEqual(deviceState.getLastConnectedDevice()?.lastTelemetry?.updateAvailable, false);

    deviceState.disconnect();
    deviceState.clearLastConnectedDevice();
  });
});
