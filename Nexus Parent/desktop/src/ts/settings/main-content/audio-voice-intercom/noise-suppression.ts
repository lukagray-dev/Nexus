/**
 * @file noise-suppression.ts
 * @description Hardware Noise Suppression & AEC toggle setting.
 *
 * Hey friend! Controls WebRTC acoustic echo cancellation, background noise
 * suppression, and automatic gain control on the parent microphone stream.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';

let unsubscribeNoiseSync: (() => void) | null = null;

/**
 * Renders the Hardware Noise Suppression & AEC toggle setting row.
 */
export function renderNoiseSuppressionSetting(): HTMLElement {
  if (unsubscribeNoiseSync) {
    unsubscribeNoiseSync();
    unsubscribeNoiseSync = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('noiseSuppression');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Hardware Noise Suppression & AEC</span>
      <span class="settings-label-desc">Acoustic echo cancellation, noise reduction, and auto-gain control</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-noise-suppression" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const noiseToggle = row.querySelector<HTMLInputElement>('#setting-noise-suppression');
  noiseToggle?.addEventListener('change', () => {
    settingsState.setValue('noiseSuppression', noiseToggle.checked);
    showToast(
      noiseToggle.checked
        ? 'Hardware noise suppression and AEC enabled'
        : 'Hardware noise suppression and AEC disabled'
    );
  });

  unsubscribeNoiseSync = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      unsubscribeNoiseSync?.();
      unsubscribeNoiseSync = null;
      return;
    }
    if (noiseToggle && noiseToggle.checked !== values.noiseSuppression) {
      noiseToggle.checked = values.noiseSuppression;
    }
  });

  return row;
}
