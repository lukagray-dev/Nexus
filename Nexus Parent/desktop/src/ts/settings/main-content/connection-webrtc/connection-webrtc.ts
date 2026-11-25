/**
 * @file connection-webrtc.ts
 * @description Connection & WebRTC Settings category compositor.
 *
 * Hey friend! Combines session lifecycle, ICE server, and signaling calibration settings.
 */

import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderAutoReconnectSetting } from './auto-reconnect.js';
import { renderIcePolicySetting } from './ice-policy.js';
import { renderStunServerSetting } from './stun-server.js';
import { renderTurnServerSetting } from './turn-server.js';
import { renderIceTimeoutSetting } from './ice-timeout.js';
import { renderSignalingHeartbeatSetting } from './signaling-heartbeat.js';

/**
 * Renders the Connection & WebRTC Settings main content section.
 */
export function renderConnectionWebrtcContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Connection & WebRTC',
      'Configure peer-to-peer signaling, STUN/TURN NAT traversal gateways, and automatic reconnection parameters.'
    )
  );

  container.appendChild(
    createSettingsGroup('SESSION LIFECYCLE', [
      renderAutoReconnectSetting(),
      renderIcePolicySetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('ICE SERVERS & NAT TRAVERSAL', [
      renderStunServerSetting(),
      renderTurnServerSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('TIMING & SIGNALING CALIBRATION', [
      renderIceTimeoutSetting(),
      renderSignalingHeartbeatSetting(),
    ])
  );

  return container;
}
