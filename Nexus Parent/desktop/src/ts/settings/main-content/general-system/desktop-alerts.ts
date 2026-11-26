/**
 * @file desktop-alerts.ts
 * @description Desktop Alert Notification settings & real-time event runtime for General & System.
 *
 * Hey friend! Configures and dispatches native OS desktop notifications + in-app alerts
 * for urgent child-device events:
 * 1. Intruder Selfie Triggered (`alertIntruderSelfie`): Fires on `APPLOCK_NEW` with wrong PIN (`pinCorrect === false`).
 * 2. Child Device Low Battery (`alertLowBattery`): Fires once when live telemetry drops below 15% while not charging
 *    (with 20% hysteresis reset to avoid spamming).
 * 3. Child Device Disconnection (`alertChildDisconnected`): Fires on unexpected WebRTC peer disconnection.
 * 4. Active Call Alert (`alertChildActiveCall`): Fires immediately and repeats every 30s while the child
 *    receives or makes a phone call (until Mic monitoring is turned on or the call ends), presenting a
 *    1-click "Turn On Mic" button inside the in-app alert banner + native OS desktop notification.
 */

import { settingsState } from '../../sidebar/state.js';
import { SettingsValues } from '../../sidebar/types.js';
import { deviceState, DeviceInfo } from '../../../shared/device-state.js';
import { sidebarState } from '../../../left-sidebar/state.js';
import { webrtcManager } from '../../../webrtc/webrtc-manager.js';
import { TYPE_APPLOCK_NEW, AppLockRecord } from '../../../right-sidebar/types.js';
import {
  isChildMicListeningOrConnecting,
  startChildMicMonitoring,
} from '../../../main-content/mic/mic.js';
import { escapeHtml } from '../helpers.js';
import { showToast } from '../../../shared/toast.js';
import { showDesktopNotification } from '../../../shared/ipc.js';

export const LOW_BATTERY_THRESHOLD_PERCENT = 15;
export const LOW_BATTERY_RECOVERY_PERCENT = 20;

export const TYPE_CALL_STATE = 'CALL_STATE';
export const CMD_CALL_ALERT_ON = 'CALL_ALERT_ON';
export const CMD_CALL_ALERT_OFF = 'CALL_ALERT_OFF';

export interface CallStateAlertPayload {
  type?: string;
  state: 'ringing' | 'active' | 'idle';
  direction?: 'incoming' | 'outgoing' | 'unknown';
  number?: string;
  contactName?: string;
  timestamp?: number;
  isRepeat?: boolean;
}

interface DesktopAlertDefinition {
  id: string;
  key:
    | 'alertIntruderSelfie'
    | 'alertLowBattery'
    | 'alertChildDisconnected'
    | 'alertChildActiveCall';
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
  {
    id: 'setting-alert-active-call',
    key: 'alertChildActiveCall',
    title: 'Active Call Alert',
    description: 'Notify with a quick Mic listen button while child receives or makes a phone call',
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
 * Hides and removes the floating Active Call Alert Banner if currently displayed.
 */
export function hideActiveCallAlertBanner(): void {
  if (typeof document === 'undefined' || typeof document.getElementById !== 'function') {
    return;
  }
  const existing = document.getElementById('nexus-active-call-alert');
  if (existing) {
    existing.classList?.remove('visible');
    if (existing.parentElement && typeof existing.parentElement.removeChild === 'function') {
      existing.parentElement.removeChild(existing);
    } else if (typeof existing.remove === 'function') {
      existing.remove();
    }
  }
}

/**
 * Displays or updates the actionable Active Call Alert Banner containing a 1-click "Turn On Mic" button.
 */
export function showActiveCallAlertBanner(title: string, body: string): void {
  if (
    typeof document === 'undefined' ||
    !document.body ||
    typeof document.createElement !== 'function'
  ) {
    return;
  }

  let banner =
    typeof document.getElementById === 'function'
      ? document.getElementById('nexus-active-call-alert')
      : null;

  if (!banner) {
    banner = document.createElement('div');
    banner.id = 'nexus-active-call-alert';
    banner.className = 'nexus-call-alert-banner';
    banner.setAttribute?.('role', 'alert');
    banner.innerHTML = `
      <div class="nexus-call-alert-content">
        <div class="nexus-call-alert-icon-wrap">
          <img src="assets/icons/left-sidebar/phone-call.svg" alt="" class="nexus-call-alert-icon" />
        </div>
        <div class="nexus-call-alert-text">
          <div id="nexus-call-alert-title" class="nexus-call-alert-title"></div>
          <div id="nexus-call-alert-body" class="nexus-call-alert-body"></div>
        </div>
      </div>
      <div class="nexus-call-alert-actions">
        <button type="button" id="btn-call-alert-mic-on" class="nexus-call-alert-mic-btn" title="Open Microphone and listen live">
          <img src="assets/icons/left-sidebar/mic.svg" alt="" class="nexus-call-alert-mic-icon" />
          <span>Turn On Mic</span>
        </button>
        <button type="button" id="btn-call-alert-dismiss" class="nexus-call-alert-dismiss-btn" title="Dismiss alert">
          <img src="assets/icons/main-content/x.svg" alt="" class="nexus-call-alert-dismiss-icon" />
        </button>
      </div>
    `;

    const micBtn = banner.querySelector<HTMLButtonElement>('#btn-call-alert-mic-on');
    micBtn?.addEventListener('click', () => {
      const started = startChildMicMonitoring();
      if (started) {
        hideActiveCallAlertBanner();
        showToast('Turning on child microphone to listen live...');
      } else {
        showToast('Unable to turn on Mic: child device is not connected');
      }
    });

    const dismissBtn = banner.querySelector<HTMLButtonElement>('#btn-call-alert-dismiss');
    dismissBtn?.addEventListener('click', () => {
      hideActiveCallAlertBanner();
    });

    document.body.appendChild(banner);
  }

  const titleEl = banner.querySelector<HTMLElement>('#nexus-call-alert-title');
  const bodyEl = banner.querySelector<HTMLElement>('#nexus-call-alert-body');
  if (titleEl) {
    titleEl.textContent = title;
  }
  if (bodyEl) {
    bodyEl.textContent = body;
  }

  banner.classList?.add('visible');
}

/**
 * Formats the title and body text for an incoming or outgoing active call alert.
 */
export function formatCallAlertCopy(payload: CallStateAlertPayload): {
  title: string;
  body: string;
} {
  const deviceName = deviceState.getConnectedDevice()?.name || 'Child device';
  const contact = (payload.contactName || '').trim();
  const number = (payload.number || '').trim();

  let partyLabel = '';
  if (contact && number) {
    partyLabel = `${contact} (${number})`;
  } else if (contact) {
    partyLabel = contact;
  } else if (number) {
    partyLabel = number;
  }

  const direction = payload.direction || 'unknown';
  let title: string;
  if (payload.state === 'ringing') {
    title = partyLabel ? `Incoming Call: ${partyLabel}` : `Incoming Call on ${deviceName}`;
  } else if (direction === 'outgoing') {
    title = partyLabel ? `Outgoing Call Active: ${partyLabel}` : `Outgoing Call on ${deviceName}`;
  } else if (direction === 'incoming') {
    title = partyLabel ? `Incoming Call Active: ${partyLabel}` : `Incoming Call Active on ${deviceName}`;
  } else {
    title = partyLabel ? `Active Call: ${partyLabel}` : `${deviceName} On a Call`;
  }

  const actionNote =
    'Turn on Mic to listen live (child audio direct; other party audible if speaker is on).';
  const prefix =
    payload.state === 'ringing'
      ? `${deviceName} is receiving a call.`
      : `${deviceName} is currently on a call.`;

  return {
    title: escapeHtml(title) === title ? title : title,
    body: `${prefix} ${actionNote}`,
  };
}

/**
 * Resets internal deduplication latches and hides transient banners (for tests or teardown).
 */
export function resetDesktopAlertsRuntimeForTesting(): void {
  lowBatteryAlertedDeviceKey = null;
  hideActiveCallAlertBanner();
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
  hideActiveCallAlertBanner();
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
 * Evaluates an incoming `CALL_STATE` packet from the child device.
 * Shows the actionable 1-click "Turn On Mic" banner + OS notification while the child is on a call,
 * suppresses repeat alerts once Mic listening starts, and clears the banner when the call ends.
 */
export function evaluateCallStateAlert(payload: CallStateAlertPayload | null | undefined): boolean {
  if (!payload || !payload.state) {
    return false;
  }

  if (payload.state === 'idle') {
    hideActiveCallAlertBanner();
    return false;
  }

  if (!settingsState.getValue('alertChildActiveCall')) {
    hideActiveCallAlertBanner();
    return false;
  }

  // Parent already turned Mic on (connecting or streaming): suppress repeating call alerts
  if (isChildMicListeningOrConnecting()) {
    hideActiveCallAlertBanner();
    return false;
  }

  const { title, body } = formatCallAlertCopy(payload);
  showActiveCallAlertBanner(title, body);
  void showDesktopNotification(title, body);
  return true;
}

/**
 * Syncs the `alertChildActiveCall` preference to the connected child device over DataChannel.
 */
export function syncCallAlertSettingToChild(enabled: boolean): boolean {
  const dc = webrtcManager.getDataChannel();
  if (!dc || dc.readyState !== 'open') {
    return false;
  }
  return webrtcManager.sendCommand(enabled ? CMD_CALL_ALERT_ON : CMD_CALL_ALERT_OFF);
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
 * Initializes live listeners for Intruder Selfie, Low Battery (<15%), Unexpected Disconnect,
 * and Child Active Call alerts.
 */
export function initDesktopAlertsRuntime(): () => void {
  lowBatteryAlertedDeviceKey = null;
  let lastCallAlertEnabled = settingsState.getValue('alertChildActiveCall');

  const unsubscribeMessages = webrtcManager.onMessage((rawMessage: string) => {
    if (typeof rawMessage !== 'string') return;
    const trimmed = rawMessage.trim();

    if (trimmed.startsWith(`{"type":"${TYPE_APPLOCK_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed) as { type?: string; record?: AppLockRecord };
        if (payload.type === TYPE_APPLOCK_NEW && payload.record) {
          evaluateAppLockAlertRecord(payload.record);
        }
      } catch (err) {
        console.warn('[DesktopAlerts] Failed to parse APPLOCK_NEW payload:', err);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_CALL_STATE}"`)) {
      try {
        const payload = JSON.parse(trimmed) as CallStateAlertPayload;
        if (payload.type === TYPE_CALL_STATE) {
          evaluateCallStateAlert(payload);
        }
      } catch (err) {
        console.warn('[DesktopAlerts] Failed to parse CALL_STATE payload:', err);
      }
    }
  });

  const unsubscribeDataChannelOpen = webrtcManager.onDataChannelOpen(() => {
    syncCallAlertSettingToChild(settingsState.getValue('alertChildActiveCall'));
  });

  const unsubscribeSettings = settingsState.onValuesChange((values) => {
    if (values.alertChildActiveCall !== lastCallAlertEnabled) {
      lastCallAlertEnabled = values.alertChildActiveCall;
      syncCallAlertSettingToChild(values.alertChildActiveCall);
      if (!values.alertChildActiveCall) {
        hideActiveCallAlertBanner();
      }
    }
  });

  const unsubscribeSidebar = sidebarState.subscribe(() => {
    if (isChildMicListeningOrConnecting()) {
      hideActiveCallAlertBanner();
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
      hideActiveCallAlertBanner();
    }
  });

  return () => {
    unsubscribeMessages();
    unsubscribeDataChannelOpen();
    unsubscribeSettings();
    unsubscribeSidebar();
    unsubscribeTelemetry();
    unsubscribeUnexpectedDisconnect();
    unsubscribeDevice();
    lowBatteryAlertedDeviceKey = null;
    hideActiveCallAlertBanner();
  };
}
