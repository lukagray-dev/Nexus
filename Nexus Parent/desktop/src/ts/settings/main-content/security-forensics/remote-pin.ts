/**
 * @file remote-pin.ts
 * @description Default 6-Digit Remote PIN setting for Security & App Lock.
 *
 * Hey friend! Sanitizes numeric input to 6 digits and provides show/hide toggling.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Default 6-Digit Remote PIN input setting row.
 */
export function renderRemotePinSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentPin = settingsState.getValue('remotePin');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Default 6-Digit Remote PIN</span>
      <span class="settings-label-desc">Security PIN required to unlock restricted applications on the child phone</span>
    </div>
    <div class="settings-pin-input-group">
      <input type="password" id="setting-remote-pin" class="settings-input settings-input-pin" maxlength="6" value="${currentPin}" placeholder="123456" />
      <button type="button" id="btn-toggle-pin-visibility" class="settings-btn-inline" title="Show/Hide PIN">Show</button>
    </div>
  `;

  const pinInput = row.querySelector<HTMLInputElement>('#setting-remote-pin');
  const toggleBtn = row.querySelector<HTMLButtonElement>('#btn-toggle-pin-visibility');

  toggleBtn?.addEventListener('click', () => {
    if (!pinInput) return;
    if (pinInput.type === 'password') {
      pinInput.type = 'text';
      toggleBtn.textContent = 'Hide';
    } else {
      pinInput.type = 'password';
      toggleBtn.textContent = 'Show';
    }
  });

  pinInput?.addEventListener('input', () => {
    const sanitized = pinInput.value.replace(/\D/g, '').slice(0, 6);
    pinInput.value = sanitized;
    settingsState.setValue('remotePin', sanitized);
  });

  return row;
}
