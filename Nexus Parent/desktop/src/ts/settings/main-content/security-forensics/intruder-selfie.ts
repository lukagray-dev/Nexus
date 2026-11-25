/**
 * @file intruder-selfie.ts
 * @description Silent Intruder Selfie Capture toggle setting.
 *
 * Hey friend! Controls whether failed App Lock PIN entries trigger silent front-camera capture.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Silent Intruder Selfie Capture toggle row.
 */
export function renderIntruderSelfieSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('intruderSelfie');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Silent Intruder Selfie Capture</span>
      <span class="settings-label-desc">Silently fires front Camera2 sensor when unauthorized PIN entry attempts occur</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-intruder-selfie" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-intruder-selfie');
  toggle?.addEventListener('change', () => {
    settingsState.setValue('intruderSelfie', toggle.checked);
  });

  return row;
}
