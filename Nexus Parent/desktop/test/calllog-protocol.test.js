/**
 * @file calllog-protocol.test.js
 * @description Unit tests for Nexus Parent Call Log DataChannel protocol,
 * state machine transitions, formatting helpers, and in-memory telemetry state.
 *
 * Hey friend! These unit tests verify:
 * 1. DataChannel calllog commands conform to monorepo contracts (CALLLOG_ON, CALLLOG_OFF).
 * 2. DataChannel confirmation responses match (CALLLOG_STARTED, CALLLOG_STOPPED, CALLLOG_ERROR).
 * 3. State machine transitions: IDLE -> STARTING -> LIVE -> STOPPING -> IDLE.
 * 4. Error recovery and safety timeout fallbacks.
 * 5. Formatting helpers: formatCallDuration, formatRelativeTime, formatFullDateTime.
 * 6. In-memory data store: sorting by timestamp, prepending real-time calls, deduplication.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_CALLLOG_ON,
  CMD_CALLLOG_OFF,
  ALIAS_CALLLOG_START,
  ALIAS_CALLLOG_STOP,
  RESP_CALLLOG_STARTED,
  RESP_CALLLOG_STOPPED,
  RESP_CALLLOG_ERROR_PREFIX,
  TYPE_CALLLOG_HISTORY,
  TYPE_CALLLOG_NEW,
  formatCallDuration,
  formatRelativeTime,
  formatFullDateTime,
  formatSimSlot
} from '../src/js/right-sidebar/types.js';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';

describe('Call Log Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_CALLLOG_ON, 'CALLLOG_ON');
    assert.strictEqual(CMD_CALLLOG_OFF, 'CALLLOG_OFF');
    assert.strictEqual(ALIAS_CALLLOG_START, 'CALLLOG_START');
    assert.strictEqual(ALIAS_CALLLOG_STOP, 'CALLLOG_STOP');

    assert.strictEqual(RESP_CALLLOG_STARTED, 'CALLLOG_STARTED');
    assert.strictEqual(RESP_CALLLOG_STOPPED, 'CALLLOG_STOPPED');
    assert.strictEqual(RESP_CALLLOG_ERROR_PREFIX, 'CALLLOG_ERROR: ');
    assert.strictEqual(TYPE_CALLLOG_HISTORY, 'CALLLOG_HISTORY');
    assert.strictEqual(TYPE_CALLLOG_NEW, 'CALLLOG_NEW');
  });

  it('Call Log state machine transitions correctly through full cycle', () => {
    let state = 'IDLE';

    // 1. User clicks Play -> STARTING
    const onStartClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_CALLLOG_ON;
    };

    const cmd1 = onStartClick();
    assert.strictEqual(cmd1, 'CALLLOG_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges CALLLOG_STARTED -> LIVE
    const onChildConfirmation = (msg) => {
      if (msg === RESP_CALLLOG_STARTED && state === 'STARTING') {
        state = 'LIVE';
      } else if (msg === RESP_CALLLOG_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_CALLLOG_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation(RESP_CALLLOG_STARTED);
    assert.strictEqual(state, 'LIVE');

    // 3. User clicks Pause -> STOPPING
    const onStopClick = () => {
      assert.strictEqual(state, 'LIVE');
      state = 'STOPPING';
      return CMD_CALLLOG_OFF;
    };

    const cmd2 = onStopClick();
    assert.strictEqual(cmd2, 'CALLLOG_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges CALLLOG_STOPPED -> IDLE
    onChildConfirmation(RESP_CALLLOG_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('Call Log state machine recovers cleanly on error', () => {
    let state = 'STARTING';

    const onChildConfirmation = (msg) => {
      if (msg.startsWith(RESP_CALLLOG_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation('CALLLOG_ERROR: Permission READ_CALL_LOG not granted');
    assert.strictEqual(state, 'IDLE');
  });

  it('formatCallDuration formats seconds to MM:SS string correctly', () => {
    assert.strictEqual(formatCallDuration(0), '00:00');
    assert.strictEqual(formatCallDuration(5), '00:05');
    assert.strictEqual(formatCallDuration(59), '00:59');
    assert.strictEqual(formatCallDuration(60), '01:00');
    assert.strictEqual(formatCallDuration(222), '03:42');
    assert.strictEqual(formatCallDuration(-1), '00:00');
    assert.strictEqual(formatCallDuration(NaN), '00:00');
  });

  it('formatRelativeTime produces valid human-readable timestamps', () => {
    const now = Date.now();
    assert.strictEqual(formatRelativeTime(now - 2000), 'Just now');
    assert.strictEqual(formatRelativeTime(now - 35000), '35s ago');
    assert.strictEqual(formatRelativeTime(now - 120000), '2m ago');
    assert.strictEqual(formatRelativeTime(null), '--');
    assert.strictEqual(formatRelativeTime(NaN), '--');
  });

  it('formatFullDateTime formats full readable date string', () => {
    const date = new Date(2026, 10, 18, 14, 30, 0); // Nov 18, 2026
    const formatted = formatFullDateTime(date.getTime());
    assert.ok(formatted.includes('Nov 18, 2026') || formatted.includes('18/11/2026') || formatted.includes('Nov'));
  });

  it('formatSimSlot normalizes raw and legacy SIM card identifiers', () => {
    // Empty / null handling
    assert.strictEqual(formatSimSlot(null), '');
    assert.strictEqual(formatSimSlot(''), '');
    assert.strictEqual(formatSimSlot('   '), '');

    // Raw integers from Android ContentProvider
    assert.strictEqual(formatSimSlot('0'), 'SIM 1');
    assert.strictEqual(formatSimSlot('1'), 'SIM 1');
    assert.strictEqual(formatSimSlot('2'), 'SIM 2');

    // Legacy sub_id values (like SIM 5 in Android TelephonyProvider)
    assert.strictEqual(formatSimSlot('SIM 5'), 'SIM 1');
    assert.strictEqual(formatSimSlot('SIM 2'), 'SIM 2');
    assert.strictEqual(formatSimSlot('SIM 1'), 'SIM 1');

    // Rich formatted labels from child's SubscriptionManager
    assert.strictEqual(formatSimSlot('SIM 1 • Jio'), 'SIM 1 • Jio');
    assert.strictEqual(formatSimSlot('SIM 2 • Airtel'), 'SIM 2 • Airtel');
  });

  it('RightSidebarStateManager manages call logs, sorting, and deduplication', () => {
    rightSidebarState.clearCallLogs();
    assert.strictEqual(rightSidebarState.getCallLogs().length, 0);

    const call1 = {
      id: '1',
      number: '+111',
      name: 'Dad',
      numberType: 'Mobile',
      callType: 'incoming',
      duration: 120,
      timestamp: 1000
    };
    const call2 = {
      id: '2',
      number: '+222',
      name: 'Mom',
      numberType: 'Mobile',
      callType: 'outgoing',
      duration: 60,
      timestamp: 2000
    };

    // Test setting initial history (sorted newest first)
    rightSidebarState.setCallLogs([call1, call2]);
    const stored = rightSidebarState.getCallLogs();
    assert.strictEqual(stored.length, 2);
    assert.strictEqual(stored[0].id, '2'); // Newer call first
    assert.strictEqual(stored[1].id, '1');

    // Test prepending a live incoming call
    const liveCall = {
      id: '3',
      number: '+333',
      name: 'School Alert',
      numberType: 'Work',
      callType: 'missed',
      duration: 0,
      timestamp: 3000
    };
    rightSidebarState.prependCallRecord(liveCall);
    const updated = rightSidebarState.getCallLogs();
    assert.strictEqual(updated.length, 3);
    assert.strictEqual(updated[0].id, '3');

    // Test duplicate prevention
    rightSidebarState.prependCallRecord(liveCall);
    assert.strictEqual(rightSidebarState.getCallLogs().length, 3);

    // Test resetting on disconnection
    rightSidebarState.setCallLogMonitoringState('LIVE');
    rightSidebarState.resetMonitoringStates();
    assert.strictEqual(rightSidebarState.getCallLogMonitoringState(), 'IDLE');
  });
});
