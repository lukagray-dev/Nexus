/**
 * @file applock-protocol.test.js
 * @description Unit tests for Nexus Parent App Lock protocol, state machine transitions, and record sorting.
 *
 * Hey friend! These tests verify:
 * 1. Protocol command and response string constants conform to monorepo specifications.
 * 2. Monitoring state machine transitions (IDLE -> STARTING -> LIVE -> STOPPING -> IDLE).
 * 3. RightSidebarStateManager manages App Lock attempt logs, snapshots, chunking, and deduplication.
 * 4. Master lock status and lockout states update reactively.
 * 5. Peer disconnection resets monitoring state back to IDLE.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';
import {
  CMD_APPLOCK_ON,
  CMD_APPLOCK_OFF,
  RESP_APPLOCK_STARTED,
  RESP_APPLOCK_STOPPED,
  RESP_APPLOCK_ERROR_PREFIX,
  TYPE_APPLOCK_SNAPSHOT,
  TYPE_APPLOCK_NEW,
  CMD_APPLOCK_TOGGLE,
  CMD_APPLOCK_SET_PIN,
  CMD_APPLOCK_CLEAR,
  CMD_APPLOCK_GET_STATUS,
  RESP_APPLOCK_STATUS,
  RESP_APPLOCK_PIN_CHANGED
} from '../src/js/right-sidebar/types.js';

describe('App Lock Protocol & State Machine Tests', () => {

  beforeEach(() => {
    rightSidebarState.resetMonitoringStates();
    rightSidebarState.clearAppLockRecords();
    rightSidebarState.setAppLockEnabled(false);
    rightSidebarState.setAppLockLockedOut(false);
  });

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_APPLOCK_ON, 'APPLOCK_ON');
    assert.strictEqual(CMD_APPLOCK_OFF, 'APPLOCK_OFF');
    assert.strictEqual(RESP_APPLOCK_STARTED, 'APPLOCK_STARTED');
    assert.strictEqual(RESP_APPLOCK_STOPPED, 'APPLOCK_STOPPED');
    assert.strictEqual(RESP_APPLOCK_ERROR_PREFIX, 'APPLOCK_ERROR: ');
    assert.strictEqual(TYPE_APPLOCK_SNAPSHOT, 'APPLOCK_SNAPSHOT');
    assert.strictEqual(TYPE_APPLOCK_NEW, 'APPLOCK_NEW');
    assert.strictEqual(CMD_APPLOCK_TOGGLE, 'APPLOCK_TOGGLE');
    assert.strictEqual(CMD_APPLOCK_SET_PIN, 'APPLOCK_SET_PIN');
    assert.strictEqual(CMD_APPLOCK_CLEAR, 'APPLOCK_CLEAR');
    assert.strictEqual(CMD_APPLOCK_GET_STATUS, 'APPLOCK_GET_STATUS');
    assert.strictEqual(RESP_APPLOCK_STATUS, 'APPLOCK_STATUS');
    assert.strictEqual(RESP_APPLOCK_PIN_CHANGED, 'APPLOCK_PIN_CHANGED');
  });

  it('App Lock state machine transitions correctly through full cycle', () => {
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'IDLE');

    rightSidebarState.setAppLockMonitoringState('STARTING');
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'STARTING');

    rightSidebarState.setAppLockMonitoringState('LIVE');
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'LIVE');

    rightSidebarState.setAppLockMonitoringState('STOPPING');
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'STOPPING');

    rightSidebarState.setAppLockMonitoringState('IDLE');
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'IDLE');
  });

  it('RightSidebarStateManager manages attempt logs, snapshots, and prepends', () => {
    const mockRecord1 = {
      id: 'att_001',
      timestamp: 1000,
      pinCorrect: false,
      failedAttempts: 1,
      isLockedOut: false,
      selfie: 'data:image/jpeg;base64,abc1'
    };

    const mockRecord2 = {
      id: 'att_002',
      timestamp: 2000,
      pinCorrect: false,
      failedAttempts: 2,
      isLockedOut: false,
      selfie: 'data:image/jpeg;base64,abc2'
    };

    // Test snapshot
    rightSidebarState.setAppLockSnapshot([mockRecord1, mockRecord2]);
    let records = rightSidebarState.getAppLockRecords();
    assert.strictEqual(records.length, 2);
    // Sorted by newest timestamp descending
    assert.strictEqual(records[0].id, 'att_002');
    assert.strictEqual(records[1].id, 'att_001');

    // Test prepend live record
    const mockRecord3 = {
      id: 'att_003',
      timestamp: 3000,
      pinCorrect: true,
      failedAttempts: 0,
      isLockedOut: false,
      selfie: 'data:image/jpeg;base64,abc3'
    };

    rightSidebarState.prependAppLockRecord(mockRecord3);
    records = rightSidebarState.getAppLockRecords();
    assert.strictEqual(records.length, 3);
    assert.strictEqual(records[0].id, 'att_003');

    // Duplicate prevention
    rightSidebarState.prependAppLockRecord(mockRecord3);
    assert.strictEqual(rightSidebarState.getAppLockRecords().length, 3);

    // Clear records
    rightSidebarState.clearAppLockRecords();
    assert.strictEqual(rightSidebarState.getAppLockRecords().length, 0);
  });

  it('Updates lock enabled and lockout state reactively', () => {
    assert.strictEqual(rightSidebarState.isAppLockEnabled(), false);
    assert.strictEqual(rightSidebarState.isAppLockLockedOut(), false);

    rightSidebarState.setAppLockEnabled(true);
    assert.strictEqual(rightSidebarState.isAppLockEnabled(), true);

    rightSidebarState.setAppLockLockedOut(true);
    assert.strictEqual(rightSidebarState.isAppLockLockedOut(), true);
  });

  it('Peer disconnection resets monitoring state to IDLE', () => {
    rightSidebarState.setAppLockMonitoringState('LIVE');
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'LIVE');

    rightSidebarState.resetMonitoringStates();
    assert.strictEqual(rightSidebarState.getAppLockMonitoringState(), 'IDLE');
  });
});
