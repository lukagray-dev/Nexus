/**
 * @file ice-timeout.ts
 * @description Non-Trickle ICE Gather Timeout slider setting.
 *
 * Hey friend! Adjusts how long WebRTC waits to bundle ICE candidates into the SDP offer.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Non-Trickle ICE Gather Timeout range slider row.
 */
export function renderIceTimeoutSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const timeoutMs = settingsState.getValue('iceGatherTimeout');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Non-Trickle ICE Gather Timeout</span>
      <span class="settings-label-desc">Max milliseconds allowed to gather local network candidates before dispatching SDP</span>
    </div>
    <div class="settings-slider-container">
      <input type="range" id="setting-ice-timeout" class="settings-slider" min="200" max="800" step="50" value="${timeoutMs}" />
      <span id="setting-ice-timeout-val" class="settings-val-badge">${timeoutMs} ms</span>
    </div>
  `;

  const slider = row.querySelector<HTMLInputElement>('#setting-ice-timeout');
  const badge = row.querySelector<HTMLElement>('#setting-ice-timeout-val');
  slider?.addEventListener('input', () => {
    const val = parseInt(slider.value, 10);
    if (badge) badge.textContent = `${val} ms`;
    settingsState.setValue('iceGatherTimeout', val);
  });

  return row;
}
