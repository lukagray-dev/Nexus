/**
 * @file stealth-confirmation.ts
 * @description Stealth Mode Confirmation Guard toggle setting.
 *
 * Hey friend! Requires explicit confirmation before hiding the child launcher icon.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Stealth Mode Confirmation Guard toggle row.
 */
export function renderStealthConfirmationSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('stealthConfirmation');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Stealth Mode Confirmation Guard</span>
      <span class="settings-label-desc">Requires explicit confirmation before toggling launcher icon visibility to prevent accidental loss of physical access</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-stealth-confirm" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-stealth-confirm');
  toggle?.addEventListener('change', () => {
    settingsState.setValue('stealthConfirmation', toggle.checked);
  });

  return row;
}
