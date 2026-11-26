/**
 * @file paired-devices.ts
 * @description Paired Devices Settings master-detail list & telemetry inspector.
 *
 * Hey friend! Here is how Paired Devices works:
 * 1. List View (default): Shows every child device the parent has ever connected to,
 *    with online/offline status, summary info, and a direct `Connect` button.
 * 2. Detail View: Clicking any device in the list opens its dedicated read-only
 *    telemetry page (live full-contrast when online, grayed-out cached data when offline),
 *    with a Back button to return to the devices list.
 */

import {
  deviceState,
  type LastConnectedDevice,
} from '../../../shared/device-state.js';
import { sidebarState } from '../../../left-sidebar/state.js';
import { reconnectManager } from '../../../shared/reconnect-manager.js';
import { showToast } from '../../../shared/toast.js';
import { createSettingsSectionHeader, escapeHtml } from '../helpers.js';
import {
  buildPairedDeviceViewModel,
  formatLastConnectedTimestamp,
  renderDeviceDetailHtml,
} from './device-card.js';

let unsubscribePairedDevices: (() => void) | null = null;

function formatDeviceSummaryLine(device: LastConnectedDevice, isOnline: boolean): string {
  const tel = device.lastTelemetry;
  const parts: string[] = [];

  if (tel?.androidVersion) {
    parts.push(`Android ${tel.androidVersion}`);
  }
  if (tel?.batteryPercent !== undefined) {
    parts.push(`${tel.batteryPercent}%${tel.isCharging ? ' Charging' : ''}`);
  }
  if (tel?.networkState) {
    parts.push(tel.networkState);
  }

  if (parts.length > 0) {
    return parts.join(' • ');
  }
  return isOnline ? 'Connected via WebRTC P2P' : formatLastConnectedTimestamp(device.lastConnectedAt);
}

/**
 * Renders the Paired Devices Settings main content section (List + Detail Telemetry View).
 */
export function renderPairedDevicesContent(): HTMLElement {
  if (unsubscribePairedDevices) {
    unsubscribePairedDevices();
    unsubscribePairedDevices = null;
  }

  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const header = createSettingsSectionHeader(
    'Paired Devices',
    'Previously connected child phones. Select a device to inspect its telemetry or click Connect.'
  );
  const body = document.createElement('div');
  body.id = 'paired-devices-view-body';
  body.className = 'paired-devices-view-body';

  container.appendChild(header);
  container.appendChild(body);

  let selectedDeviceRawId: string | null = null;
  let connectingRawId: string | null = null;

  const connectToPairedDevice = async (rawId: string): Promise<void> => {
    if (connectingRawId) return;

    const record = deviceState.getPairedDeviceRecord(rawId);
    const label = record?.name || deviceState.formatId(rawId);

    const activeRawId =
      deviceState.getConnectedDevice()?.rawId ||
      deviceState.getConnectedDevice()?.id.replace(/\D/g, '');

    if (deviceState.isConnected() && activeRawId === rawId) {
      return;
    }

    if (deviceState.isConnected() || deviceState.isReconnecting()) {
      deviceState.disconnect();
    }

    connectingRawId = rawId;
    renderCurrentView();
    showToast(`Connecting to ${label}...`);

    try {
      const ok = await deviceState.connectLive(rawId);
      if (ok) {
        showToast(`Connected to ${label}`);
      } else {
        showToast(`${label} is currently offline`);
      }
    } catch (err) {
      console.warn('[PairedDevices] Failed to connect to paired device:', err);
      showToast(`${label} is currently offline`);
    } finally {
      connectingRawId = null;
      renderCurrentView();
    }
  };

  const disconnectFromPairedDevice = (rawId: string): void => {
    const record = deviceState.getPairedDeviceRecord(rawId);
    const liveName = deviceState.getConnectedDevice()?.name;
    const label = liveName || record?.name || deviceState.formatId(rawId);
    sidebarState.setActiveFeatureId(null);
    deviceState.disconnect();
    showToast(`Disconnected from ${label}`);
  };

  const renderListView = (): void => {
    const titleEl = header.querySelector<HTMLElement>('.settings-content-title');
    const descEl = header.querySelector<HTMLElement>('.settings-content-desc');
    if (titleEl) titleEl.textContent = 'Paired Devices';
    if (descEl) {
      descEl.textContent =
        'Previously connected child phones. Select a device to inspect its telemetry or click Connect.';
    }

    const history = deviceState.getPairedDevicesHistory();
    if (history.length === 0) {
      const emptyVm = buildPairedDeviceViewModel();
      body.innerHTML = `<div class="settings-device-inspector">${renderDeviceDetailHtml(emptyVm)}</div>`;
      return;
    }

    const isConnected = deviceState.isConnected();
    const isReconnecting = deviceState.isReconnecting();
    const liveDevice = deviceState.getConnectedDevice();
    const liveRawId = liveDevice?.rawId || liveDevice?.id.replace(/\D/g, '') || null;
    const reconnectRawId = reconnectManager.getTargetDevice()?.rawId || null;

    const rowsHtml = history
      .map((dev) => {
        const isDeviceOnline = isConnected && liveRawId === dev.rawId;
        const isDeviceReconnecting = isReconnecting && reconnectRawId === dev.rawId;
        const isConnectingNow = connectingRawId === dev.rawId;

        const statusBadgeClass = isDeviceOnline
          ? 'settings-device-badge-online'
          : isDeviceReconnecting
          ? 'settings-device-badge-reconnecting'
          : 'settings-device-badge-offline';
        const statusBadgeText = isDeviceOnline
          ? 'ONLINE'
          : isDeviceReconnecting
          ? 'RECONNECTING'
          : 'OFFLINE';

        const displayName = isDeviceOnline && liveDevice?.name ? liveDevice.name : dev.name || 'Paired Child Device';
        const displayModel =
          isDeviceOnline && liveDevice?.model ? liveDevice.model : dev.model || 'Cached Hardware Record';
        const summarySource: LastConnectedDevice =
          isDeviceOnline && liveDevice
            ? {
                ...dev,
                lastTelemetry: {
                  ...dev.lastTelemetry,
                  androidVersion: liveDevice.androidVersion,
                  batteryPercent: liveDevice.batteryPercent,
                  isCharging: liveDevice.isCharging,
                  networkState: liveDevice.networkState,
                },
              }
            : dev;

        const connectBtnLabel = isDeviceOnline
          ? 'Disconnect'
          : isConnectingNow
          ? 'Connecting...'
          : 'Connect';
        const connectBtnClass = isDeviceOnline
          ? 'settings-btn settings-btn-danger paired-device-connect-btn'
          : 'settings-btn settings-btn-primary paired-device-connect-btn';
        const actionType = isDeviceOnline ? 'disconnect' : 'connect';

        return `
          <div
            class="paired-device-list-item${isDeviceOnline ? ' is-online' : ' is-offline-row'}"
            data-raw-id="${escapeHtml(dev.rawId)}"
            role="button"
            tabindex="0"
            title="View detailed telemetry for ${escapeHtml(displayName)}"
          >
            <div class="paired-device-list-left">
              <img class="settings-device-card-icon" src="assets/icons/settings/devices.svg" alt="" />
              <div class="paired-device-list-info">
                <div class="settings-device-alias-row">
                  <span class="settings-device-name">${escapeHtml(displayName)}</span>
                  <span class="${statusBadgeClass}">${statusBadgeText}</span>
                </div>
                <span class="settings-device-subtitle">${escapeHtml(displayModel)} • ${escapeHtml(dev.formattedId)}</span>
                <span class="paired-device-list-meta">${escapeHtml(formatDeviceSummaryLine(summarySource, isDeviceOnline))}</span>
              </div>
            </div>
            <div class="paired-device-list-actions">
              <button
                type="button"
                class="${connectBtnClass}"
                data-connect-raw-id="${escapeHtml(dev.rawId)}"
                data-device-action="${actionType}"
                ${isConnectingNow ? 'disabled' : ''}
              >
                ${connectBtnLabel}
              </button>
              <img class="paired-device-chevron" src="assets/icons/left-sidebar/chevron-right.svg" alt="" />
            </div>
          </div>
        `;
      })
      .join('');

    body.innerHTML = `<div id="paired-devices-list" class="paired-devices-list">${rowsHtml}</div>`;

    const items = body.querySelectorAll<HTMLElement>('.paired-device-list-item');
    items.forEach((item) => {
      const rawId = item.dataset.rawId;
      if (!rawId) return;

      item.addEventListener('click', () => {
        selectedDeviceRawId = rawId;
        renderCurrentView();
      });
    });

    const connectButtons = body.querySelectorAll<HTMLButtonElement>('.paired-device-connect-btn');
    connectButtons.forEach((btn) => {
      const rawId = btn.dataset.connectRawId;
      if (!rawId) return;

      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (btn.dataset.deviceAction === 'disconnect') {
          disconnectFromPairedDevice(rawId);
        } else {
          void connectToPairedDevice(rawId);
        }
      });
    });
  };

  const renderDetailView = (rawId: string): void => {
    const vm = buildPairedDeviceViewModel(rawId);
    const titleEl = header.querySelector<HTMLElement>('.settings-content-title');
    const descEl = header.querySelector<HTMLElement>('.settings-content-desc');
    if (titleEl) titleEl.textContent = vm.displayName;
    if (descEl) {
      descEl.textContent =
        'Read-only hardware, power, connectivity, and security telemetry for this paired child phone.';
    }

    const isOnline = vm.statusMode === 'online';
    const isConnectingNow = connectingRawId === rawId;

    body.innerHTML = `
      <div class="paired-device-detail-bar">
        <button type="button" id="btn-paired-devices-back" class="settings-btn-inline paired-device-back-btn">
          <img src="assets/icons/main-content/arrow-left.svg" alt="" class="paired-device-back-icon" />
          <span>All Paired Devices</span>
        </button>
        ${
          !isOnline
            ? `<button
                type="button"
                id="btn-detail-connect-device"
                class="settings-btn settings-btn-primary"
                ${isConnectingNow ? 'disabled' : ''}
              >
                ${isConnectingNow ? 'Connecting...' : 'Connect'}
              </button>`
            : ''
        }
      </div>
      <div class="settings-device-inspector">
        ${renderDeviceDetailHtml(vm)}
      </div>
    `;

    body.querySelector<HTMLButtonElement>('#btn-paired-devices-back')?.addEventListener('click', () => {
      selectedDeviceRawId = null;
      renderCurrentView();
    });

    body.querySelector<HTMLButtonElement>('#btn-detail-connect-device')?.addEventListener('click', () => {
      void connectToPairedDevice(rawId);
    });
  };

  const renderCurrentView = (): void => {
    if (selectedDeviceRawId) {
      const stillExists =
        deviceState.getPairedDeviceRecord(selectedDeviceRawId) !== null ||
        (deviceState.isConnected() &&
          (deviceState.getConnectedDevice()?.rawId ||
            deviceState.getConnectedDevice()?.id.replace(/\D/g, '')) === selectedDeviceRawId);

      if (stillExists) {
        renderDetailView(selectedDeviceRawId);
        return;
      }
      selectedDeviceRawId = null;
    }
    renderListView();
  };

  renderCurrentView();

  unsubscribePairedDevices = deviceState.subscribe(() => {
    if (container.isConnected === false) {
      unsubscribePairedDevices?.();
      unsubscribePairedDevices = null;
      return;
    }
    renderCurrentView();
  });

  return container;
}
