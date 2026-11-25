/**
 * @file launch-on-startup.ts
 * @description Launch on System Startup setting for General & System.
 *
 * Hey friend! Controls OS login autostart registration via Rust's `tauri-plugin-autostart`,
 * completely independent from whether the window starts minimized in the system tray.
 */

import { settingsState } from '../../sidebar/state.js';
import { setLaunchOnStartupSetting } from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let lastSyncedLaunchOnStartup: boolean | null = null;

/**
 * Records the value last confirmed by Rust so reactive listeners avoid redundant IPC calls.
 */
export function markLaunchOnStartupSynced(enabled: boolean): void {
  lastSyncedLaunchOnStartup = enabled;
}

/**
 * Syncs `launchOnStartup` with the Rust OS autostart backend.
 */
export async function applyLaunchOnStartupSetting(enabled: boolean): Promise<boolean> {
  try {
    const ok = await setLaunchOnStartupSetting(enabled);
    if (ok) {
      lastSyncedLaunchOnStartup = enabled;
    }
    return ok;
  } catch (err) {
    console.error('[GeneralSystem:LaunchOnStartup] Failed to update OS autostart:', err);
    return false;
  }
}

/**
 * Syncs `launchOnStartup` to Rust only if it differs from the last confirmed native value.
 */
export async function syncLaunchOnStartupIfChanged(enabled: boolean): Promise<boolean> {
  if (lastSyncedLaunchOnStartup === enabled) {
    return true;
  }
  return await applyLaunchOnStartupSetting(enabled);
}

/**
 * Renders the "Launch on System Startup" toggle setting row.
 */
export function renderLaunchOnStartupSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('launchOnStartup');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Launch on System Startup</span>
      <span class="settings-label-desc">Automatically starts Nexus Parent when you sign into your computer</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-launch-on-startup" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const startupToggle = row.querySelector<HTMLInputElement>('#setting-launch-on-startup');
  startupToggle?.addEventListener('change', async () => {
    const nextValue = startupToggle.checked;
    startupToggle.disabled = true;

    const ok = await applyLaunchOnStartupSetting(nextValue);
    startupToggle.disabled = false;

    if (ok) {
      settingsState.setValue('launchOnStartup', nextValue);
      showToast(
        nextValue
          ? 'System startup enabled: Nexus Parent will launch when your PC boots'
          : 'System startup disabled'
      );
    } else {
      startupToggle.checked = !nextValue;
      showToast('Failed to update system startup configuration');
    }
  });

  return row;
}
