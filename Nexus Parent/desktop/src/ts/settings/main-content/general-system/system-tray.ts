/**
 * @file system-tray.ts
 * @description Close Button [X] Behavior & System Tray integration for General & System.
 *
 * Hey friend! This setting controls what happens when the user clicks `[X]` or presses `Alt+F4`:
 * - `'tray'` (default): Rust intercepts `CloseRequested`, calls `api.prevent_close()`, and hides
 *   the window to the system tray. All WebRTC streams, DataChannels, Firebase listeners, and
 *   background monitoring keep running uninterrupted.
 * - `'exit'`: Clicking `[X]` terminates the application completely and disconnects cleanly.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  CloseActionMode,
  setCloseActionSetting,
} from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';
import { bindSegmentedGroup } from '../helpers.js';

let lastSyncedCloseAction: CloseActionMode | null = null;

/**
 * Records the value last confirmed by Rust so reactive listeners avoid redundant IPC calls.
 */
export function markCloseActionSynced(action: CloseActionMode): void {
  lastSyncedCloseAction = action;
}

/**
 * Syncs the selected close button action (`tray` or `exit`) with the Rust window manager.
 */
export async function applyCloseActionSetting(action: CloseActionMode): Promise<boolean> {
  const ok = await setCloseActionSetting(action);
  if (ok) {
    lastSyncedCloseAction = action;
  }
  return ok;
}

/**
 * Syncs `closeAction` to Rust only if it differs from the last confirmed native value
 * (for example, after `settingsState.resetToDefaults()`).
 */
export async function syncCloseActionIfChanged(action: CloseActionMode): Promise<boolean> {
  if (lastSyncedCloseAction === action) {
    return true;
  }
  return await applyCloseActionSetting(action);
}

/**
 * Renders the "Close Button [X] Behavior" segmented setting row.
 */
export function renderSystemTraySetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentAction = settingsState.getValue('closeAction');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Close Button [X] Behavior</span>
      <span class="settings-label-desc">Keep active monitoring alive in background tray or terminate app</span>
    </div>
    <div class="settings-segmented-group" id="setting-close-action">
      <button type="button" class="settings-segmented-btn ${currentAction === 'tray' ? 'is-active' : ''}" data-value="tray">Minimize to Tray</button>
      <button type="button" class="settings-segmented-btn ${currentAction === 'exit' ? 'is-active' : ''}" data-value="exit">Exit Application</button>
    </div>
  `;

  bindSegmentedGroup<CloseActionMode>(row, '#setting-close-action', async (selected) => {
    const synced = await applyCloseActionSetting(selected);
    settingsState.setValue('closeAction', selected);

    if (!synced) {
      showToast('Failed to update native close behavior');
      return;
    }

    showToast(
      selected === 'tray'
        ? 'Closing window will minimize to system tray'
        : 'Closing window will exit application'
    );
  });

  return row;
}
