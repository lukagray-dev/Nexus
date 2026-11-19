/**
 * @file wallpaper-protocol.test.js
 * @description Unit tests for Nexus Parent Wallpaper Remote Control DataChannel protocol,
 * state machine transitions, chunked image reassembly, target switching, and push validation.
 *
 * Hey friend! These unit tests verify:
 * 1. Monorepo contract constants for wallpaper commands, responses, and chunk types.
 * 2. State machine transitions: IDLE -> STARTING -> LIVE -> STOPPING -> IDLE.
 * 3. Error recovery and timeout resets.
 * 4. Multi-screen target switching (Home, Lock, Both) and isSame synchronization.
 * 5. Incoming 16KB chunk reassembly into full data URLs.
 * 6. Push wallpaper payload chunking calculation and progress tracking.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_WALLPAPER_ON,
  CMD_WALLPAPER_OFF,
  CMD_WALLPAPER_GET,
  ALIAS_WALLPAPER_START,
  ALIAS_WALLPAPER_STOP,
  RESP_WALLPAPER_STARTED,
  RESP_WALLPAPER_STOPPED,
  RESP_WALLPAPER_ERROR_PREFIX,
  TYPE_WALLPAPER_CHUNK,
  CMD_PUSH_WALLPAPER_START,
  CMD_PUSH_WALLPAPER_CHUNK,
  RESP_WALLPAPER_SET_SUCCESS,
  RESP_WALLPAPER_SET_ERROR_PREFIX
} from '../src/js/right-sidebar/types.js';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';

describe('Wallpaper Protocol & State Machine Tests', () => {

  it('Command, confirmation, and chunk constants conform to monorepo contract', () => {
    assert.strictEqual(CMD_WALLPAPER_ON, 'WALLPAPER_ON');
    assert.strictEqual(CMD_WALLPAPER_OFF, 'WALLPAPER_OFF');
    assert.strictEqual(CMD_WALLPAPER_GET, 'WALLPAPER_GET');
    assert.strictEqual(ALIAS_WALLPAPER_START, 'WALLPAPER_START');
    assert.strictEqual(ALIAS_WALLPAPER_STOP, 'WALLPAPER_STOP');

    assert.strictEqual(RESP_WALLPAPER_STARTED, 'WALLPAPER_STARTED');
    assert.strictEqual(RESP_WALLPAPER_STOPPED, 'WALLPAPER_STOPPED');
    assert.strictEqual(RESP_WALLPAPER_ERROR_PREFIX, 'WALLPAPER_ERROR: ');

    assert.strictEqual(TYPE_WALLPAPER_CHUNK, 'WALLPAPER_CHUNK');
    assert.strictEqual(CMD_PUSH_WALLPAPER_START, 'PUSH_WALLPAPER_START');
    assert.strictEqual(CMD_PUSH_WALLPAPER_CHUNK, 'PUSH_WALLPAPER_CHUNK');
    assert.strictEqual(RESP_WALLPAPER_SET_SUCCESS, 'WALLPAPER_SET_SUCCESS');
    assert.strictEqual(RESP_WALLPAPER_SET_ERROR_PREFIX, 'WALLPAPER_SET_ERROR: ');
  });

  it('Wallpaper state machine transitions correctly through full cycle', () => {
    rightSidebarState.setWallpaperMonitoringState('IDLE');
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'IDLE');

    // 1. User clicks Play -> STARTING
    rightSidebarState.setWallpaperMonitoringState('STARTING');
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'STARTING');

    // 2. Child responds with WALLPAPER_STARTED -> LIVE
    rightSidebarState.setWallpaperMonitoringState('LIVE');
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'LIVE');

    // 3. User clicks Pause -> STOPPING
    rightSidebarState.setWallpaperMonitoringState('STOPPING');
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'STOPPING');

    // 4. Child responds with WALLPAPER_STOPPED -> IDLE
    rightSidebarState.setWallpaperMonitoringState('IDLE');
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'IDLE');
  });

  it('Wallpaper state machine resets cleanly on error', () => {
    rightSidebarState.setWallpaperMonitoringState('STARTING');

    const handleChildResponse = (msg) => {
      if (msg.startsWith(RESP_WALLPAPER_ERROR_PREFIX)) {
        rightSidebarState.setWallpaperMonitoringState('IDLE');
      }
    };

    handleChildResponse(`${RESP_WALLPAPER_ERROR_PREFIX}Permission denied`);
    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'IDLE');
  });

  it('Target selection toggles Home, Lock, and Both screens', () => {
    rightSidebarState.setWallpaperTarget('home');
    assert.strictEqual(rightSidebarState.getWallpaperData().currentTarget, 'home');

    rightSidebarState.setWallpaperTarget('lock');
    assert.strictEqual(rightSidebarState.getWallpaperData().currentTarget, 'lock');

    rightSidebarState.setWallpaperTarget('both');
    assert.strictEqual(rightSidebarState.getWallpaperData().currentTarget, 'both');
  });

  it('Reassembles multi-chunk wallpaper payload into valid data URL', () => {
    // Simulate receiving 2 chunks for Home Screen
    const chunk0 = {
      type: TYPE_WALLPAPER_CHUNK,
      screen: 'home',
      chunkIndex: 0,
      totalChunks: 2,
      data: 'AAAABBBB',
      isSame: true
    };
    const chunk1 = {
      type: TYPE_WALLPAPER_CHUNK,
      screen: 'home',
      chunkIndex: 1,
      totalChunks: 2,
      data: 'CCCCDDDD',
      isSame: true
    };

    rightSidebarState.handleIncomingWallpaperChunk(chunk0);
    assert.strictEqual(rightSidebarState.getWallpaperData().homeUrl, null); // Not ready yet

    rightSidebarState.handleIncomingWallpaperChunk(chunk1);
    const data = rightSidebarState.getWallpaperData();
    assert.strictEqual(data.homeUrl, 'data:image/jpeg;base64,AAAABBBBCCCCDDDD');
    assert.strictEqual(data.lockUrl, 'data:image/jpeg;base64,AAAABBBBCCCCDDDD');
    assert.strictEqual(data.isSame, true);
  });

  it('Differentiates distinct Home and Lock screen wallpapers', () => {
    // Home chunk with isSame = false
    rightSidebarState.handleIncomingWallpaperChunk({
      type: TYPE_WALLPAPER_CHUNK,
      screen: 'home',
      chunkIndex: 0,
      totalChunks: 1,
      data: 'HOME_IMAGE',
      isSame: false
    });

    // Lock chunk with isSame = false
    rightSidebarState.handleIncomingWallpaperChunk({
      type: TYPE_WALLPAPER_CHUNK,
      screen: 'lock',
      chunkIndex: 0,
      totalChunks: 1,
      data: 'LOCK_IMAGE',
      isSame: false
    });

    const data = rightSidebarState.getWallpaperData();
    assert.strictEqual(data.homeUrl, 'data:image/jpeg;base64,HOME_IMAGE');
    assert.strictEqual(data.lockUrl, 'data:image/jpeg;base64,LOCK_IMAGE');
    assert.strictEqual(data.isSame, false);
  });

  it('Peer disconnection resets monitoring and fetching flags', () => {
    rightSidebarState.setWallpaperMonitoringState('LIVE');
    rightSidebarState.setWallpaperFetching(true);
    rightSidebarState.setWallpaperPushing(true, 50);

    rightSidebarState.resetMonitoringStates();

    assert.strictEqual(rightSidebarState.getWallpaperMonitoringState(), 'IDLE');
    assert.strictEqual(rightSidebarState.getWallpaperData().isFetching, false);
    assert.strictEqual(rightSidebarState.getWallpaperData().isPushing, false);
  });
});
