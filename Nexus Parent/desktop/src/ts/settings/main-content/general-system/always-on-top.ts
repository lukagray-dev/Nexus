/**
 * @file always-on-top.ts
 * @description Always on Top window pinning setting for General & System.
 *
 * Hey friend! Keeps the Nexus Parent desktop window pinned above other windows
 * via Rust's `window.set_always_on_top(enabled)` and persists across restarts.
 */

import { settingsState } from '../../sidebar/state.js';
import { setAlwaysOnTopSetting } from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let lastSyncedAlwaysOnTop: boolean | null = null;

/**
 * Records the value last confirmed by Rust so reactive listeners avoid redundant IPC calls.
 */
export function markAlwaysOnTopSynced(enabled: boolean): void {
  lastSyncedAlwaysOnTop = enabled;
}

/**
 * Syncs `alwaysOnTop` with the Rust native window backend.
 */
export async function applyAlwaysOnTopSetting(enabled: boolean): Promise<boolean> {
  try {
    const ok = await setAlwaysOnTopSetting(enabled);
    if (ok) {
      lastSyncedAlwaysOnTop = enabled;
    }
    return ok;
  } catch (err) {
    console.error('[GeneralSystem:AlwaysOnTop] Failed to update window pinning:', err);
    return false;
  }
}

/**
 * Syncs `alwaysOnTop` to Rust only if it differs from the last confirmed native value.
 */
export async function syncAlwaysOnTopIfChanged(enabled: boolean): Promise<boolean> {
  if (lastSyncedAlwaysOnTop === enabled) {
    return true;
  }
  return await applyAlwaysOnTopSetting(enabled);
}

/**
 * Renders the "Always on Top" toggle setting row.
 */
export function renderAlwaysOnTopSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('alwaysOnTop');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Always on Top</span>
      <span class="settings-label-desc">Keep Nexus Parent floating above other desktop windows</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-always-on-top" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-always-on-top');
  toggle?.addEventListener('change', async () => {
    const nextValue = toggle.checked;
    toggle.disabled = true;

    const ok = await applyAlwaysOnTopSetting(nextValue);
    toggle.disabled = false;

    if (ok) {
      settingsState.setValue('alwaysOnTop', nextValue);
      showToast(
        nextValue
          ? 'Window pinned above other applications'
          : 'Window Always on Top disabled'
      );
    } else {
      toggle.checked = !nextValue;
      showToast('Failed to update Always on Top window state');
    }
  });

  return row;
}
