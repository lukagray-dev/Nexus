/**
 * @file clip-format.ts
 * @description DVR Clip Export Container format setting.
 *
 * Hey friend! Chooses between MP4 and WebM containers when exporting recorded DVR clips.
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';

/**
 * Renders the DVR Clip Export Container segmented selector row.
 */
export function renderClipFormatSetting(): HTMLElement {
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
  });

  return row;
}
