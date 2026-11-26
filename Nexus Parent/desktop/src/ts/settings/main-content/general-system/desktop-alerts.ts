/**
 * @file desktop-alerts.ts
 * @description Desktop Alert Notification settings & real-time event runtime for General & System.
 *
 * Hey friend! Configures and dispatches native OS desktop notifications + in-app toasts
 * for urgent child-device events:
 * 1. Intruder Selfie Triggered (`alertIntruderSelfie`): Fires on `APPLOCK_NEW` with wrong PIN (`pinCorrect === false`).
 * 2. Child Device Low Battery (`alertLowBattery`): Fires once when live telemetry drops below 15% while not charging
 *    (with 20% hysteresis reset to avoid spamming).
 * 3. Child Device Disconnection (`alertChildDisconnected`): Fires on unexpected WebRTC peer disconnection.
 */

import { settingsState } from '../../sidebar/state.js';
import { SettingsValues } from '../../sidebar/types.js';
import { deviceState, DeviceInfo } from '../../../shared/device-state.js';
import { webrtcManager } from '../../../webrtc/webrtc-manager.js';
import { TYPE_APPLOCK_NEW, AppLockRecord } from '../../../right-sidebar/types.js';
import { showToast } from '../../../shared/toast.js';
import { showDesktopNotification } from '../../../shared/ipc.js';

export const LOW_BATTERY_THRESHOLD_PERCENT = 15;
export const LOW_BATTERY_RECOVERY_PERCENT = 20;

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
    description: 'Alert when active P2P link drops unexpectedly or child enters offline zone',
  },
];

let unsubscribeAlertsSync: (() => void) | null = null;
let lowBatteryAlertedDeviceKey: string | null = null;

/**
 * Dispatches both an in-app tactical toast and a native OS desktop notification.
 */
function dispatchDesktopAlert(title: string, body: string): void {
  showToast(`${title}: ${body}`, 4000);
  void showDesktopNotification(title, body);
}

/**
 * Resets internal low-battery deduplication latch (for tests or full session teardown).
 */
export function resetDesktopAlertsRuntimeForTesting(): void {
  lowBatteryAlertedDeviceKey = null;
}

/**
 * Evaluates an incoming `APPLOCK_NEW` record and triggers an intruder alert when enabled.
 */
export function evaluateAppLockAlertRecord(record: AppLockRecord | null | undefined): boolean {
  if (!record || record.pinCorrect !== false) {
    return false;
  }
  if (!settingsState.getValue('alertIntruderSelfie')) {
    return false;
  }

  const deviceName = deviceState.getConnectedDevice()?.name || 'Child device';
  const title = record.selfie ? 'Intruder Selfie Captured' : 'Unauthorized PIN Attempt';
  const attemptsText =
    typeof record.failedAttempts === 'number' && record.failedAttempts > 0
      ? `attempt #${record.failedAttempts}`
      : 'failed attempt';
  const body = record.isLockedOut
    ? `${deviceName}: Wrong PIN (${attemptsText}) — 60s lockout active`
    : `${deviceName}: Wrong PIN (${attemptsText})`;

  dispatchDesktopAlert(title, body);
  return true;
}

/**
 * Evaluates live child telemetry for `< 15%` low battery with hysteresis deduplication.
 */
export function evaluateLowBatteryAlert(device: DeviceInfo | null | undefined): boolean {
  if (!device) {
    lowBatteryAlertedDeviceKey = null;
    return false;
  }

  const percent = device.batteryPercent;
  const isCharging = device.isCharging === true;
  if (typeof percent !== 'number' || Number.isNaN(percent) || percent < 0) {
    return false;
  }

  if (isCharging || percent >= LOW_BATTERY_RECOVERY_PERCENT) {
    lowBatteryAlertedDeviceKey = null;
    return false;
  }

  if (percent >= LOW_BATTERY_THRESHOLD_PERCENT) {
    return false;
  }

  const deviceKey = device.rawId || device.id;
  if (lowBatteryAlertedDeviceKey === deviceKey) {
    return false;
  }

  if (!settingsState.getValue('alertLowBattery')) {
    return false;
  }

  lowBatteryAlertedDeviceKey = deviceKey;
  dispatchDesktopAlert(
    'Child Device Low Battery',
    `${device.name} battery is at ${Math.round(percent)}% — connect charger soon`
  );
  return true;
}

/**
 * Evaluates an unexpected child disconnection and fires a desktop alert when enabled.
 */
export function evaluateUnexpectedDisconnectAlert(device: DeviceInfo | null | undefined): boolean {
  lowBatteryAlertedDeviceKey = null;
  if (!device || !settingsState.getValue('alertChildDisconnected')) {
    return false;
  }

  const label = device.name ? `${device.name} (${device.id})` : device.id;
  dispatchDesktopAlert(
    'Child Device Disconnected',
    `Lost P2P connection to ${label}`
  );
  return true;
}

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
    showToast(
      toggle.checked
        ? `${def.title} alert enabled`
        : `${def.title} alert disabled`
    );
  });

  return row;
}

/**
 * Renders the desktop alert notification toggle rows inside General & System.
 */
export function renderDesktopAlertSettings(): HTMLElement[] {
  if (unsubscribeAlertsSync) {
    unsubscribeAlertsSync();
    unsubscribeAlertsSync = null;
  }

  const values = settingsState.getValues();
  const rows = DESKTOP_ALERT_DEFINITIONS.map((def) => createAlertToggleRow(def, values));

  unsubscribeAlertsSync = settingsState.onValuesChange((updated) => {
    if (rows[0]?.isConnected === false) {
      unsubscribeAlertsSync?.();
      unsubscribeAlertsSync = null;
      return;
    }
    DESKTOP_ALERT_DEFINITIONS.forEach((def, idx) => {
      const toggle = rows[idx]?.querySelector<HTMLInputElement>(`#${def.id}`);
      if (toggle && toggle.checked !== Boolean(updated[def.key])) {
        toggle.checked = Boolean(updated[def.key]);
      }
    });
  });

  return rows;
}

/**
 * Initializes live listeners for Intruder Selfie, Low Battery (<15%), and Unexpected Disconnect alerts.
 */
export function initDesktopAlertsRuntime(): () => void {
  lowBatteryAlertedDeviceKey = null;

  const unsubscribeMessages = webrtcManager.onMessage((rawMessage: string) => {
    if (typeof rawMessage !== 'string') return;
    const trimmed = rawMessage.trim();
    if (!trimmed.startsWith(`{"type":"${TYPE_APPLOCK_NEW}"`)) {
      return;
    }

    try {
      const payload = JSON.parse(trimmed) as { type?: string; record?: AppLockRecord };
      if (payload.type === TYPE_APPLOCK_NEW && payload.record) {
        evaluateAppLockAlertRecord(payload.record);
      }
    } catch (err) {
      console.warn('[DesktopAlerts] Failed to parse APPLOCK_NEW payload:', err);
    }
  });

  const unsubscribeTelemetry = deviceState.onTelemetryUpdate((device) => {
    evaluateLowBatteryAlert(device);
  });

  const unsubscribeUnexpectedDisconnect = deviceState.onUnexpectedDisconnect((device) => {
    evaluateUnexpectedDisconnectAlert(device);
  });

  const unsubscribeDevice = deviceState.subscribe(() => {
    if (!deviceState.isConnected() && !deviceState.isReconnecting()) {
      lowBatteryAlertedDeviceKey = null;
    }
  });

  return () => {
    unsubscribeMessages();
    unsubscribeTelemetry();
    unsubscribeUnexpectedDisconnect();
    unsubscribeDevice();
    lowBatteryAlertedDeviceKey = null;
  };
}
