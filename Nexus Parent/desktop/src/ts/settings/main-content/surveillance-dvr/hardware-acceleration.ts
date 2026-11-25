/**
 * @file hardware-acceleration.ts
 * @description GPU Video Hardware Acceleration toggle setting.
 *
 * Hey friend! Controls whether WebView2 uses GPU hardware video decoding for
 * incoming SRTP video streams.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the GPU Video Hardware Acceleration toggle setting row.
 */
export function renderHardwareAccelerationSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('hardwareAcceleration');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">GPU Video Hardware Acceleration</span>
      <span class="settings-label-desc">Uses native GPU decoding inside WebView2 for smooth zero-lag playback</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-hardware-acceleration" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const hwToggle = row.querySelector<HTMLInputElement>('#setting-hardware-acceleration');
  hwToggle?.addEventListener('change', () => {
    settingsState.setValue('hardwareAcceleration', hwToggle.checked);
  });

  return row;
}
