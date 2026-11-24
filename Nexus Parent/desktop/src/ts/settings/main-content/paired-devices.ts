/**
 * @file paired-devices.ts
 * @description Paired Devices Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Paired child mobile hardware overview and nickname alias customization.
 * 2. Real-time battery, storage, and operating system telemetry snapshot.
 * 3. Pairing new devices via QR code / 12-digit PIN.
 * 4. Clearing offline cache and remote unpairing.
 */

import { settingsState } from '../sidebar/state.js';
import { showToast } from '../../shared/toast.js';

export function renderPairedDevicesContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Paired Child Devices</h2>
      <p class="settings-content-desc">Manage enrolled mobile devices, customize friendly nicknames, and monitor hardware telemetry.</p>
    </div>

    <!-- Active Device Card -->
    <div class="settings-device-card">
      <div class="settings-device-card-header">
        <div class="settings-device-card-header-left">
          <img class="settings-device-card-icon" src="assets/icons/settings/devices.svg" alt="" />
          <div>
            <div class="settings-device-alias-row">
              <input type="text" id="setting-device-alias" class="settings-device-alias-input" value="${values.deviceAlias}" placeholder="Device Nickname" />
              <span class="settings-device-badge-online">ONLINE</span>
            </div>
            <span class="settings-device-id">ID: 4821 9402 1104</span>
          </div>
        </div>
        <button type="button" id="btn-unpair-device" class="settings-btn-danger-outline" title="Unpair and drop connection keys">Unpair</button>
      </div>

      <div class="settings-device-telemetry-grid">
        <div class="settings-telemetry-tile">
          <span class="settings-telemetry-tile-label">Operating System</span>
          <span class="settings-telemetry-tile-val">Android 14 (API 34)</span>
        </div>
        <div class="settings-telemetry-tile">
          <span class="settings-telemetry-tile-label">Battery Level</span>
          <span class="settings-telemetry-tile-val">82% Charging</span>
        </div>
        <div class="settings-telemetry-tile">
          <span class="settings-telemetry-tile-label">Storage Free</span>
          <span class="settings-telemetry-tile-val">58.4 GB / 128 GB</span>
        </div>
        <div class="settings-telemetry-tile">
          <span class="settings-telemetry-tile-label">Connection Type</span>
          <span class="settings-telemetry-tile-val">WebRTC Direct P2P</span>
        </div>
      </div>
    </div>

    <div class="settings-group" style="margin-top: 18px;">
      <div class="settings-group-title">DEVICE ENROLLMENT & ACTIONS</div>

      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Enroll New Child Phone</span>
          <span class="settings-label-desc">Generate secure pairing QR code and 12-digit one-time token</span>
        </div>
        <button type="button" id="btn-pair-new" class="settings-btn settings-btn-primary">
          <img src="assets/icons/settings/devices.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
          Pair New Device
        </button>
      </div>

      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Clear Device Local Cache</span>
          <span class="settings-label-desc">Purge cached storage directory trees and contact snapshots for this child</span>
        </div>
        <button type="button" id="btn-clear-device-cache" class="settings-btn settings-btn-secondary">
          <img src="assets/icons/settings/trash.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
          Clear Cache
        </button>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const aliasInput = container.querySelector<HTMLInputElement>('#setting-device-alias');
  aliasInput?.addEventListener('input', () => {
    settingsState.setValue('deviceAlias', aliasInput.value.trim());
  });

  const pairBtn = container.querySelector<HTMLButtonElement>('#btn-pair-new');
  pairBtn?.addEventListener('click', () => {
    showToast('Pairing dialog: Scan QR on child device');
  });

  const clearBtn = container.querySelector<HTMLButtonElement>('#btn-clear-device-cache');
  clearBtn?.addEventListener('click', () => {
    showToast('Local device cache cleared');
  });

  const unpairBtn = container.querySelector<HTMLButtonElement>('#btn-unpair-device');
  unpairBtn?.addEventListener('click', () => {
    showToast('Device unpaired successfully');
  });

  return container;
}
