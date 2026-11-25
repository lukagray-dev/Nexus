/**
 * @file auto-connect-last-device.ts
 * @description Auto-Connect Last Paired Device on Launch setting and startup runner.
 *
 * Hey friend! When enabled, Nexus Parent automatically tries once on boot to reconnect
 * to the last paired 12-digit child device stored in `deviceState` so parents land straight
 * on their dashboard without clicking Quick Connect every time.
 */

import { settingsState } from '../../sidebar/state.js';
import { deviceState } from '../../../shared/device-state.js';
import { showToast } from '../../../shared/toast.js';

/**
 * Renders the "Auto-Connect Last Paired Device on Launch" toggle row.
 */
export function renderAutoConnectLastDeviceSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('autoConnectLastDevice');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Auto-Connect Last Paired Device on Launch</span>
      <span class="settings-label-desc">Automatically reconnect to the most recently paired child phone when Nexus Parent starts</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-auto-connect-last-device" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-auto-connect-last-device');
  toggle?.addEventListener('change', () => {
    const nextValue = toggle.checked;
    settingsState.setValue('autoConnectLastDevice', nextValue);
    showToast(
      nextValue
        ? 'Auto-connect on launch enabled'
        : 'Auto-connect on launch disabled'
    );
  });

  return row;
}

/**
 * Performs a single startup connection attempt to the last paired device when enabled.
 */
export async function runStartupAutoConnect(): Promise<boolean> {
  if (!settingsState.getValue('autoConnectLastDevice')) {
    return false;
  }

  if (deviceState.isConnected() || deviceState.isReconnecting()) {
    return false;
  }

  const lastDevice = deviceState.getLastConnectedDevice();
  if (!lastDevice || !lastDevice.rawId || lastDevice.rawId.length !== 12) {
    return false;
  }

  const label = lastDevice.name || lastDevice.formattedId;
  showToast(`Auto-connecting to ${label}...`);

  try {
    const ok = await deviceState.connectLive(lastDevice.rawId);
    if (ok) {
      showToast(`Connected to ${label}`);
    }
    return ok;
  } catch (err) {
    console.warn('[GeneralSystem:AutoConnect] Startup auto-connect failed:', err);
    showToast(`${label} is currently offline — ready for manual connect`);
    return false;
  }
}
