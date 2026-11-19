/**
 * @file vibrateflash-protocol.test.js
 * @description Unit tests for Nexus Parent Vibrate and Flash Remote Hardware Control DataChannel protocol,
 * command string composition, confirmation parsing, and state tracking.
 *
 * Hey friend! These unit tests verify:
 * 1. Monorepo contract constants for vibration, flash, and combined triggers.
 * 2. Protocol command prefixes: VIBRATE:, FLASH:, VIBRATE_FLASH:.
 * 3. Child confirmation prefixes: VIBRATE_SUCCESS:, FLASH_SUCCESS:, VIBRATE_FLASH_SUCCESS:.
 * 4. State tracking of last executed action and confirmation status.
 * 5. Error status handling.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_VIBRATE_PREFIX,
  CMD_FLASH_PREFIX,
  CMD_VIBRATE_FLASH_PREFIX,
  CMD_STOP_VIBRATE_FLASH,
  RESP_VIBRATE_SUCCESS_PREFIX,
  RESP_VIBRATE_ERROR_PREFIX,
  RESP_FLASH_SUCCESS_PREFIX,
  RESP_FLASH_ERROR_PREFIX,
  RESP_VIBRATE_FLASH_SUCCESS_PREFIX,
  RESP_VIBRATE_FLASH_ERROR_PREFIX
} from '../src/js/right-sidebar/types.js';
import { rightSidebarState } from '../src/js/right-sidebar/state.js';

describe('Vibrate & Flash Protocol & State Tracking Tests', () => {

  it('Command prefixes and confirmation constants conform to monorepo contract', () => {
    assert.strictEqual(CMD_VIBRATE_PREFIX, 'VIBRATE:');
    assert.strictEqual(CMD_FLASH_PREFIX, 'FLASH:');
    assert.strictEqual(CMD_VIBRATE_FLASH_PREFIX, 'VIBRATE_FLASH:');
    assert.strictEqual(CMD_STOP_VIBRATE_FLASH, 'STOP_VIBRATE_FLASH');

    assert.strictEqual(RESP_VIBRATE_SUCCESS_PREFIX, 'VIBRATE_SUCCESS: ');
    assert.strictEqual(RESP_VIBRATE_ERROR_PREFIX, 'VIBRATE_ERROR: ');
    assert.strictEqual(RESP_FLASH_SUCCESS_PREFIX, 'FLASH_SUCCESS: ');
    assert.strictEqual(RESP_FLASH_ERROR_PREFIX, 'FLASH_ERROR: ');
    assert.strictEqual(RESP_VIBRATE_FLASH_SUCCESS_PREFIX, 'VIBRATE_FLASH_SUCCESS: ');
    assert.strictEqual(RESP_VIBRATE_FLASH_ERROR_PREFIX, 'VIBRATE_FLASH_ERROR: ');
  });

  it('Composes command strings correctly for all supported patterns', () => {
    const patterns = ['short', 'double', 'pulse', 'sos'];
    patterns.forEach(p => {
      const cmd = `${CMD_VIBRATE_PREFIX}${p}`;
      assert.strictEqual(cmd, `VIBRATE:${p}`);
    });

    const flashActions = ['on', 'off', 'strobe', 'beacon'];
    flashActions.forEach(a => {
      const cmd = `${CMD_FLASH_PREFIX}${a}`;
      assert.strictEqual(cmd, `FLASH:${a}`);
    });

    const combinedPatterns = ['alert', 'sos', 'pulse'];
    combinedPatterns.forEach(cp => {
      const cmd = `${CMD_VIBRATE_FLASH_PREFIX}${cp}`;
      assert.strictEqual(cmd, `VIBRATE_FLASH:${cp}`);
    });
  });

  it('Updates state when vibration success confirmation arrives', () => {
    rightSidebarState.setVibrateFlashStatus('double', true, "Vibration 'double' executed on child");
    const status = rightSidebarState.getVibrateFlashStatus();

    assert.strictEqual(status.lastAction, 'double');
    assert.strictEqual(status.lastSuccess, true);
    assert.strictEqual(status.statusMessage, "Vibration 'double' executed on child");
    assert.ok(status.timestamp > 0);
  });

  it('Updates state when flashlight action fails', () => {
    const errorMsg = 'Camera torch unavailable';
    rightSidebarState.setVibrateFlashStatus('strobe', false, errorMsg);
    const status = rightSidebarState.getVibrateFlashStatus();

    assert.strictEqual(status.lastAction, 'strobe');
    assert.strictEqual(status.lastSuccess, false);
    assert.strictEqual(status.statusMessage, errorMsg);
  });

  it('Updates state when combined pattern executes successfully', () => {
    rightSidebarState.setVibrateFlashStatus('sos', true, "Combined pattern 'sos' executed");
    const status = rightSidebarState.getVibrateFlashStatus();

    assert.strictEqual(status.lastAction, 'sos');
    assert.strictEqual(status.lastSuccess, true);
    assert.strictEqual(status.statusMessage, "Combined pattern 'sos' executed");
  });
});
