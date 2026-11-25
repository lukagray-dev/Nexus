/**
 * @file turn-server.ts
 * @description Secondary TURN Relay Server input setting for Connection & WebRTC.
 *
 * Hey friend! Stores the fallback TURN relay URL used when strict firewalls block UDP.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Secondary TURN Relay Server input setting row.
 */
export function renderTurnServerSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row-vertical';

  const value = settingsState.getValue('secondaryTurnServer');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Secondary TURN Relay Server</span>
      <span class="settings-label-desc">Private TURN server used when symmetric cellular firewalls block direct UDP traffic</span>
    </div>
    <input type="text" id="setting-turn-server" class="settings-input" value="${value}" placeholder="turn:turn.nexus-relay.net:3478" />
  `;

  const turnInput = row.querySelector<HTMLInputElement>('#setting-turn-server');
  turnInput?.addEventListener('input', () => {
    settingsState.setValue('secondaryTurnServer', turnInput.value.trim());
  });

  return row;
}
