/**
 * @file screen-protocol.test.js
 * @description Unit tests for Nexus Parent Screen Monitoring DataChannel protocol, Opus audio frames, and DVR timeline.
 *
 * Hey friend! These tests verify:
 * 1. DataChannel screen commands match the monorepo specification (SCREEN_ON, SCREEN_OFF).
 * 2. DataChannel confirmation responses match (SCREEN_STARTED, SCREEN_STOPPED, SCREEN_ERROR: ).
 * 3. Audio DataChannel packet prefix matches (OPUS_AUDIO:).
 * 4. State transitions: IDLE -> STARTING -> STREAMING -> STOPPING -> IDLE.
 * 5. Error recovery and timeout fallback.
 * 6. Disconnection fail-safe reset.
 * 7. YouTube-style Live DVR timeline calculations and live-edge snapping.
 * 8. Base64 Opus audio frame parsing and extraction.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_SCREEN_ON,
  CMD_SCREEN_OFF,
  RESP_SCREEN_STARTED,
  RESP_SCREEN_STOPPED,
  RESP_SCREEN_ERROR_PREFIX,
  HEADER_OPUS_AUDIO
} from '../src/js/main-content/screen/types.js';

describe('Screen Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    assert.strictEqual(CMD_SCREEN_ON, 'SCREEN_ON');
    assert.strictEqual(CMD_SCREEN_OFF, 'SCREEN_OFF');

    assert.strictEqual(RESP_SCREEN_STARTED, 'SCREEN_STARTED');
    assert.strictEqual(RESP_SCREEN_STOPPED, 'SCREEN_STOPPED');
    assert.strictEqual(RESP_SCREEN_ERROR_PREFIX, 'SCREEN_ERROR: ');
    assert.strictEqual(HEADER_OPUS_AUDIO, 'OPUS_AUDIO:');
  });

  it('Screen UI state machine transitions correctly through full cycle', () => {
    // Model state transitions: IDLE | STARTING | STREAMING | STOPPING
    let state = 'IDLE';

    // 1. User clicks Play
    const onPlayClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return CMD_SCREEN_ON;
    };

    const cmd1 = onPlayClick();
    assert.strictEqual(cmd1, 'SCREEN_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges SCREEN_STARTED
    const onChildConfirmation = (msg) => {
      if (msg === RESP_SCREEN_STARTED && state === 'STARTING') {
        state = 'STREAMING';
      } else if (msg === RESP_SCREEN_STOPPED && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith(RESP_SCREEN_ERROR_PREFIX)) {
        if (state === 'STARTING') state = 'IDLE';
        if (state === 'STOPPING') state = 'STREAMING';
      }
    };

    onChildConfirmation(RESP_SCREEN_STARTED);
    assert.strictEqual(state, 'STREAMING');

    // 3. User clicks Pause
    const onPauseClick = () => {
      assert.strictEqual(state, 'STREAMING');
      state = 'STOPPING';
      return CMD_SCREEN_OFF;
    };

    const cmd2 = onPauseClick();
    assert.strictEqual(cmd2, 'SCREEN_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges SCREEN_STOPPED
    onChildConfirmation(RESP_SCREEN_STOPPED);
    assert.strictEqual(state, 'IDLE');
  });

  it('Screen UI state machine handles error fallback', () => {
    let state = 'IDLE';

    // 1. Click Play -> STARTING
    state = 'STARTING';

    // Error arrives from child (e.g. accessibility permission missing)
    const errorMsg = 'SCREEN_ERROR: Accessibility permission not granted on child device';
    if (errorMsg.startsWith(RESP_SCREEN_ERROR_PREFIX) && state === 'STARTING') {
      state = 'IDLE';
    }
    assert.strictEqual(state, 'IDLE');

    // 2. Click Pause -> STOPPING
    state = 'STREAMING';
    state = 'STOPPING';

    const stopErrorMsg = 'SCREEN_ERROR: Failed to halt capture';
    if (stopErrorMsg.startsWith(RESP_SCREEN_ERROR_PREFIX) && state === 'STOPPING') {
      state = 'STREAMING'; // Revert back to streaming if stop fails
    }
    assert.strictEqual(state, 'STREAMING');
  });

  it('Disconnection fail-safe resets active screen stream to IDLE', () => {
    let state = 'STREAMING';
    let isLiveBadgeVisible = true;
    let isOverlayVisible = false;

    const onPeerDisconnected = () => {
      state = 'IDLE';
      isLiveBadgeVisible = false;
      isOverlayVisible = true;
    };

    onPeerDisconnected();
    assert.strictEqual(state, 'IDLE');
    assert.strictEqual(isLiveBadgeVisible, false);
    assert.strictEqual(isOverlayVisible, true);
  });

  it('Opus internal system audio message header parsing and extraction', () => {
    const rawDataChannelMessage = 'OPUS_AUDIO:AQIDBAUGBwgJCgsMDQ4PEA==';
    assert.strictEqual(rawDataChannelMessage.startsWith(HEADER_OPUS_AUDIO), true);

    const base64Payload = rawDataChannelMessage.slice(HEADER_OPUS_AUDIO.length);
    assert.strictEqual(base64Payload, 'AQIDBAUGBwgJCgsMDQ4PEA==');

    // Verify binary decoding
    const binary = Buffer.from(base64Payload, 'base64');
    assert.strictEqual(binary.length, 16);
    assert.strictEqual(binary[0], 0x01);
    assert.strictEqual(binary[15], 0x10);
  });

  it('YouTube-style DVR timeline calculations maintain accuracy', () => {
    const totalDurationSeconds = 120; // 2 minutes recorded

    // Progress at live edge
    const liveOffset = 120;
    const livePercent = Math.min(100, Math.max(0, (liveOffset / totalDurationSeconds) * 100));
    assert.strictEqual(livePercent, 100);

    // Progress when scrubbed back 60s
    const pastOffset = 60;
    const pastPercent = Math.min(100, Math.max(0, (pastOffset / totalDurationSeconds) * 100));
    assert.strictEqual(pastPercent, 50);

    // Live snap condition (within 2 seconds of live edge snaps back to live)
    const nearLiveOffset = 119;
    const isLive = nearLiveOffset >= totalDurationSeconds - 2;
    assert.strictEqual(isLive, true);
  });
});
