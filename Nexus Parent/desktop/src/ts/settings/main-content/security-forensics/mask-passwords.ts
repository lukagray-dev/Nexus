/**
 * @file mask-passwords.ts
 * @description Mask Password Inputs in Keystroke Log toggle setting.
 *
 * Hey friend! Replaces detected password fields with bullet masks in keystroke logs.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Mask Password Inputs toggle setting row.
 */
export function renderMaskPasswordsSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('maskPasswords');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Mask Password Inputs in Keystroke Log</span>
      <span class="settings-label-desc">Replaces detected password input fields with bullet masks (••••••••) to protect family account privacy</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-mask-passwords" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-mask-passwords');
  toggle?.addEventListener('change', () => {
    settingsState.setValue('maskPasswords', toggle.checked);
  });

  return row;
}
