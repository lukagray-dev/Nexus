/**
 * @file device-card.ts
 * @description Read-only Paired Child Device Telemetry Inspector for Settings.
 *
 * Hey friend! This module builds the detailed telemetry view for any selected child device:
 * 1. Connected: Full-contrast live hardware, OS, power, memory, connectivity, and posture tiles.
 * 2. Disconnected (cached history record): Grayed-out (`.is-offline`) view preserving
 *    the selected device's identity and cached telemetry snapshot from `localStorage`.
 * 3. Empty: Grayed-out empty state showing `"—"` placeholders.
 */

import {
  deviceState,
  type DeviceInfo,
  type LastConnectedDevice,
  type LastDeviceTelemetrySnapshot,
} from '../../../shared/device-state.js';
import { reconnectManager } from '../../../shared/reconnect-manager.js';
import { escapeHtml } from '../helpers.js';

let unsubscribeDeviceCard: (() => void) | null = null;

export interface ResolvedDeviceViewModel {
  rawId: string | null;
  statusMode: 'online' | 'reconnecting' | 'offline' | 'empty';
  isGrayedOut: boolean;
  badgeText: string;
  badgeClass: string;
  displayName: string;
  displayModel: string;
  displayId: string;
  lastSyncedLabel: string;
  osText: string;
  hardwareText: string;
  displayScreenText: string;
  appVersionText: string;
  batteryText: string;
  thermalPowerText: string;
  ramText: string;
  storageText: string;
  networkCarrierText: string;
  ringerUptimeText: string;
  securityGuardsText: string;
  survivalPermissionsText: string;
}

export function formatLastConnectedTimestamp(timestamp?: number): string {
  if (!timestamp || !Number.isFinite(timestamp)) {
    return 'Cached snapshot';
  }
  try {
    return `Last connected: ${new Date(timestamp).toLocaleString([], {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  } catch {
    return 'Cached snapshot';
  }
}

function formatTelemetrySnapshot(
  telemetry: LastDeviceTelemetrySnapshot | undefined,
  fallbackModel?: string
): Pick<
  ResolvedDeviceViewModel,
  | 'osText'
  | 'hardwareText'
  | 'displayScreenText'
  | 'appVersionText'
  | 'batteryText'
  | 'thermalPowerText'
  | 'ramText'
  | 'storageText'
  | 'networkCarrierText'
  | 'ringerUptimeText'
  | 'securityGuardsText'
  | 'survivalPermissionsText'
> {
  if (!telemetry) {
    return {
      osText: '—',
      hardwareText: fallbackModel || '—',
      displayScreenText: '—',
      appVersionText: '—',
      batteryText: '—',
      thermalPowerText: '—',
      ramText: '—',
      storageText: '—',
      networkCarrierText: '—',
      ringerUptimeText: '—',
      securityGuardsText: '—',
      survivalPermissionsText: '—',
    };
  }

  const osText = telemetry.androidVersion
    ? telemetry.sdkInt && telemetry.sdkInt > 0
      ? `Android ${telemetry.androidVersion} (API ${telemetry.sdkInt})`
      : `Android ${telemetry.androidVersion}`
    : '—';

  const hardwareText = fallbackModel
    ? telemetry.brand && !fallbackModel.toLowerCase().startsWith(telemetry.brand.toLowerCase())
      ? `${telemetry.brand} ${fallbackModel}`.trim()
      : fallbackModel
    : '—';

  const screenStateLabel =
    telemetry.isScreenOn === undefined
      ? null
      : telemetry.isScreenOn
      ? 'Awake'
      : 'Screen Off';
  const displayScreenText =
    screenStateLabel && telemetry.screenResolution
      ? `${screenStateLabel} (${telemetry.screenResolution})`
      : screenStateLabel || telemetry.screenResolution || '—';

  const appVersionText = telemetry.appVersion
    ? telemetry.appVersion.startsWith('v')
      ? telemetry.appVersion
      : `v${telemetry.appVersion}`
    : '—';

  const batteryPct =
    telemetry.batteryPercent !== undefined ? `${telemetry.batteryPercent}%` : null;
  const chargeLabel = telemetry.isCharging
    ? telemetry.chargingSource && telemetry.chargingSource !== 'Battery'
      ? `Charging (${telemetry.chargingSource})`
      : 'Charging'
    : 'On Battery';
  const batteryText = batteryPct ? `${batteryPct} • ${chargeLabel}` : '—';

  const tempLabel =
    telemetry.batteryTemperatureC && telemetry.batteryTemperatureC > 0
      ? `${telemetry.batteryTemperatureC.toFixed(1)} °C`
      : null;
  const powerLabel =
    telemetry.isPowerSaveMode === undefined
      ? null
      : telemetry.isPowerSaveMode
      ? 'Battery Saver On'
      : 'Normal Mode';
  const thermalPowerText =
    tempLabel && powerLabel
      ? `${tempLabel} • ${powerLabel}`
      : tempLabel || powerLabel || '—';

  const ramText =
    telemetry.ramUsageText ||
    (telemetry.totalRamBytes && telemetry.availableRamBytes !== undefined
      ? deviceState.formatRam(telemetry.availableRamBytes, telemetry.totalRamBytes)
      : '—');

  const storageText =
    telemetry.storageUsageText ||
    (telemetry.totalStorageBytes && telemetry.freeStorageBytes !== undefined
      ? deviceState.formatStorage(telemetry.freeStorageBytes, telemetry.totalStorageBytes)
      : '—');

  const networkLabel = telemetry.networkState || null;
  const carrierLabel = telemetry.simCarrier ? telemetry.simCarrier : null;
  const networkCarrierText =
    networkLabel && carrierLabel
      ? `${networkLabel} • ${carrierLabel}`
      : networkLabel || carrierLabel || '—';

  const ringerLabel = telemetry.ringerMode || null;
  const uptimeLabel =
    telemetry.deviceUptimeMs && telemetry.deviceUptimeMs > 0
      ? `Up ${deviceState.formatUptime(telemetry.deviceUptimeMs)}`
      : null;
  const ringerUptimeText =
    ringerLabel && uptimeLabel
      ? `${ringerLabel} • ${uptimeLabel}`
      : ringerLabel || uptimeLabel || '—';

  const stealthLabel =
    telemetry.isStealthMode === undefined
      ? null
      : telemetry.isStealthMode
      ? 'Stealth Active'
      : 'Launcher Visible';
  const lockLabel =
    telemetry.isAppLockEnabled === undefined
      ? null
      : telemetry.isAppLockEnabled
      ? 'App Lock On'
      : 'App Lock Off';
  const securityGuardsText =
    stealthLabel && lockLabel
      ? `${stealthLabel} • ${lockLabel}`
      : stealthLabel || lockLabel || '—';

  const permLabel =
    telemetry.totalPermissionsCount && telemetry.totalPermissionsCount > 0
      ? `${telemetry.grantedPermissionsCount ?? 0}/${telemetry.totalPermissionsCount} Permissions`
      : null;
  const dozeLabel =
    telemetry.batteryOptimizationIgnored === undefined
      ? null
      : telemetry.batteryOptimizationIgnored
      ? 'Unrestricted Battery'
      : 'Battery Optimized';
  const survivalPermissionsText =
    permLabel && dozeLabel
      ? `${permLabel} • ${dozeLabel}`
      : permLabel || dozeLabel || '—';

  return {
    osText,
    hardwareText,
    displayScreenText,
    appVersionText,
    batteryText,
    thermalPowerText,
    ramText,
    storageText,
    networkCarrierText,
    ringerUptimeText,
    securityGuardsText,
    survivalPermissionsText,
  };
}

/**
 * Resolves the active or cached child device state into a uniform read-only view model.
 * When `targetRawId` is supplied, resolves specifically for that paired device record.
 */
export function buildPairedDeviceViewModel(targetRawId?: string): ResolvedDeviceViewModel {
  const isConnected = deviceState.isConnected();
  const isReconnecting = deviceState.isReconnecting();
  const reconnectTarget = reconnectManager.getTargetDevice();
  const liveDevice: DeviceInfo | null = deviceState.getConnectedDevice();
  const liveRawId = liveDevice?.rawId || liveDevice?.id.replace(/\D/g, '') || null;

  const matchesLive =
    isConnected &&
    liveDevice !== null &&
    (!targetRawId || liveRawId === targetRawId);

  if (matchesLive && liveDevice) {
    const telemetryFields = formatTelemetrySnapshot(liveDevice, liveDevice.model);
    return {
      rawId: liveRawId,
      statusMode: 'online',
      isGrayedOut: false,
      badgeText: 'ONLINE',
      badgeClass: 'settings-device-badge-online',
      displayName: liveDevice.name || 'Android Device',
      displayModel: liveDevice.model || 'Android Device',
      displayId: `ID: ${liveDevice.id}`,
      lastSyncedLabel: `Last synced: ${liveDevice.lastSeen || 'Just now'}`,
      ...telemetryFields,
    };
  }

  const cachedRecord: LastConnectedDevice | null = targetRawId
    ? deviceState.getPairedDeviceRecord(targetRawId)
    : deviceState.getLastConnectedDevice();

  if (cachedRecord) {
    const isReconnectingTarget =
      isReconnecting &&
      (!reconnectTarget || reconnectTarget.rawId === cachedRecord.rawId);
    const telemetryFields = formatTelemetrySnapshot(
      cachedRecord.lastTelemetry,
      cachedRecord.model
    );
    return {
      rawId: cachedRecord.rawId,
      statusMode: isReconnectingTarget ? 'reconnecting' : 'offline',
      isGrayedOut: true,
      badgeText: isReconnectingTarget ? 'RECONNECTING' : 'OFFLINE • CACHED',
      badgeClass: isReconnectingTarget
        ? 'settings-device-badge-reconnecting'
        : 'settings-device-badge-offline',
      displayName: cachedRecord.name || 'Paired Child Device',
      displayModel: cachedRecord.model || 'Cached Hardware Record',
      displayId: `ID: ${cachedRecord.formattedId}`,
      lastSyncedLabel: isReconnectingTarget
        ? 'Attempting to restore peer connection...'
        : formatLastConnectedTimestamp(cachedRecord.lastConnectedAt),
      ...telemetryFields,
    };
  }

  return {
    rawId: null,
    statusMode: 'empty',
    isGrayedOut: true,
    badgeText: 'NOT PAIRED',
    badgeClass: 'settings-device-badge-offline',
    displayName: 'No Child Device Paired Yet',
    displayModel: 'Connect a 12-digit Child ID from the pairing screen to view telemetry',
    displayId: 'ID: —',
    lastSyncedLabel: 'Awaiting first connection',
    ...formatTelemetrySnapshot(undefined),
  };
}

function renderTelemetryTile(label: string, value: string): string {
  return `
    <div class="settings-telemetry-tile">
      <span class="settings-telemetry-tile-label">${escapeHtml(label)}</span>
      <span class="settings-telemetry-tile-val">${escapeHtml(value)}</span>
    </div>
  `;
}

/**
 * Generates the read-only telemetry HTML markup for a resolved device view model.
 */
export function renderDeviceDetailHtml(vm: ResolvedDeviceViewModel): string {
  const grayClass = vm.isGrayedOut ? ' is-offline' : '';

  return `
    <div class="settings-device-card${grayClass}" id="paired-device-summary-card">
      <div class="settings-device-card-header">
        <div class="settings-device-card-header-left">
          <img class="settings-device-card-icon" src="assets/icons/settings/devices.svg" alt="" />
          <div class="settings-device-identity">
            <div class="settings-device-alias-row">
              <span id="paired-device-name" class="settings-device-name">${escapeHtml(vm.displayName)}</span>
              <span id="paired-device-status-badge" class="${vm.badgeClass}">${escapeHtml(vm.badgeText)}</span>
            </div>
            <span id="paired-device-subtitle" class="settings-device-subtitle">${escapeHtml(vm.displayModel)}</span>
            <span id="paired-device-id" class="settings-device-id">${escapeHtml(vm.displayId)}</span>
          </div>
        </div>
        <span id="paired-device-last-sync" class="settings-device-sync-time">${escapeHtml(vm.lastSyncedLabel)}</span>
      </div>
    </div>

    <div class="settings-group${grayClass}">
      <div class="settings-group-title">DEVICE &amp; OS</div>
      <div class="settings-device-telemetry-grid settings-device-telemetry-grid-2col">
        ${renderTelemetryTile('Operating System', vm.osText)}
        ${renderTelemetryTile('Hardware Model', vm.hardwareText)}
        ${renderTelemetryTile('Display & Screen State', vm.displayScreenText)}
        ${renderTelemetryTile('Child App Version', vm.appVersionText)}
      </div>
    </div>

    <div class="settings-group${grayClass}">
      <div class="settings-group-title">POWER &amp; MEMORY</div>
      <div class="settings-device-telemetry-grid settings-device-telemetry-grid-2col">
        ${renderTelemetryTile('Battery & Charging', vm.batteryText)}
        ${renderTelemetryTile('Thermal & Power Mode', vm.thermalPowerText)}
        ${renderTelemetryTile('System Memory (RAM)', vm.ramText)}
        ${renderTelemetryTile('Internal Storage', vm.storageText)}
      </div>
    </div>

    <div class="settings-group${grayClass}">
      <div class="settings-group-title">NETWORK &amp; SYSTEM POSTURE</div>
      <div class="settings-device-telemetry-grid settings-device-telemetry-grid-2col">
        ${renderTelemetryTile('Network & Carrier', vm.networkCarrierText)}
        ${renderTelemetryTile('Ringer & Uptime', vm.ringerUptimeText)}
        ${renderTelemetryTile('Security Guards', vm.securityGuardsText)}
        ${renderTelemetryTile('Survival & Permissions', vm.survivalPermissionsText)}
      </div>
    </div>
  `;
}

/**
 * Renders a standalone read-only telemetry inspector card (subscribed to `deviceState`).
 */
export function renderDeviceCardSetting(targetRawId?: string): HTMLElement {
  if (unsubscribeDeviceCard) {
    unsubscribeDeviceCard();
    unsubscribeDeviceCard = null;
  }

  const wrapper = document.createElement('div');
  wrapper.className = 'settings-device-inspector';

  const renderIntoWrapper = () => {
    wrapper.innerHTML = renderDeviceDetailHtml(buildPairedDeviceViewModel(targetRawId));
  };

  renderIntoWrapper();

  unsubscribeDeviceCard = deviceState.subscribe(() => {
    if (wrapper.isConnected === false) {
      unsubscribeDeviceCard?.();
      unsubscribeDeviceCard = null;
      return;
    }
    renderIntoWrapper();
  });

  return wrapper;
}
