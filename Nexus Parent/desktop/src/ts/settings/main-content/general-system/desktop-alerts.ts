/**
 * @file desktop-alerts.ts
 * @description Desktop Alert Notification checkboxes for General & System.
 *
 * Hey friend! Configures which urgent child-device events raise native desktop notifications.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the desktop alert notification checklist inside General & System.
 */
export function renderDesktopAlertsGroup(): HTMLElement {
  const list = document.createElement('div');
  list.className = 'settings-checkbox-list';

  const values = settingsState.getValues();

  list.innerHTML = `
    <label class="settings-checkbox-row">
      <input type="checkbox" id="setting-alert-intruder" ${values.alertIntruderSelfie ? 'checked' : ''} />
      <span class="settings-checkbox-custom"></span>
      <div class="settings-checkbox-label">
        <span class="settings-label-title">Intruder Selfie Triggered</span>
        <span class="settings-label-desc">Desktop notification when child phone detects unauthorized PIN attempts</span>
      </div>
    </label>

    <label class="settings-checkbox-row">
      <input type="checkbox" id="setting-alert-low-battery" ${values.alertLowBattery ? 'checked' : ''} />
      <span class="settings-checkbox-custom"></span>
      <div class="settings-checkbox-label">
        <span class="settings-label-title">Child Device Low Battery</span>
        <span class="settings-label-desc">Alert when child battery drops below 15%</span>
      </div>
    </label>

    <label class="settings-checkbox-row">
      <input type="checkbox" id="setting-alert-disconnect" ${values.alertChildDisconnected ? 'checked' : ''} />
      <span class="settings-checkbox-custom"></span>
      <div class="settings-checkbox-label">
        <span class="settings-label-title">Child Device Disconnection</span>
        <span class="settings-label-desc">Alert when active P2P link drops or child enters offline zone</span>
      </div>
    </label>

    <label class="settings-checkbox-row">
      <input type="checkbox" id="setting-alert-sos" ${values.alertSos ? 'checked' : ''} />
      <span class="settings-checkbox-custom"></span>
      <div class="settings-checkbox-label">
        <span class="settings-label-title">Emergency SOS Alert</span>
        <span class="settings-label-desc">High-priority audio chime and desktop alert when child triggers emergency broadcast</span>
      </div>
    </label>
  `;

  const alertIntruder = list.querySelector<HTMLInputElement>('#setting-alert-intruder');
  alertIntruder?.addEventListener('change', () => {
    settingsState.setValue('alertIntruderSelfie', alertIntruder.checked);
  });

  const alertBattery = list.querySelector<HTMLInputElement>('#setting-alert-low-battery');
  alertBattery?.addEventListener('change', () => {
    settingsState.setValue('alertLowBattery', alertBattery.checked);
  });

  const alertDisconnect = list.querySelector<HTMLInputElement>('#setting-alert-disconnect');
  alertDisconnect?.addEventListener('change', () => {
    settingsState.setValue('alertChildDisconnected', alertDisconnect.checked);
  });

  const alertSos = list.querySelector<HTMLInputElement>('#setting-alert-sos');
  alertSos?.addEventListener('change', () => {
    settingsState.setValue('alertSos', alertSos.checked);
  });

  return list;
}
