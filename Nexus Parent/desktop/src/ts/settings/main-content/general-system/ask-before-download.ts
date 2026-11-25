/**
 * @file ask-before-download.ts
 * @description Ask Every Time Before Downloading toggle setting for General & System.
 *
 * Hey friend! This setting controls whether Monitor Storage downloads save straight to
 * the configured default directory or prompt the parent every time:
 * - Disabled by default (`false`): first download on a fresh install asks once for a folder,
 *   and every download after that saves directly into `downloadsDirectory`.
 * - Enabled (`true`): disables the default directory picker row and opens the native OS
 *   Save As dialog before every download.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';

/**
 * Renders the "Ask Every Time Before Downloading" toggle setting row.
 */
export function renderAskBeforeDownloadSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('askBeforeDownload');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Ask Every Time Before Downloading</span>
      <span class="settings-label-desc">Always prompt for a save location instead of using the default downloads directory</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-ask-before-download" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const askToggle = row.querySelector<HTMLInputElement>('#setting-ask-before-download');
  askToggle?.addEventListener('change', () => {
    const nextValue = askToggle.checked;
    settingsState.setValue('askBeforeDownload', nextValue);
    showToast(
      nextValue
        ? 'Prompt enabled: you will choose where to save every download'
        : 'Default directory enabled: downloads will save automatically'
    );
  });

  return row;
}
