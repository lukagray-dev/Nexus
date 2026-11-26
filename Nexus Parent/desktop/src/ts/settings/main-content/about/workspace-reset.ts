/**
 * @file workspace-reset.ts
 * @description Real Local Data & Parent Workspace Reset action with two-step confirmation.
 *
 * Hey friend! Unlike a fake placeholder, executing Reset Workspace here:
 * 1. Disconnects any active WebRTC peer connection (`deviceState.disconnect()`).
 * 2. Clears the remembered paired child device (`deviceState.clearLastConnectedDevice()`).
 * 3. Resets the saved voice changer preset (`saveVoicePresetId('original')`).
 * 4. Restores factory defaults in `settingsState.resetToDefaults()`, which automatically
 *    reconciles Rust tray/autostart/always-on-top/wake-lock and audio runtime pipelines.
 */

import { deviceState } from '../../../shared/device-state.js';
import { showToast } from '../../../shared/toast.js';
import { saveVoicePresetId } from '../../../main-content/mic/voice-presets.js';
import { settingsState } from '../../sidebar/state.js';
import { createSettingsGroup } from '../helpers.js';

export const EVENT_WORKSPACE_RESET = 'nexus-workspace-reset';

/**
 * Executes a full local Parent workspace reset across connection, device memory, and settings.
 */
export function executeWorkspaceReset(): void {
  deviceState.disconnect();
  deviceState.clearLastConnectedDevice();
  saveVoicePresetId('original');
  settingsState.resetToDefaults();

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent(EVENT_WORKSPACE_RESET));
  }
}

/**
 * Renders the Danger Zone workspace reset card inside the About page.
 */
export function renderWorkspaceResetGroup(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';
  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Clear Local Parent Data & Reset Workspace</span>
      <span class="settings-label-desc">
        Disconnects active session, clears paired device history, and restores all factory defaults
      </span>
    </div>
    <button type="button" id="btn-factory-reset" class="settings-btn settings-btn-danger">
      <img src="assets/icons/settings/trash.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 5px;" />
      <span id="btn-factory-reset-label">Reset Workspace</span>
    </button>
  `;

  const resetBtn = row.querySelector<HTMLButtonElement>('#btn-factory-reset');
  const resetLabel = row.querySelector<HTMLElement>('#btn-factory-reset-label');

  let armed = false;
  let disarmTimer: ReturnType<typeof setTimeout> | null = null;

  const setArmedState = (nextArmed: boolean): void => {
    armed = nextArmed;
    if (resetLabel) {
      resetLabel.textContent = nextArmed ? 'Confirm Full Reset?' : 'Reset Workspace';
    }
    resetBtn?.classList.toggle('is-armed', nextArmed);
  };

  resetBtn?.addEventListener('click', () => {
    if (!armed) {
      setArmedState(true);
      if (disarmTimer) clearTimeout(disarmTimer);
      disarmTimer = setTimeout(() => {
        setArmedState(false);
        disarmTimer = null;
      }, 4000);
      return;
    }

    if (disarmTimer) {
      clearTimeout(disarmTimer);
      disarmTimer = null;
    }
    setArmedState(false);
    executeWorkspaceReset();
    showToast('Parent workspace cleared and restored to factory defaults');
  });

  return createSettingsGroup('LOCAL DATA & DANGER ZONE', [row], {
    extraClass: 'settings-group-danger',
    titleColor: '#ef4444',
  });
}
