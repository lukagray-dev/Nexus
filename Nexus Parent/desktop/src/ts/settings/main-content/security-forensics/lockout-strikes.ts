/**
 * @file lockout-strikes.ts
 * @description Failed Attempt Lockout Threshold dropdown setting.
 *
 * Hey friend! Configures how many failed PIN attempts engage the 60-second lockout.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Failed Attempt Lockout Threshold dropdown row.
 */
export function renderLockoutStrikesSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const strikes = settingsState.getValue('lockoutStrikes');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Failed Attempt Lockout Threshold</span>
      <span class="settings-label-desc">Number of incorrect strikes before child device engages a 60-second hardware lockout</span>
    </div>
    <select id="setting-lockout-strikes" class="settings-select">
      <option value="3" ${strikes === '3' ? 'selected' : ''}>3 Strikes (60s Lockout, Recommended)</option>
      <option value="5" ${strikes === '5' ? 'selected' : ''}>5 Strikes (60s Lockout)</option>
      <option value="disabled" ${strikes === 'disabled' ? 'selected' : ''}>Disabled (No Lockout Timer)</option>
    </select>
  `;

  const select = row.querySelector<HTMLSelectElement>('#setting-lockout-strikes');
  select?.addEventListener('change', () => {
    settingsState.setValue('lockoutStrikes', select.value as '3' | '5' | 'disabled');
  });

  return row;
}
