/**
 * @file downloads-directory.ts
 * @description Downloads & Media Save Directory setting row for General & System.
 *
 * Hey friend! Manages the local export folder path where Monitor Storage files
 * and downloaded media are saved:
 * 1. On a fresh install, `downloadsDirectory` starts empty (`''`) so the first
 *    download asks once for a folder and saves it here automatically.
 * 2. Clicking "Browse" opens the native OS folder picker via Rust `pick_downloads_directory`.
 * 3. When "Ask Every Time Before Downloading" is enabled, this row visually disables
 *    while preserving the saved path for when the user turns auto-save back on.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  getDefaultDownloadsDirectory,
  isTauriRuntime,
  pickDownloadsDirectory,
} from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let unsubscribeDirectorySync: (() => void) | null = null;

/**
 * Escapes double quotes and angle brackets when interpolating into an HTML attribute string.
 */
function escapeHtmlAttr(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * Renders the Downloads & Media Save Directory setting row.
 */
export function renderDownloadsDirectorySetting(): HTMLElement {
  // Release any previous subscription from an earlier modal mount so listeners never accumulate.
  if (unsubscribeDirectorySync) {
    unsubscribeDirectorySync();
    unsubscribeDirectorySync = null;
  }

  const row = document.createElement('div');
  const values = settingsState.getValues();
  const isDisabled = values.askBeforeDownload;

  row.id = 'setting-downloads-dir-row';
  row.className = isDisabled ? 'settings-row-vertical is-disabled' : 'settings-row-vertical';

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Downloads & Media Save Directory</span>
      <span class="settings-label-desc">Default folder on your PC where Monitor Storage downloads and saved media are stored</span>
    </div>
    <div class="settings-file-picker-group">
      <input
        type="text"
        id="setting-downloads-dir"
        class="settings-input"
        placeholder="Not set yet (will ask once on first download)"
        value="${escapeHtmlAttr(values.downloadsDirectory)}"
        ${isDisabled ? 'disabled' : ''}
      />
      <button
        type="button"
        id="btn-browse-downloads"
        class="settings-btn-inline"
        ${isDisabled ? 'disabled' : ''}
      >
        <img src="assets/icons/settings/folder.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
        Browse
      </button>
    </div>
  `;

  const dirInput = row.querySelector<HTMLInputElement>('#setting-downloads-dir');
  const browseBtn = row.querySelector<HTMLButtonElement>('#btn-browse-downloads');

  const applyDisabledState = (disabled: boolean): void => {
    row.classList.toggle('is-disabled', disabled);
    if (dirInput) dirInput.disabled = disabled;
    if (browseBtn) browseBtn.disabled = disabled;
  };

  dirInput?.addEventListener('input', () => {
    settingsState.setValue('downloadsDirectory', dirInput.value.trim());
  });

  browseBtn?.addEventListener('click', async () => {
    if (settingsState.getValue('askBeforeDownload')) {
      return;
    }

    if (!isTauriRuntime()) {
      showToast('Native folder picker requires the Nexus Parent desktop app');
      return;
    }

    browseBtn.disabled = true;
    try {
      const currentValue = dirInput?.value.trim() || '';
      const suggestedInitial = currentValue || (await getDefaultDownloadsDirectory()) || undefined;
      const selectedPath = await pickDownloadsDirectory(suggestedInitial);

      if (selectedPath) {
        const normalized = selectedPath.trim();
        if (dirInput) {
          dirInput.value = normalized;
        }
        settingsState.setValue('downloadsDirectory', normalized);
        showToast(`Downloads directory set to: ${normalized}`);
      }
    } catch (err) {
      console.error('[GeneralSystem:DownloadsDirectory] Failed to pick folder:', err);
      showToast('Failed to open folder selection dialog');
    } finally {
      applyDisabledState(settingsState.getValue('askBeforeDownload'));
    }
  });

  // Reactively sync disabled state & path changes (e.g. toggling Ask Every Time or Reset Defaults)
  unsubscribeDirectorySync = settingsState.onValuesChange((updated) => {
    applyDisabledState(updated.askBeforeDownload);
    if (dirInput && dirInput.value.trim() !== updated.downloadsDirectory) {
      dirInput.value = updated.downloadsDirectory;
    }
  });

  return row;
}
