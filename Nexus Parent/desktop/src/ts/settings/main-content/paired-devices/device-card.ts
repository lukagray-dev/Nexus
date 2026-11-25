/**
 * @file device-card.ts
 * @description Active Paired Device Card & Nickname Alias setting.
 *
 * Hey friend! Displays the active paired child device telemetry snapshot and
 * allows editing the friendly device alias.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';

/**
 * Renders the Active Device Card with editable alias and unpair action.
 */
export function renderDeviceCardSetting(): HTMLElement {
  const card = document.createElement('div');
  card.className = 'settings-device-card';

  const alias = settingsState.getValue('deviceAlias');

  card.innerHTML = `
    <div class="settings-device-card-header">
      <div class="settings-device-card-header-left">
        <img class="settings-device-card-icon" src="assets/icons/settings/devices.svg" alt="" />
        <div>
          <div class="settings-device-alias-row">
            <input type="text" id="setting-device-alias" class="settings-device-alias-input" value="${alias}" placeholder="Device Nickname" />
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
  `;

  const aliasInput = card.querySelector<HTMLInputElement>('#setting-device-alias');
  aliasInput?.addEventListener('input', () => {
    settingsState.setValue('deviceAlias', aliasInput.value.trim());
  });

  const unpairBtn = card.querySelector<HTMLButtonElement>('#btn-unpair-device');
  unpairBtn?.addEventListener('click', () => {
    showToast('Device unpaired successfully');
  });

  return card;
}
