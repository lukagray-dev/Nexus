/**
 * @file connection-webrtc.ts
 * @description Connection & WebRTC Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Automatic reconnection attempts when the desktop app launches.
 * 2. Primary STUN server and private TURN relay endpoints for NAT traversal.
 * 3. ICE candidate transport policy (All direct P2P vs Relay-Only proxy).
 * 4. Non-trickle ICE candidate gathering timeout window.
 * 5. Firebase RTDB presence heartbeat cadence.
 */

import { settingsState } from '../sidebar/state.js';

export function renderConnectionWebrtcContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Connection & WebRTC</h2>
      <p class="settings-content-desc">Configure peer-to-peer signaling, STUN/TURN NAT traversal gateways, and automatic reconnection parameters.</p>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">SESSION LIFECYCLE</div>

      <!-- 1. Auto-Reconnect on Launch -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Auto-Reconnect on Startup</span>
          <span class="settings-label-desc">Automatically establish WebRTC handshake with last-paired child device upon launch</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-auto-reconnect" ${values.autoReconnect ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>

      <!-- 2. ICE Transport Policy -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">ICE Transport Policy</span>
          <span class="settings-label-desc">Relay Only routes all media through TURN servers, concealing parent IP from local network</span>
        </div>
        <select id="setting-ice-policy" class="settings-select">
          <option value="all" ${values.iceTransportPolicy === 'all' ? 'selected' : ''}>All (Direct P2P + Relay Fallback, Default)</option>
          <option value="relay" ${values.iceTransportPolicy === 'relay' ? 'selected' : ''}>Relay Only (Enforce High Privacy Proxy)</option>
        </select>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">ICE SERVERS & NAT TRAVERSAL</div>

      <!-- 3. Primary STUN Server -->
      <div class="settings-row-vertical">
        <div class="settings-row-label">
          <span class="settings-label-title">Primary STUN Endpoint</span>
          <span class="settings-label-desc">Public STUN server used to discover external IP and port mapping</span>
        </div>
        <input type="text" id="setting-stun-server" class="settings-input" value="${values.primaryStunServer}" placeholder="stun:stun.l.google.com:19302" />
      </div>

      <!-- 4. Secondary TURN Relay Server -->
      <div class="settings-row-vertical">
        <div class="settings-row-label">
          <span class="settings-label-title">Secondary TURN Relay Server</span>
          <span class="settings-label-desc">Private TURN server used when symmetric cellular firewalls block direct UDP traffic</span>
        </div>
        <input type="text" id="setting-turn-server" class="settings-input" value="${values.secondaryTurnServer}" placeholder="turn:turn.nexus-relay.net:3478" />
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">TIMING & SIGNALING CALIBRATION</div>

      <!-- 5. ICE Gathering Timeout -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Non-Trickle ICE Gather Timeout</span>
          <span class="settings-label-desc">Max milliseconds allowed to gather local network candidates before dispatching SDP</span>
        </div>
        <div class="settings-slider-container">
          <input type="range" id="setting-ice-timeout" class="settings-slider" min="200" max="800" step="50" value="${values.iceGatherTimeout}" />
          <span id="setting-ice-timeout-val" class="settings-val-badge">${values.iceGatherTimeout} ms</span>
        </div>
      </div>

      <!-- 6. Signaling Heartbeat Interval -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Signaling Heartbeat Cadence</span>
          <span class="settings-label-desc">Keep-alive frequency to verify child device presence on Firebase RTDB</span>
        </div>
        <div class="settings-slider-container">
          <input type="range" id="setting-heartbeat" class="settings-slider" min="10" max="60" step="5" value="${values.signalingHeartbeat}" />
          <span id="setting-heartbeat-val" class="settings-val-badge">${values.signalingHeartbeat} s</span>
        </div>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const reconnectToggle = container.querySelector<HTMLInputElement>('#setting-auto-reconnect');
  reconnectToggle?.addEventListener('change', () => {
    settingsState.setValue('autoReconnect', reconnectToggle.checked);
  });

  const policySelect = container.querySelector<HTMLSelectElement>('#setting-ice-policy');
  policySelect?.addEventListener('change', () => {
    settingsState.setValue('iceTransportPolicy', policySelect.value as 'all' | 'relay');
  });

  const stunInput = container.querySelector<HTMLInputElement>('#setting-stun-server');
  stunInput?.addEventListener('input', () => {
    settingsState.setValue('primaryStunServer', stunInput.value.trim());
  });

  const turnInput = container.querySelector<HTMLInputElement>('#setting-turn-server');
  turnInput?.addEventListener('input', () => {
    settingsState.setValue('secondaryTurnServer', turnInput.value.trim());
  });

  const iceTimeoutSlider = container.querySelector<HTMLInputElement>('#setting-ice-timeout');
  const iceTimeoutVal = container.querySelector<HTMLElement>('#setting-ice-timeout-val');
  iceTimeoutSlider?.addEventListener('input', () => {
    const val = parseInt(iceTimeoutSlider.value, 10);
    if (iceTimeoutVal) iceTimeoutVal.textContent = `${val} ms`;
    settingsState.setValue('iceGatherTimeout', val);
  });

  const heartbeatSlider = container.querySelector<HTMLInputElement>('#setting-heartbeat');
  const heartbeatVal = container.querySelector<HTMLElement>('#setting-heartbeat-val');
  heartbeatSlider?.addEventListener('input', () => {
    const val = parseInt(heartbeatSlider.value, 10);
    if (heartbeatVal) heartbeatVal.textContent = `${val} s`;
    settingsState.setValue('signalingHeartbeat', val);
  });

  return container;
}
