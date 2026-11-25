/**
 * @file auto-reconnect.ts
 * @description Auto-Reconnect on Startup toggle setting for Connection & WebRTC.
 *
 * Hey friend! Controls whether Nexus Parent automatically tries reconnecting to the
 * last paired child device upon app launch.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Auto-Reconnect on Startup toggle setting row.
 */
export function renderAutoReconnectSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('autoReconnect');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Auto-Reconnect on Startup</span>
      <span class="settings-label-desc">Automatically establish WebRTC handshake with last-paired child device upon launch</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-auto-reconnect" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const reconnectToggle = row.querySelector<HTMLInputElement>('#setting-auto-reconnect');
  reconnectToggle?.addEventListener('change', () => {
    settingsState.setValue('autoReconnect', reconnectToggle.checked);
  });

  return row;
}
