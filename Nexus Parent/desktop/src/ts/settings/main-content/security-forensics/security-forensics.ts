/**
 * @file security-forensics.ts
 * @description Security & App Lock Settings category compositor.
 *
 * Hey friend! Assembles App Lock PIN guard and forensic auditing settings.
 */

import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderRemotePinSetting } from './remote-pin.js';
import { renderIntruderSelfieSetting } from './intruder-selfie.js';
import { renderLockoutStrikesSetting } from './lockout-strikes.js';
import { renderMaskPasswordsSetting } from './mask-passwords.js';
import { renderStealthConfirmationSetting } from './stealth-confirmation.js';
import { renderDesktopAlertSettings } from './desktop-alerts.js';

/**
 * Renders the Security & App Lock Settings main content section.
 */
export function renderSecurityForensicsContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Security & App Lock',
      'Configure remote PIN enforcement, intruder photo captures, keystroke audit masking, stealth protections, and desktop alerts.'
    )
  );

  container.appendChild(
    createSettingsGroup('APP LOCK & REMOTE PIN GUARD', [
      renderRemotePinSetting(),
      renderIntruderSelfieSetting(),
      renderLockoutStrikesSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('FORENSIC AUDITING & STEALTH SHIELDS', [
      renderMaskPasswordsSetting(),
      renderStealthConfirmationSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('DESKTOP ALERT NOTIFICATIONS', renderDesktopAlertSettings())
  );

  return container;
}
