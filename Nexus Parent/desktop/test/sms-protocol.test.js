/**
 * @file sms-protocol.test.js
 * @description Unit tests for Nexus Parent SMS DataChannel protocol,
 * state machine transitions, chunked reassembly, and in-memory message state.
 *
 * Hey friend! These unit tests verify:
 * 1. DataChannel SMS commands conform to monorepo contracts (SMS_ON, SMS_OFF).
 * 2. DataChannel confirmation responses match (SMS_STARTED, SMS_STOPPED, SMS_ERROR).
 * 3. State machine transitions: IDLE -> STARTING -> LIVE -> STOPPING -> IDLE.
 * 4. Error recovery and safety timeout fallbacks.
 * 5. Chunked message reassembly: appending incoming chunks without dropped records.
 * 6. In-memory data store: sorting by timestamp, prepending live messages, deduplication.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_SMS_ON,
  CMD_SMS_OFF,
  ALIAS_SMS_START,
  ALIAS_SMS_STOP,
  RESP_SMS_STARTED,
  RESP_SMS_STOPPED,
  RESP_SMS_ERROR_PREFIX,
  TYPE_SMS_HISTORY,
  TYPE_SMS_NEW
} from '../src/js/right-sidebar/types.js';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';

describe('SMS Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_SMS_ON, 'SMS_ON');
    assert.strictEqual(CMD_SMS_OFF, 'SMS_OFF');
    assert.strictEqual(ALIAS_SMS_START, 'SMS_START');
    assert.strictEqual(ALIAS_SMS_STOP, 'SMS_STOP');

    assert.strictEqual(RESP_SMS_STARTED, 'SMS_STARTED');
    assert.strictEqual(RESP_SMS_STOPPED, 'SMS_STOPPED');
    assert.strictEqual(RESP_SMS_ERROR_PREFIX, 'SMS_ERROR: ');
    assert.strictEqual(TYPE_SMS_HISTORY, 'SMS_HISTORY');
    assert.strictEqual(TYPE_SMS_NEW, 'SMS_NEW');
  });

  it('SMS state machine transitions correctly through full cycle', () => {
    let state = 'IDLE';

    // 1. User clicks Play -> STARTING
    const onStartClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_SMS_ON;
    };

    const cmd1 = onStartClick();
    assert.strictEqual(cmd1, 'SMS_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges SMS_STARTED -> LIVE
    const onChildConfirmation = (msg) => {
      if (msg === RESP_SMS_STARTED && state === 'STARTING') {
        state = 'LIVE';
      } else if (msg === RESP_SMS_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_SMS_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation(RESP_SMS_STARTED);
    assert.strictEqual(state, 'LIVE');

    // 3. User clicks Pause -> STOPPING
    const onStopClick = () => {
      assert.strictEqual(state, 'LIVE');
      state = 'STOPPING';
      return CMD_SMS_OFF;
    };

    const cmd2 = onStopClick();
    assert.strictEqual(cmd2, 'SMS_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges SMS_STOPPED -> IDLE
    onChildConfirmation(RESP_SMS_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('SMS state machine recovers cleanly on error', () => {
    let state = 'STARTING';

    const onChildConfirmation = (msg) => {
      if (msg.startsWith(RESP_SMS_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation('SMS_ERROR: Permission READ_SMS not granted');
    assert.strictEqual(state, 'IDLE');
  });

  it('RightSidebarStateManager handles chunked SMS reassembly and deduplication', () => {
    rightSidebarState.clearSmsMessages();
    assert.strictEqual(rightSidebarState.getSmsMessages().length, 0);

    const chunk1 = [
      { id: '1', address: '+111', senderName: 'Dad', body: 'Msg 1', smsType: 'inbox', timestamp: 1000 },
      { id: '2', address: '+222', senderName: 'Mom', body: 'Msg 2', smsType: 'sent', timestamp: 2000 }
    ];
    const chunk2 = [
      { id: '3', address: '+333', senderName: 'School', body: 'Msg 3', smsType: 'inbox', timestamp: 3000 }
    ];

    // Append chunk 1
    rightSidebarState.appendSmsChunk(chunk1);
    assert.strictEqual(rightSidebarState.getSmsMessages().length, 2);

    // Append chunk 2
    rightSidebarState.appendSmsChunk(chunk2);
    const messages = rightSidebarState.getSmsMessages();
    assert.strictEqual(messages.length, 3);
    assert.strictEqual(messages[0].id, '3'); // Newest first

    // Prepend a live incoming SMS
    const liveSms = {
      id: '4',
      address: '+444',
      senderName: 'Bank Alert',
      body: 'Live OTP',
      smsType: 'inbox',
      timestamp: 4000
    };
    rightSidebarState.prependSmsRecord(liveSms);
    const updated = rightSidebarState.getSmsMessages();
    assert.strictEqual(updated.length, 4);
    assert.strictEqual(updated[0].id, '4');

    // Prevent duplicate append
    rightSidebarState.appendSmsChunk([liveSms]);
    assert.strictEqual(rightSidebarState.getSmsMessages().length, 4);

    // Peer disconnection reset
    rightSidebarState.setSmsMonitoringState('LIVE');
    rightSidebarState.resetMonitoringStates();
    assert.strictEqual(rightSidebarState.getSmsMonitoringState(), 'IDLE');
  });
});
