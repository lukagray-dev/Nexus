/**
 * @file camera-lens.ts
 * @description Preferred Default Camera Lens setting.
 *
 * Hey friend! Chooses whether rear or front camera opens first when starting
 * live camera surveillance.
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';

/**
 * Renders the Preferred Default Camera Lens segmented selector row.
 */
export function renderCameraLensSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentLens = settingsState.getValue('preferredCameraLens');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Preferred Default Camera Lens</span>
      <span class="settings-label-desc">Initial optical sensor engaged when starting camera surveillance</span>
    </div>
    <div class="settings-segmented-group" id="setting-camera-lens">
      <button type="button" class="settings-segmented-btn ${currentLens === 'rear' ? 'is-active' : ''}" data-value="rear">Rear Lens</button>
      <button type="button" class="settings-segmented-btn ${currentLens === 'front' ? 'is-active' : ''}" data-value="front">Front Lens</button>
    </div>
  `;

  bindSegmentedGroup<'rear' | 'front'>(row, '#setting-camera-lens', (val) => {
    settingsState.setValue('preferredCameraLens', val);
  });

  return row;
}
