/**
 * @file transport-storage.ts
 * @description Live WebRTC Transport, Firebase Auth, Peer Session, and Real LocalStorage Footprint.
 *
 * Hey friend! Unlike dummy static telemetry cards, this module reports real live values:
 * - WebRTC P2P transport & encryption (DTLS-SRTP + SCTP, Non-Trickle ICE STUN mirror count).
 * - Firebase RTDB project ID and live anonymous authentication status.
 * - Active child connection state (`deviceState`).
 * - Actual measured UTF-16 byte footprint of `nexus_*` entries in `localStorage`.
 */

import { FIREBASE_CONFIG, STUN_SERVERS } from '../../../webrtc/firebase-config.js';
import { authManager } from '../../../webrtc/auth-manager.js';
import { deviceState } from '../../../shared/device-state.js';
import { VOICE_STORAGE_KEY } from '../../../main-content/mic/voice-presets.js';
import { SETTINGS_STORAGE_KEY, settingsState } from '../../sidebar/state.js';
import { createSettingsGroup, escapeHtml } from '../helpers.js';

export interface LocalStorageFootprint {
  bytes: number;
  keyCount: number;
  formatted: string;
}

const KNOWN_NEXUS_STORAGE_KEYS = [
  SETTINGS_STORAGE_KEY,
  'nexus_last_connected_child_device',
  VOICE_STORAGE_KEY,
];

let cleanupTransportSubscriptions: (() => void) | null = null;

/**
 * Formats a byte count into a clean B / KB / MB string.
 */
export function formatByteSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

/**
 * Measures the real `localStorage` usage across all `nexus_*` keys in bytes.
 */
export function measureLocalStorageFootprint(): LocalStorageFootprint {
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return { bytes: 0, keyCount: 0, formatted: '0 B' };
    }

    const storage = window.localStorage;
    const seenKeys = new Set<string>();
    let totalBytes = 0;

    if (typeof storage.length === 'number' && typeof storage.key === 'function') {
      for (let i = 0; i < storage.length; i++) {
        const key = storage.key(i);
        if (key && key.startsWith('nexus_')) {
          seenKeys.add(key);
        }
      }
    } else {
      for (const key of KNOWN_NEXUS_STORAGE_KEYS) {
        if (storage.getItem(key) !== null) {
          seenKeys.add(key);
        }
      }
    }

    for (const key of seenKeys) {
      const value = storage.getItem(key);
      if (value !== null) {
        // UTF-16 string storage in WebView2 localStorage uses 2 bytes per code unit.
        totalBytes += (key.length + value.length) * 2;
      }
    }

    return {
      bytes: totalBytes,
      keyCount: seenKeys.size,
      formatted: formatByteSize(totalBytes),
    };
  } catch {
    return { bytes: 0, keyCount: 0, formatted: '0 B' };
  }
}

/**
 * Renders the Transport, Security & Local Storage group for the About page.
 */
export function renderTransportStorageGroup(): HTMLElement {
  if (cleanupTransportSubscriptions) {
    cleanupTransportSubscriptions();
    cleanupTransportSubscriptions = null;
  }

  const footprint = measureLocalStorageFootprint();
  const isAuthed = authManager.isAuthenticated();

  const grid = document.createElement('div');
  grid.className = 'settings-build-grid';
  grid.innerHTML = `
    <div class="settings-build-tile">
      <span class="settings-build-label">P2P Encryption</span>
      <span class="settings-build-val">DTLS-SRTP / SCTP</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">ICE Discovery</span>
      <span class="settings-build-val">Non-Trickle (${STUN_SERVERS.length} STUN)</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">Signaling Auth</span>
      <span id="about-auth-val" class="settings-build-val">${escapeHtml(
        `${FIREBASE_CONFIG.projectId} • ${isAuthed ? 'Signed In' : 'Standby'}`
      )}</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">Local Storage</span>
      <span id="about-storage-val" class="settings-build-val">${escapeHtml(
        `${footprint.formatted} (${footprint.keyCount} keys)`
      )}</span>
    </div>
  `;

  const sessionRow = document.createElement('div');
  sessionRow.className = 'settings-row';
  sessionRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Active Parent Session & Remembered Device</span>
      <span id="about-session-desc" class="settings-label-desc"></span>
    </div>
    <span id="about-session-badge" class="settings-val-badge">Standby</span>
  `;

  let disposed = false;
  let wasMounted = false;

  const updateLiveMetrics = (): void => {
    const latestFootprint = measureLocalStorageFootprint();
    const storageEl = grid.querySelector<HTMLElement>('#about-storage-val');
    if (storageEl) {
      storageEl.textContent = `${latestFootprint.formatted} (${latestFootprint.keyCount} keys)`;
    }

    const authEl = grid.querySelector<HTMLElement>('#about-auth-val');
    if (authEl) {
      authEl.textContent = `${FIREBASE_CONFIG.projectId} • ${
        authManager.isAuthenticated() ? 'Signed In' : 'Standby'
      }`;
    }

    const sessionDesc = sessionRow.querySelector<HTMLElement>('#about-session-desc');
    const sessionBadge = sessionRow.querySelector<HTMLElement>('#about-session-badge');
    const connected = deviceState.getConnectedDevice();
    const lastDevice = deviceState.getLastConnectedDevice();

    if (connected && deviceState.isConnected()) {
      if (sessionBadge) sessionBadge.textContent = 'Connected';
      if (sessionDesc) {
        sessionDesc.textContent = `Live P2P link with ${connected.name} (${connected.id})`;
      }
    } else if (deviceState.isReconnecting()) {
      if (sessionBadge) sessionBadge.textContent = 'Reconnecting';
      if (sessionDesc) {
        sessionDesc.textContent = lastDevice
          ? `Attempting recovery with ${lastDevice.name || lastDevice.formattedId}`
          : 'Attempting WebRTC peer recovery';
      }
    } else {
      if (sessionBadge) sessionBadge.textContent = 'Standby';
      if (sessionDesc) {
        sessionDesc.textContent = lastDevice
          ? `Last paired device: ${lastDevice.name || 'Android Device'} (${lastDevice.formattedId})`
          : 'No child device currently linked';
      }
    }
  };

  // Populate initial values synchronously before the node is mounted into the DOM
  updateLiveMetrics();

  let unsubDevice: () => void = () => {};
  let unsubSettings: () => void = () => {};

  const onWorkspaceReset = () => syncLiveMetrics();

  const dispose = (): void => {
    if (disposed) return;
    disposed = true;
    unsubDevice();
    unsubSettings();
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener('nexus-workspace-reset', onWorkspaceReset);
    }
  };

  function syncLiveMetrics(): void {
    if (grid.isConnected === true) {
      wasMounted = true;
    } else if (wasMounted && grid.isConnected === false) {
      dispose();
      return;
    }
    if (disposed) return;
    updateLiveMetrics();
  }

  unsubDevice = deviceState.subscribe(syncLiveMetrics);
  unsubSettings = settingsState.onValuesChange(syncLiveMetrics);

  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('nexus-workspace-reset', onWorkspaceReset);
  }

  cleanupTransportSubscriptions = dispose;

  return createSettingsGroup('TRANSPORT, SECURITY & SESSION TELEMETRY', [grid, sessionRow]);
}
