/**
 * @file screen-quality.ts
 * @description Screen Mirror Quality preset setting.
 *
 * Hey friend! Configures the target resolution and framerate used for screen
 * mirroring streams from the child device.
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';

/**
 * Renders the Screen Mirror Quality segmented selector row.
 */
export function renderScreenQualitySetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentQuality = settingsState.getValue('screenQuality');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Screen Mirror Quality</span>
      <span class="settings-label-desc">Higher quality increases detail; lower saves child phone battery</span>
    </div>
    <div class="settings-segmented-group" id="setting-screen-quality">
      <button type="button" class="settings-segmented-btn ${currentQuality === '1080p' ? 'is-active' : ''}" data-value="1080p">1080p @ 30fps</button>
      <button type="button" class="settings-segmented-btn ${currentQuality === '720p' ? 'is-active' : ''}" data-value="720p">720p @ 30fps</button>
      <button type="button" class="settings-segmented-btn ${currentQuality === '540p' ? 'is-active' : ''}" data-value="540p">540p @ 24fps</button>
    </div>
  `;

  bindSegmentedGroup<'1080p' | '720p' | '540p'>(row, '#setting-screen-quality', (val) => {
    settingsState.setValue('screenQuality', val);
  });

  return row;
}
