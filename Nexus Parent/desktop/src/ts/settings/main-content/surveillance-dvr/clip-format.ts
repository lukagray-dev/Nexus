/**
 * @file clip-format.ts
 * @description DVR Clip Export Container format setting.
 *
 * Hey friend! Chooses between MP4 and WebM containers when exporting recorded DVR clips.
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';
import { showToast } from '../../../shared/toast.js';

let unsubscribeClipFormatSync: (() => void) | null = null;

/**
 * Renders the DVR Clip Export Container segmented selector row.
 */
export function renderClipFormatSetting(): HTMLElement {
  if (unsubscribeClipFormatSync) {
    unsubscribeClipFormatSync();
    unsubscribeClipFormatSync = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentFormat = settingsState.getValue('dvrClipFormat');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">DVR Clip Export Container</span>
      <span class="settings-label-desc">Format used when saving downloaded video clips to your PC</span>
    </div>
    <div class="settings-segmented-group" id="setting-clip-format">
      <button type="button" class="settings-segmented-btn ${currentFormat === 'mp4' ? 'is-active' : ''}" data-value="mp4">MP4 (H.264 / AAC)</button>
      <button type="button" class="settings-segmented-btn ${currentFormat === 'webm' ? 'is-active' : ''}" data-value="webm">WebM (Fast Save)</button>
    </div>
  `;

  bindSegmentedGroup<'mp4' | 'webm'>(row, '#setting-clip-format', (val) => {
    settingsState.setValue('dvrClipFormat', val);
    showToast(`DVR clip export container set to ${val.toUpperCase()}`);
  });

  unsubscribeClipFormatSync = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      unsubscribeClipFormatSync?.();
      unsubscribeClipFormatSync = null;
      return;
    }
    const buttons = row.querySelectorAll<HTMLButtonElement>('#setting-clip-format .settings-segmented-btn');
    buttons.forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.value === values.dvrClipFormat);
    });
  });

  return row;
}
