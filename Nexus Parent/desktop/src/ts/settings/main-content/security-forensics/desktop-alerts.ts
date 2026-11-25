/**
 * @file desktop-alerts.ts
 * @description Desktop Alert Notification toggle settings for Security & App Lock.
 *
 * Hey friend! Configures which urgent child-device security and connectivity events
 * raise native desktop notifications on the parent workstation:
 * - Intruder Selfie Triggered (`alertIntruderSelfie`)
 * - Child Device Low Battery (`alertLowBattery`)
 * - Child Device Disconnection (`alertChildDisconnected`)
 */

import { settingsState } from '../../sidebar/state.js';
import { SettingsValues } from '../../sidebar/types.js';

interface DesktopAlertDefinition {
  id: string;
  key: 'alertIntruderSelfie' | 'alertLowBattery' | 'alertChildDisconnected';
  title: string;
  description: string;
}

const DESKTOP_ALERT_DEFINITIONS: DesktopAlertDefinition[] = [
  {
    id: 'setting-alert-intruder',
    key: 'alertIntruderSelfie',
    title: 'Intruder Selfie Triggered',
    description: 'Desktop notification when child phone detects unauthorized PIN attempts',
  },
  {
    id: 'setting-alert-low-battery',
    key: 'alertLowBattery',
    title: 'Child Device Low Battery',
    description: 'Alert when child battery drops below 15%',
  },
  {
    id: 'setting-alert-disconnect',
    key: 'alertChildDisconnected',
    title: 'Child Device Disconnection',
    description: 'Alert when active P2P link drops or child enters offline zone',
  },
];

/**
 * Builds an individual toggle row for a desktop alert preference.
 */
function createAlertToggleRow(
  def: DesktopAlertDefinition,
  values: Readonly<SettingsValues>
): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isChecked = Boolean(values[def.key]);

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">${def.title}</span>
      <span class="settings-label-desc">${def.description}</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="${def.id}" ${isChecked ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>(`#${def.id}`);
  toggle?.addEventListener('change', () => {
    settingsState.setValue(def.key, toggle.checked);
  });

  return row;
}

/**
 * Renders the desktop alert notification toggle rows inside Security & App Lock.
 */
export function renderDesktopAlertSettings(): HTMLElement[] {
  const values = settingsState.getValues();
  return DESKTOP_ALERT_DEFINITIONS.map((def) => createAlertToggleRow(def, values));
}
