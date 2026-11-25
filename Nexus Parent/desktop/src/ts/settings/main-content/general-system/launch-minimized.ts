/**
 * @file launch-minimized.ts
 * @description Launch Minimized on System Startup setting for General & System.
 *
 * Hey friend! This setting controls real OS startup registration via Rust's
 * `tauri-plugin-autostart` (passing `--minimized` on OS boot):
 * - Disabled by default (`false`).
 * - When enabled, OS boot launches Nexus Parent straight into the system tray.
 * - Manual launches from Desktop or Start Menu still open the window normally.
 * - If OS startup registration fails, the toggle reverts cleanly and notifies the user.
 */

import { settingsState } from '../../sidebar/state.js';
import { setLaunchMinimizedSetting } from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let lastSyncedLaunchMinimized: boolean | null = null;

/**
 * Records the value last confirmed by Rust so reactive listeners avoid redundant IPC calls.
 */
export function markLaunchMinimizedSynced(enabled: boolean): void {
  lastSyncedLaunchMinimized = enabled;
}

/**
 * Syncs the `launchMinimized` preference with the Rust OS autostart backend.
 * Returns true if the native update succeeded (or running outside Tauri).
 */
export async function applyLaunchMinimizedSetting(enabled: boolean): Promise<boolean> {
  try {
    const ok = await setLaunchMinimizedSetting(enabled);
    if (ok) {
      lastSyncedLaunchMinimized = enabled;
    }
    return ok;
  } catch (err) {
    console.error('[GeneralSystem:LaunchMinimized] Failed to update OS autostart:', err);
    return false;
  }
}

/**
 * Syncs `launchMinimized` to Rust only if it differs from the last confirmed native value
 * (for example, after `settingsState.resetToDefaults()`).
 */
export async function syncLaunchMinimizedIfChanged(enabled: boolean): Promise<boolean> {
  if (lastSyncedLaunchMinimized === enabled) {
    return true;
  }
  return await applyLaunchMinimizedSetting(enabled);
}

/**
 * Renders the "Launch Minimized on System Startup" toggle setting row.
 */
export function renderLaunchMinimizedSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('launchMinimized');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Launch Minimized on System Startup</span>
      <span class="settings-label-desc">Starts Nexus Parent in the background system tray when your PC boots</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-launch-minimized" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const launchToggle = row.querySelector<HTMLInputElement>('#setting-launch-minimized');
  launchToggle?.addEventListener('change', async () => {
    const nextValue = launchToggle.checked;
    launchToggle.disabled = true;

    const ok = await applyLaunchMinimizedSetting(nextValue);
    launchToggle.disabled = false;

    if (ok) {
      settingsState.setValue('launchMinimized', nextValue);
      showToast(
        nextValue
          ? 'System startup enabled: app will launch minimized in tray on boot'
          : 'System startup disabled'
      );
    } else {
      // Revert UI state if native OS autostart registration failed
      launchToggle.checked = !nextValue;
      showToast('Failed to update system startup configuration');
    }
  });

  return row;
}
