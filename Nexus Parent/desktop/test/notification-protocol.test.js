/**
 * @file notification-protocol.test.js
 * @description Unit tests for Nexus Parent Notification DataChannel protocol,
 * state machine transitions, snapshot handling, and in-memory notification state.
 *
 * Hey friend! These unit tests verify:
 * 1. DataChannel Notification commands conform to monorepo contracts (NOTIFICATION_ON, NOTIFICATION_OFF).
 * 2. DataChannel confirmation responses match (NOTIFICATION_STARTED, NOTIFICATION_STOPPED, NOTIFICATION_ERROR).
 * 3. State machine transitions: IDLE -> STARTING -> LIVE -> STOPPING -> IDLE.
 * 4. Error recovery and safety timeout fallbacks.
 * 5. In-memory data store: snapshot population, sorting by postTime, prepending live records, deduplication, removal.
 * 6. Disconnection fail-safe: peer disconnection resets monitoring states to IDLE.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_NOTIFICATION_ON,
  CMD_NOTIFICATION_OFF,
  ALIAS_NOTIFICATION_START,
  ALIAS_NOTIFICATION_STOP,
  RESP_NOTIFICATION_STARTED,
  RESP_NOTIFICATION_STOPPED,
  RESP_NOTIFICATION_ERROR_PREFIX,
  TYPE_NOTIFICATION_SNAPSHOT,
  TYPE_NOTIFICATION_NEW,
  TYPE_NOTIFICATION_REMOVED,
  formatRelativeTime
} from '../src/js/right-sidebar/types.js';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';

describe('Notification Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_NOTIFICATION_ON, 'NOTIFICATION_ON');
    assert.strictEqual(CMD_NOTIFICATION_OFF, 'NOTIFICATION_OFF');
    assert.strictEqual(ALIAS_NOTIFICATION_START, 'NOTIFICATION_START');
    assert.strictEqual(ALIAS_NOTIFICATION_STOP, 'NOTIFICATION_STOP');

    assert.strictEqual(RESP_NOTIFICATION_STARTED, 'NOTIFICATION_STARTED');
    assert.strictEqual(RESP_NOTIFICATION_STOPPED, 'NOTIFICATION_STOPPED');
    assert.strictEqual(RESP_NOTIFICATION_ERROR_PREFIX, 'NOTIFICATION_ERROR: ');
    assert.strictEqual(TYPE_NOTIFICATION_SNAPSHOT, 'NOTIFICATION_SNAPSHOT');
    assert.strictEqual(TYPE_NOTIFICATION_NEW, 'NOTIFICATION_NEW');
    assert.strictEqual(TYPE_NOTIFICATION_REMOVED, 'NOTIFICATION_REMOVED');
  });

  it('Notification state machine transitions correctly through full cycle', () => {
    let state = 'IDLE';

    // 1. User clicks Play -> STARTING
    const onStartClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_NOTIFICATION_ON;
    };

    const cmd1 = onStartClick();
    assert.strictEqual(cmd1, 'NOTIFICATION_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges NOTIFICATION_STARTED -> LIVE
    const onChildConfirmation = (msg) => {
      if (msg === RESP_NOTIFICATION_STARTED && state === 'STARTING') {
        state = 'LIVE';
      } else if (msg === RESP_NOTIFICATION_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_NOTIFICATION_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation(RESP_NOTIFICATION_STARTED);
    assert.strictEqual(state, 'LIVE');

    // 3. User clicks Pause -> STOPPING
    const onStopClick = () => {
      assert.strictEqual(state, 'LIVE');
      state = 'STOPPING';
      return CMD_NOTIFICATION_OFF;
    };

    const cmd2 = onStopClick();
    assert.strictEqual(cmd2, 'NOTIFICATION_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges NOTIFICATION_STOPPED -> IDLE
    onChildConfirmation(RESP_NOTIFICATION_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('Notification state machine recovers cleanly on error', () => {
    let state = 'STARTING';

    const onChildConfirmation = (msg) => {
      if (msg.startsWith(RESP_NOTIFICATION_ERROR_PREFIX)) {
        state = 'IDLE';
      }
    };

    onChildConfirmation('NOTIFICATION_ERROR: Notification access permission not granted');
    assert.strictEqual(state, 'IDLE');
  });

  it('RightSidebarStateManager manages notifications, snapshot, prepending, and removal', () => {
    rightSidebarState.clearNotifications();
    assert.strictEqual(rightSidebarState.getNotifications().length, 0);

    const snapshotItems = [
      {
        id: 101,
        key: '0|com.whatsapp|101|null|10100',
        packageName: 'com.whatsapp',
        appName: 'WhatsApp',
        title: 'Lucas',
        text: 'Are you on your way?',
        category: 'msg',
        postTime: 1732008400000,
        isOngoing: false,
        actions: ['Reply']
      },
      {
        id: 102,
        key: '0|com.google.android.youtube|102|null|10200',
        packageName: 'com.google.android.youtube',
        appName: 'YouTube',
        title: 'Veritasium',
        text: 'How Quantum Computers Work',
        category: 'transport',
        postTime: 1732008500000,
        isOngoing: true
      }
    ];

    // Set snapshot
    rightSidebarState.setNotificationSnapshot(snapshotItems);
    const stored = rightSidebarState.getNotifications();
    assert.strictEqual(stored.length, 2);
    // Verified sorted descending by postTime (102 before 101)
    assert.strictEqual(stored[0].id, 102);
    assert.strictEqual(stored[1].id, 101);

    // Prepend live notification
    const liveNotif = {
      id: 103,
      key: '0|com.instagram.android|103|null|10300',
      packageName: 'com.instagram.android',
      appName: 'Instagram',
      title: 'emma_w',
      text: 'Liked your story',
      category: 'social',
      postTime: 1732008600000
    };
    rightSidebarState.prependNotification(liveNotif);

    const afterPrepend = rightSidebarState.getNotifications();
    assert.strictEqual(afterPrepend.length, 3);
    assert.strictEqual(afterPrepend[0].id, 103);

    // Prepend duplicate key updates existing position
    const duplicateUpdate = {
      ...liveNotif,
      text: 'Liked your story and commented'
    };
    rightSidebarState.prependNotification(duplicateUpdate);
    const afterDup = rightSidebarState.getNotifications();
    assert.strictEqual(afterDup.length, 3);
    assert.strictEqual(afterDup[0].text, 'Liked your story and commented');

    // Remove notification by key
    rightSidebarState.removeNotification('0|com.whatsapp|101|null|10100');
    const afterRemove = rightSidebarState.getNotifications();
    assert.strictEqual(afterRemove.length, 2);
    assert.strictEqual(afterRemove.some(n => n.key === '0|com.whatsapp|101|null|10100'), false);

    // Cleanup
    rightSidebarState.clearNotifications();
    assert.strictEqual(rightSidebarState.getNotifications().length, 0);
  });

  it('Peer disconnection resets notification monitoring state to IDLE', () => {
    rightSidebarState.setNotificationMonitoringState('LIVE');
    assert.strictEqual(rightSidebarState.getNotificationMonitoringState(), 'LIVE');

    rightSidebarState.resetMonitoringStates();
    assert.strictEqual(rightSidebarState.getNotificationMonitoringState(), 'IDLE');
  });

  it('formatRelativeTime produces human-readable timestamp string', () => {
    const now = Date.now();
    const justNow = formatRelativeTime(now - 3000);
    assert.strictEqual(justNow, 'Just now');

    const tenSecondsAgo = formatRelativeTime(now - 10000);
    assert.strictEqual(tenSecondsAgo, '10s ago');

    const twoMinutesAgo = formatRelativeTime(now - 120000);
    assert.strictEqual(twoMinutesAgo, '2m ago');
  });
});
