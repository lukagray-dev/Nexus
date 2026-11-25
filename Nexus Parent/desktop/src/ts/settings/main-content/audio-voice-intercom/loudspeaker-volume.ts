/**
 * @file loudspeaker-volume.ts
 * @description Intercom Loudspeaker Target Volume setting.
 *
 * Hey friend! Controls the target volume override applied on the child phone's
 * loudspeaker when the parent broadcasts via push-to-talk intercom.
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';

/**
 * Renders the Loudspeaker Target Volume segmented selector setting row.
 */
export function renderLoudspeakerVolumeSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentVolume = settingsState.getValue('loudspeakerVolume');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Loudspeaker Target Volume</span>
      <span class="settings-label-desc">Hardware volume forced on the child device when parent speaks</span>
    </div>
    <div class="settings-segmented-group" id="setting-speaker-volume">
      <button type="button" class="settings-segmented-btn ${currentVolume === 'max' ? 'is-active' : ''}" data-value="max">Max (100%)</button>
      <button type="button" class="settings-segmented-btn ${currentVolume === 'high' ? 'is-active' : ''}" data-value="high">High (85%)</button>
      <button type="button" class="settings-segmented-btn ${currentVolume === 'normal' ? 'is-active' : ''}" data-value="normal">Preserve Level</button>
    </div>
  `;

  bindSegmentedGroup<'max' | 'high' | 'normal'>(row, '#setting-speaker-volume', (val) => {
    settingsState.setValue('loudspeakerVolume', val);
  });

  return row;
}
