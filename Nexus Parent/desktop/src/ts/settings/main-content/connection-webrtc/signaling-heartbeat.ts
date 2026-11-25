/**
 * @file signaling-heartbeat.ts
 * @description Signaling Heartbeat Cadence slider setting.
 *
 * Hey friend! Configures how frequently presence heartbeats refresh on Firebase RTDB.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Signaling Heartbeat Cadence range slider row.
 */
export function renderSignalingHeartbeatSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const seconds = settingsState.getValue('signalingHeartbeat');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Signaling Heartbeat Cadence</span>
      <span class="settings-label-desc">Keep-alive frequency to verify child device presence on Firebase RTDB</span>
    </div>
    <div class="settings-slider-container">
      <input type="range" id="setting-heartbeat" class="settings-slider" min="10" max="60" step="5" value="${seconds}" />
      <span id="setting-heartbeat-val" class="settings-val-badge">${seconds} s</span>
    </div>
  `;

  const slider = row.querySelector<HTMLInputElement>('#setting-heartbeat');
  const badge = row.querySelector<HTMLElement>('#setting-heartbeat-val');
  slider?.addEventListener('input', () => {
    const val = parseInt(slider.value, 10);
    if (badge) badge.textContent = `${val} s`;
    settingsState.setValue('signalingHeartbeat', val);
  });

  return row;
}
