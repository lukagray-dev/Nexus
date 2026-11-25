/**
 * @file ice-policy.ts
 * @description ICE Transport Policy dropdown setting for Connection & WebRTC.
 *
 * Hey friend! Configures whether WebRTC uses direct P2P plus relay fallback (`all`)
 * or routes strictly through TURN relays (`relay`).
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the ICE Transport Policy dropdown setting row.
 */
export function renderIcePolicySetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const policy = settingsState.getValue('iceTransportPolicy');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">ICE Transport Policy</span>
      <span class="settings-label-desc">Relay Only routes all media through TURN servers, concealing parent IP from local network</span>
    </div>
    <select id="setting-ice-policy" class="settings-select">
      <option value="all" ${policy === 'all' ? 'selected' : ''}>All (Direct P2P + Relay Fallback, Default)</option>
      <option value="relay" ${policy === 'relay' ? 'selected' : ''}>Relay Only (Enforce High Privacy Proxy)</option>
    </select>
  `;

  const policySelect = row.querySelector<HTMLSelectElement>('#setting-ice-policy');
  policySelect?.addEventListener('change', () => {
    settingsState.setValue('iceTransportPolicy', policySelect.value as 'all' | 'relay');
  });

  return row;
}
