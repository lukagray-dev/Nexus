/**
 * @file stun-server.ts
 * @description Primary STUN Endpoint input setting for Connection & WebRTC.
 *
 * Hey friend! Stores the primary STUN URI used for NAT external address discovery.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Primary STUN Endpoint input setting row.
 */
export function renderStunServerSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row-vertical';

  const value = settingsState.getValue('primaryStunServer');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Primary STUN Endpoint</span>
      <span class="settings-label-desc">Public STUN server used to discover external IP and port mapping</span>
    </div>
    <input type="text" id="setting-stun-server" class="settings-input" value="${value}" placeholder="stun:stun.l.google.com:19302" />
  `;

  const stunInput = row.querySelector<HTMLInputElement>('#setting-stun-server');
  stunInput?.addEventListener('input', () => {
    settingsState.setValue('primaryStunServer', stunInput.value.trim());
  });

  return row;
}
