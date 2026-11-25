/**
 * @file start-minimized.ts
 * @description Start Minimized to Tray setting for General & System.
 *
 * Hey friend! Controls whether Nexus Parent launches hidden in the OS system tray on
 * every launch (both OS startup and manual launches), independent of `launchOnStartup`.
 */

import { settingsState } from '../../sidebar/state.js';
import { setStartMinimizedSetting } from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let lastSyncedStartMinimized: boolean | null = null;

/**
 * Records the value last confirmed by Rust so reactive listeners avoid redundant IPC calls.
 */
export function markStartMinimizedSynced(enabled: boolean): void {
  lastSyncedStartMinimized = enabled;
}

/**
 * Syncs `startMinimized` with the Rust window lifecycle backend.
 */
export async function applyStartMinimizedSetting(enabled: boolean): Promise<boolean> {
  const ok = await setStartMinimizedSetting(enabled);
  if (ok) {
    lastSyncedStartMinimized = enabled;
  }
  return ok;
}

/**
 * Syncs `startMinimized` to Rust only if it differs from the last confirmed native value.
 */
export async function syncStartMinimizedIfChanged(enabled: boolean): Promise<boolean> {
  if (lastSyncedStartMinimized === enabled) {
    return true;
  }
  return await applyStartMinimizedSetting(enabled);
}

/**
 * Renders the "Start Minimized to Tray" toggle setting row.
 */
export function renderStartMinimizedSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('startMinimized');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Start Minimized to Tray</span>
      <span class="settings-label-desc">Starts Nexus Parent hidden in the background system tray on every launch</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-start-minimized" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-start-minimized');
  toggle?.addEventListener('change', async () => {
    const nextValue = toggle.checked;
    toggle.disabled = true;

    const ok = await applyStartMinimizedSetting(nextValue);
    toggle.disabled = false;

    if (ok) {
      settingsState.setValue('startMinimized', nextValue);
      showToast(
        nextValue
          ? 'App will start minimized in the system tray'
          : 'App will open its main window on launch'
      );
    } else {
      toggle.checked = !nextValue;
      showToast('Failed to update start minimized setting');
    }
  });

  return row;
}
