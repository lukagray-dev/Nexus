/**
 * @file output-device.ts
 * @description Monitoring Output Speaker selection setting.
 *
 * Hey friend! Selects which local speakers or headphones play incoming audio
 * from the child's environment.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Monitoring Output Speaker dropdown setting row.
 */
export function renderOutputDeviceSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentDevice = settingsState.getValue('outputDeviceId');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Monitoring Output Speaker</span>
      <span class="settings-label-desc">Speakers or headphones used to listen to the child's environment</span>
    </div>
    <select id="setting-output-device" class="settings-select">
      <option value="default" ${currentDevice === 'default' ? 'selected' : ''}>System Default Output</option>
      <option value="speakers" ${currentDevice === 'speakers' ? 'selected' : ''}>Speakers (Realtek Audio)</option>
      <option value="headphones" ${currentDevice === 'headphones' ? 'selected' : ''}>Headphones / Bluetooth Audio</option>
    </select>
  `;

  const outputSelect = row.querySelector<HTMLSelectElement>('#setting-output-device');
  outputSelect?.addEventListener('change', () => {
    settingsState.setValue('outputDeviceId', outputSelect.value);
  });

  return row;
}
