/**
 * @file input-device.ts
 * @description Microphone Input Device selection setting.
 *
 * Hey friend! Selects which local PC microphone captures parent voice audio.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Microphone Input Device dropdown setting row.
 */
export function renderInputDeviceSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentDevice = settingsState.getValue('inputDeviceId');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Microphone Input Device</span>
      <span class="settings-label-desc">PC microphone used for parent speech broadcast</span>
    </div>
    <select id="setting-input-device" class="settings-select">
      <option value="default" ${currentDevice === 'default' ? 'selected' : ''}>System Default Input</option>
      <option value="mic-internal" ${currentDevice === 'mic-internal' ? 'selected' : ''}>Realtek High Definition Audio (Built-in)</option>
      <option value="mic-usb" ${currentDevice === 'mic-usb' ? 'selected' : ''}>USB Studio Microphone (48kHz)</option>
    </select>
  `;

  const inputSelect = row.querySelector<HTMLSelectElement>('#setting-input-device');
  inputSelect?.addEventListener('change', () => {
    settingsState.setValue('inputDeviceId', inputSelect.value);
  });

  return row;
}
