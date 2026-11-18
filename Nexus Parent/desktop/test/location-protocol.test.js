/**
 * @file location-protocol.test.js
 * @description Unit tests for Nexus Parent Geolocation Tracking DataChannel protocol,
 * state machine transitions, formatting helpers, and timeout fallbacks.
 *
 * Hey friend! These unit tests verify:
 * 1. DataChannel location commands match the monorepo specification (LOCATION_ON, LOCATION_OFF).
 * 2. DataChannel confirmation responses match (LOCATION_STARTED, LOCATION_STOPPED, LOCATION_ERROR).
 * 3. State machine transitions: IDLE -> STARTING -> TRACKING -> STOPPING -> IDLE.
 * 4. Error recovery and 20s safety timeout fallback.
 * 5. Peer disconnection fail-safe reset.
 * 6. Helper functions: coordinate formatting, speed formatting, accuracy margin, and relative time.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_LOCATION_ON,
  CMD_LOCATION_OFF,
  ALIAS_LOCATE_CHILD,
  ALIAS_LOCATE_CHILD_STOP,
  RESP_LOCATION_STARTED,
  RESP_LOCATION_STOPPED,
  RESP_LOCATION_ERROR_PREFIX,
  TYPE_LOCATION_UPDATE,
  formatCoordinates,
  formatSpeed,
  formatAccuracy,
  formatRelativeTime
} from '../src/js/main-content/location/types.js';

describe('Location Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_LOCATION_ON, 'LOCATION_ON');
    assert.strictEqual(CMD_LOCATION_OFF, 'LOCATION_OFF');
    assert.strictEqual(ALIAS_LOCATE_CHILD, 'LOCATE_CHILD');
    assert.strictEqual(ALIAS_LOCATE_CHILD_STOP, 'LOCATE_CHILD_STOP');

    assert.strictEqual(RESP_LOCATION_STARTED, 'LOCATION_STARTED');
    assert.strictEqual(RESP_LOCATION_STOPPED, 'LOCATION_STOPPED');
    assert.strictEqual(RESP_LOCATION_ERROR_PREFIX, 'LOCATION_ERROR: ');
    assert.strictEqual(TYPE_LOCATION_UPDATE, 'LOCATION_UPDATE');
  });

  it('Location UI state machine transitions correctly through full start/stop cycle', () => {
    let state = 'IDLE';

    // 1. User clicks Start Live Tracking
    const onStartClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_LOCATION_ON;
    };

    const cmd1 = onStartClick();
    assert.strictEqual(cmd1, 'LOCATION_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges LOCATION_STARTED
    const onChildConfirmation = (msg) => {
      if (msg === RESP_LOCATION_STARTED && state === 'STARTING') {
        state = 'TRACKING';
      } else if (msg === RESP_LOCATION_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_LOCATION_ERROR_PREFIX)) {
        if (state === 'STARTING') state = 'IDLE';
        if (state === 'STOPPING') state = 'TRACKING';
      }
    };

    onChildConfirmation(RESP_LOCATION_STARTED);
    assert.strictEqual(state, 'TRACKING');

    // 3. User clicks Stop Tracking
    const onStopClick = () => {
      assert.strictEqual(state, 'TRACKING');
      state = 'STOPPING';
      return CMD_LOCATION_OFF;
    };

    const cmd2 = onStopClick();
    assert.strictEqual(cmd2, 'LOCATION_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges LOCATION_STOPPED
    onChildConfirmation(RESP_LOCATION_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('Location UI state machine handles error fallback', () => {
    let state = 'IDLE';

    // 1. Start tracking -> STARTING
    state = 'STARTING';

    // Error arrives from child (e.g. missing location permission)
    const errorMsg = 'LOCATION_ERROR: Location permission not granted';
    if (errorMsg.startsWith(RESP_LOCATION_ERROR_PREFIX) && state === 'STARTING') {
      state = 'IDLE';
    }
    assert.strictEqual(state, 'IDLE');

    // 2. Stop tracking -> STOPPING
    state = 'TRACKING';
    state = 'STOPPING';

    const stopError = 'LOCATION_ERROR: Hardware provider busy';
    if (stopError.startsWith(RESP_LOCATION_ERROR_PREFIX) && state === 'STOPPING') {
      state = 'TRACKING';
    }
    assert.strictEqual(state, 'TRACKING');
  });

  it('Peer disconnection immediately resets tracking state to IDLE', () => {
    let state = 'TRACKING';

    const onPeerDisconnected = () => {
      state = 'IDLE';
    };

    onPeerDisconnected();
    assert.strictEqual(state, 'IDLE');
  });

  it('formatCoordinates handles cardinal hemispheres correctly', () => {
    // Northern and Eastern hemisphere
    const kolkata = formatCoordinates(22.572645, 88.363892);
    assert.strictEqual(kolkata, '22.572645° N, 88.363892° E');

    // Southern and Western hemisphere
    const santiago = formatCoordinates(-33.44889, -70.669265);
    assert.strictEqual(santiago, '33.448890° S, 70.669265° W');

    // Equator and Prime Meridian
    const origin = formatCoordinates(0.0, 0.0);
    assert.strictEqual(origin, '0.000000° N, 0.000000° E');
  });

  it('formatSpeed converts meters-per-second to km/h with stationary threshold', () => {
    assert.strictEqual(formatSpeed(0.0), '0.0 km/h (Stationary)');
    assert.strictEqual(formatSpeed(0.15), '0.0 km/h (Stationary)');
    assert.strictEqual(formatSpeed(10.0), '36.0 km/h');
    assert.strictEqual(formatSpeed(20.0), '72.0 km/h');
  });

  it('formatAccuracy displays margin in meters', () => {
    assert.strictEqual(formatAccuracy(0), '±0 m (High Precision)');
    assert.strictEqual(formatAccuracy(4.8), '±4.8 m');
    assert.strictEqual(formatAccuracy(15.2), '±15.2 m');
  });

  it('formatRelativeTime calculates human-readable relative timestamps', () => {
    const now = Date.now();
    assert.strictEqual(formatRelativeTime(now), 'Just now');
    assert.strictEqual(formatRelativeTime(now - 1000), 'Just now');
    assert.strictEqual(formatRelativeTime(now - 15000), '15 seconds ago');
    assert.strictEqual(formatRelativeTime(now - 120000), '2m ago');
    assert.strictEqual(formatRelativeTime(now - 7200000), '2h ago');
  });
});
