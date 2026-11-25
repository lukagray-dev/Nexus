/**
 * @file downloads-directory.ts
 * @description Downloads & Media Save Directory setting row for General & System.
 *
 * Hey friend! Manages the local export path input where downloaded snapshots,
 * DVR video clips, and child storage files are saved.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';

/**
 * Renders the Downloads & Media Save Directory setting row.
 */
export function renderDownloadsDirectorySetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row-vertical';

  const values = settingsState.getValues();

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Downloads & Media Save Directory</span>
      <span class="settings-label-desc">Folder location on your PC where saved photos, recordings, and storage downloads are stored</span>
    </div>
    <div class="settings-file-picker-group">
      <input type="text" id="setting-downloads-dir" class="settings-input" value="${values.downloadsDirectory}" />
      <button type="button" id="btn-browse-downloads" class="settings-btn-inline">
        <img src="assets/icons/settings/folder.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
        Browse
      </button>
    </div>
  `;

  const dirInput = row.querySelector<HTMLInputElement>('#setting-downloads-dir');
  dirInput?.addEventListener('input', () => {
    settingsState.setValue('downloadsDirectory', dirInput.value.trim());
  });

  const browseBtn = row.querySelector<HTMLButtonElement>('#btn-browse-downloads');
  browseBtn?.addEventListener('click', () => {
    showToast('Folder dialog: ~/Downloads/Nexus selected');
  });

  return row;
}
