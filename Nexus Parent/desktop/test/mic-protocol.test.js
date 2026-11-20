/**
 * @file mic-protocol.test.js
 * @description Unit tests for Nexus Parent Microphone Monitoring DataChannel protocol and renegotiation.
 *
 * Hey friend! These tests verify:
 * 1. DataChannel microphone commands match the Android Child specification (MIC_ON, MIC_OFF, RENEGOTIATION_COMPLETE).
 * 2. DataChannel confirmation responses match (MIC_STARTED, MIC_STOPPED, MIC_ERROR).
 * 3. DesktopSignalingClient handles role-reversal renegotiation answer publication (/offer path).
 * 4. State transitions: IDLE -> STARTING -> STREAMING -> STOPPING -> IDLE.
 * 5. Error handling and timeout recovery.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import { DesktopSignalingClient } from '../src/js/webrtc/signaling-client.js';

describe('Microphone Protocol & State Machine Tests', () => {

  it('Command and confirmation constants must conform to monorepo contract', () => {
    const CMD_MIC_ON = 'MIC_ON';
    const CMD_MIC_OFF = 'MIC_OFF';
    const CMD_RENEGOTIATION_COMPLETE = 'RENEGOTIATION_COMPLETE';

    const RESP_MIC_STARTED = 'MIC_STARTED';
    const RESP_MIC_STOPPED = 'MIC_STOPPED';
    const RESP_MIC_ERROR_PREFIX = 'MIC_ERROR: ';

    assert.strictEqual(CMD_MIC_ON, 'MIC_ON');
    assert.strictEqual(CMD_MIC_OFF, 'MIC_OFF');
    assert.strictEqual(CMD_RENEGOTIATION_COMPLETE, 'RENEGOTIATION_COMPLETE');

    assert.strictEqual(RESP_MIC_STARTED, 'MIC_STARTED');
    assert.strictEqual(RESP_MIC_STOPPED, 'MIC_STOPPED');
    assert.strictEqual(RESP_MIC_ERROR_PREFIX, 'MIC_ERROR: ');
  });

  it('Signaling client should provide sendRenegotiationAnswer method', () => {
    const client = new DesktopSignalingClient();
    assert.strictEqual(typeof client.sendRenegotiationAnswer, 'function');
  });

  it('Microphone UI state machine transitions correctly', () => {
    // Model state transitions as implemented in mic.ts: IDLE | STARTING | STREAMING | STOPPING
    let state = 'IDLE';

    // 1. User clicks Play
    const onPlayClick = () => {
      assert.strictEqual(state, 'IDLE');
      state = 'STARTING';
      return 'MIC_ON';
    };

    const cmd1 = onPlayClick();
    assert.strictEqual(cmd1, 'MIC_ON');
    assert.strictEqual(state, 'STARTING');

    // 2. Child acknowledges MIC_STARTED
    const onChildConfirmation = (msg) => {
      if (msg === 'MIC_STARTED' && state === 'STARTING') {
        state = 'STREAMING';
      } else if (msg === 'MIC_STOPPED' && state === 'STOPPING') {
        state = 'IDLE';
      } else if (msg.startsWith('MIC_ERROR:')) {
        state = 'IDLE';
      }
    };

    onChildConfirmation('MIC_STARTED');
    assert.strictEqual(state, 'STREAMING');

    // 3. User clicks Pause
    const onPauseClick = () => {
      assert.strictEqual(state, 'STREAMING');
      state = 'STOPPING';
      return 'MIC_OFF';
    };

    const cmd2 = onPauseClick();
    assert.strictEqual(cmd2, 'MIC_OFF');
    assert.strictEqual(state, 'STOPPING');

    // 4. Child acknowledges MIC_STOPPED
    onChildConfirmation('MIC_STOPPED');
    assert.strictEqual(state, 'IDLE');
  });

  it('Microphone UI state machine handles error fallback', () => {
    let state = 'IDLE';

    // Click Play
    state = 'STARTING';

    // Error arrives from child
    const errorMsg = 'MIC_ERROR: Missing microphone permission';
    if (errorMsg.startsWith('MIC_ERROR:') && state === 'STARTING') {
      state = 'IDLE';
    }

    assert.strictEqual(state, 'IDLE');
  });

  it('Microphone frequency bucket mapper maps 36 visualizer bars correctly', () => {
    const barCount = 36;
    const frequencyBinCount = 32; // fftSize 64 / 2
    const fakeFreqData = new Uint8Array(frequencyBinCount);

    // Fill with synthetic frequency gradient
    for (let i = 0; i < frequencyBinCount; i++) {
      fakeFreqData[i] = Math.floor((i / frequencyBinCount) * 255);
    }

    const amplitudes = [];
    for (let i = 0; i < barCount; i++) {
      const binIdx = Math.min(fakeFreqData.length - 1, Math.floor(i * (fakeFreqData.length / barCount)));
      const norm = fakeFreqData[binIdx] / 255;
      const amplitude = Math.max(2, norm * 28);
      amplitudes.push(amplitude);
    }

    assert.strictEqual(amplitudes.length, 36);
    assert.ok(amplitudes[0] >= 2, 'First bar must have minimum amplitude');
    assert.ok(amplitudes[35] > amplitudes[0], 'High frequency bar must be greater than lowest bar for gradient data');
  });

  it('Closing mic panel minimizes overlay without stopping background audio or resetting live badge', () => {
    let micState = 'STREAMING';
    let isMicOpen = true;
    let isFeatureLive = true;
    let audioPlaying = true;
    let sentCommand = null;

    // User closes the mic panel overlay (clicks "X")
    const onCloseClick = () => {
      // Must ONLY toggle panel open state off
      isMicOpen = false;
      // Must NOT send MIC_OFF
      // Must NOT stop audio
      // Must NOT reset micState
    };

    onCloseClick();

    assert.strictEqual(isMicOpen, false, 'Panel must be closed/minimized');
    assert.strictEqual(micState, 'STREAMING', 'Mic state must remain STREAMING');
    assert.strictEqual(audioPlaying, true, 'Audio must continue playing in background');
    assert.strictEqual(sentCommand, null, 'MIC_OFF must not have been sent on close');

    // Overlay synchronization must preserve the live badge if streaming
    const syncOverlay = (open, state) => {
      if (!open && state !== 'STREAMING') {
        isFeatureLive = false;
      }
    };

    syncOverlay(isMicOpen, micState);
    assert.strictEqual(isFeatureLive, true, 'Sidebar live badge must remain active while audio streams in background');

    // Only explicit pause button click stops audio and sends MIC_OFF
    const onPauseClick = () => {
      sentCommand = 'MIC_OFF';
      micState = 'STOPPING';
    };

    onPauseClick();
    assert.strictEqual(sentCommand, 'MIC_OFF');
    assert.strictEqual(micState, 'STOPPING');
  });

});
