/**
 * @file loudspeaker-volume.ts
 * @description Intercom Loudspeaker Target Volume setting.
 *
 * Hey friend! Controls the target volume override applied on the child phone's
 * loudspeaker when the parent broadcasts via push-to-talk intercom:
 * - `max` (100% hardware loudspeaker override)
 * - `high` (85% hardware loudspeaker level)
 * - `normal` (preserves the child device's current media/call volume)
 */

import { settingsState } from '../../sidebar/state.js';
import { bindSegmentedGroup } from '../helpers.js';
import { showToast } from '../../../shared/toast.js';

let unsubscribeSpeakerSync: (() => void) | null = null;

const VOLUME_LABELS: Record<'max' | 'high' | 'normal', string> = {
  max: 'Max (100%)',
  high: 'High (85%)',
  normal: 'Preserve Level',
};

/**
 * Renders the Loudspeaker Target Volume segmented selector setting row.
 */
export function renderLoudspeakerVolumeSetting(): HTMLElement {
  if (unsubscribeSpeakerSync) {
    unsubscribeSpeakerSync();
    unsubscribeSpeakerSync = null;
  }

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
    showToast(`Child loudspeaker target set to ${VOLUME_LABELS[val]}`);
  });

  unsubscribeSpeakerSync = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      unsubscribeSpeakerSync?.();
      unsubscribeSpeakerSync = null;
      return;
    }
    const buttons = row.querySelectorAll<HTMLButtonElement>('#setting-speaker-volume .settings-segmented-btn');
    buttons.forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.value === values.loudspeakerVolume);
    });
  });

  return row;
}
