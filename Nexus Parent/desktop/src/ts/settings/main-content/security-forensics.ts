/**
 * @file security-forensics.ts
 * @description Security & App Lock Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Default remote 6-digit numeric PIN for the child phone AppLock guard screen.
 * 2. Silent offscreen camera intruder selfie capture on failed unlock attempts.
 * 3. Lockout strike threshold (3 strikes / 5 strikes / disabled).
 * 4. Automatic masking of sensitive password keystrokes in the Keystroke Auditor.
 * 5. Double-confirmation protection when toggling Stealth Mode on the child launcher.
 */

import { settingsState } from '../sidebar/state.js';

export function renderSecurityForensicsContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Security & App Lock</h2>
      <p class="settings-content-desc">Configure remote PIN enforcement, intruder photo captures, keystroke audit masking, and stealth protections.</p>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">APP LOCK & REMOTE PIN GUARD</div>

      <!-- 1. Default Remote PIN -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Default 6-Digit Remote PIN</span>
          <span class="settings-label-desc">Security PIN required to unlock restricted applications on the child phone</span>
        </div>
        <div class="settings-pin-input-group">
          <input type="password" id="setting-remote-pin" class="settings-input settings-input-pin" maxlength="6" value="${values.remotePin}" placeholder="123456" />
          <button type="button" id="btn-toggle-pin-visibility" class="settings-btn-inline" title="Show/Hide PIN">Show</button>
        </div>
      </div>

      <!-- 2. Intruder Selfie Capture -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Silent Intruder Selfie Capture</span>
          <span class="settings-label-desc">Silently fires front Camera2 sensor when unauthorized PIN entry attempts occur</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-intruder-selfie" ${values.intruderSelfie ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>

      <!-- 3. Lockout Strike Threshold -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Failed Attempt Lockout Threshold</span>
          <span class="settings-label-desc">Number of incorrect strikes before child device engages a 60-second hardware lockout</span>
        </div>
        <select id="setting-lockout-strikes" class="settings-select">
          <option value="3" ${values.lockoutStrikes === '3' ? 'selected' : ''}>3 Strikes (60s Lockout, Recommended)</option>
          <option value="5" ${values.lockoutStrikes === '5' ? 'selected' : ''}>5 Strikes (60s Lockout)</option>
          <option value="disabled" ${values.lockoutStrikes === 'disabled' ? 'selected' : ''}>Disabled (No Lockout Timer)</option>
        </select>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">FORENSIC AUDITING & STEALTH SHIELDS</div>

      <!-- 4. Sensitive Password Masking -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Mask Password Inputs in Keystroke Log</span>
          <span class="settings-label-desc">Replaces detected password input fields with bullet masks (••••••••) to protect family account privacy</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-mask-passwords" ${values.maskPasswords ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>

      <!-- 5. Stealth Mode Confirmation -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Stealth Mode Confirmation Guard</span>
          <span class="settings-label-desc">Requires explicit confirmation before toggling launcher icon visibility to prevent accidental loss of physical access</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-stealth-confirm" ${values.stealthConfirmation ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const pinInput = container.querySelector<HTMLInputElement>('#setting-remote-pin');
  const togglePinBtn = container.querySelector<HTMLButtonElement>('#btn-toggle-pin-visibility');

  togglePinBtn?.addEventListener('click', () => {
    if (!pinInput) return;
    if (pinInput.type === 'password') {
      pinInput.type = 'text';
      togglePinBtn.textContent = 'Hide';
    } else {
      pinInput.type = 'password';
      togglePinBtn.textContent = 'Show';
    }
  });

  pinInput?.addEventListener('input', () => {
    const sanitized = pinInput.value.replace(/\D/g, '').slice(0, 6);
    pinInput.value = sanitized;
    settingsState.setValue('remotePin', sanitized);
  });

  const selfieToggle = container.querySelector<HTMLInputElement>('#setting-intruder-selfie');
  selfieToggle?.addEventListener('change', () => {
    settingsState.setValue('intruderSelfie', selfieToggle.checked);
  });

  const strikesSelect = container.querySelector<HTMLSelectElement>('#setting-lockout-strikes');
  strikesSelect?.addEventListener('change', () => {
    settingsState.setValue('lockoutStrikes', strikesSelect.value as '3' | '5' | 'disabled');
  });

  const maskToggle = container.querySelector<HTMLInputElement>('#setting-mask-passwords');
  maskToggle?.addEventListener('change', () => {
    settingsState.setValue('maskPasswords', maskToggle.checked);
  });

  const stealthToggle = container.querySelector<HTMLInputElement>('#setting-stealth-confirm');
  stealthToggle?.addEventListener('change', () => {
    settingsState.setValue('stealthConfirmation', stealthToggle.checked);
  });

  return container;
}
