/**
 * @file parent-identity.ts
 * @description Parent application identity hero card, live update status, and system summary copy.
 *
 * Hey friend! Renders the top brand card on the About page with:
 * - Official `nexus-parent.svg` logo and bundle identifier (`com.nexus.parent`).
 * - Live version badge queried from Rust (`getAppVersion()`).
 * - Live GitHub release updater status with `Check Now` / `Restart to update`.
 * - One-click `Copy Info` button generating a plain-text diagnostics summary.
 */

import {
  ParentSystemInfo,
  UpdateStatusPayload,
  checkForUpdates,
  getAppVersion,
  getParentSystemInfo,
  getUpdateStatus,
  isTauriRuntime,
  listenTauriEvent,
  restartToUpdate,
} from '../../../shared/ipc.js';
import { deviceState } from '../../../shared/device-state.js';
import { showToast } from '../../../shared/toast.js';
import { settingsState } from '../../sidebar/state.js';
import {
  EVENT_UPDATE_STATUS,
  formatUpdateStatusBadge,
  runManualUpdateCheck,
} from '../general-system/auto-update.js';
import { createSettingsGroup, escapeHtml } from '../helpers.js';

let cleanupIdentitySubscriptions: (() => void) | null = null;

/**
 * Formats a plain-text diagnostics summary of the Parent desktop app for issue reports.
 */
export function buildParentDiagnosticsSummary(
  info: ParentSystemInfo | null,
  status: UpdateStatusPayload | null
): string {
  const version = info?.appVersion || status?.currentVersion || '0.1.0-alpha';
  const osLine = info ? `${info.os} (${info.arch})` : 'WebView / Browser';
  const installLine = info?.installKind || status?.installKind || 'portable';
  const tauriLine = info ? `Tauri v${info.tauriVersion}` : 'Non-Tauri Preview';
  const webviewLine = info?.webviewVersion || (typeof navigator !== 'undefined' ? navigator.userAgent : 'Unknown');
  const coresLine = info ? String(info.cpuCores) : String(typeof navigator !== 'undefined' ? navigator.hardwareConcurrency || 1 : 1);
  const connected = deviceState.getConnectedDevice();
  const peerLine = connected
    ? `Connected (${connected.name} / ${connected.id})`
    : deviceState.isReconnecting()
    ? 'Reconnecting'
    : 'Standby';

  return [
    `Application: ${info?.appName || 'Nexus Parent'} (${info?.identifier || 'com.nexus.parent'})`,
    `Version: v${version}`,
    `Update State: ${formatUpdateStatusBadge(status)}`,
    `OS / Arch: ${osLine}`,
    `Package Mode: ${installLine}`,
    `Runtime: ${tauriLine}`,
    `WebView: ${webviewLine}`,
    `CPU Cores: ${coresLine}`,
    `Hardware Acceleration: ${settingsState.getValue('hardwareAcceleration') ? 'Enabled' : 'Disabled'}`,
    `Active Peer: ${peerLine}`,
    `Config Dir: ${info?.configDir || 'Unavailable'}`,
    `Local Data Dir: ${info?.localDataDir || 'Unavailable'}`,
  ].join('\n');
}

/**
 * Renders the Parent Identity & Release Status group for the About page.
 */
export function renderParentIdentityGroup(): HTMLElement {
  if (cleanupIdentitySubscriptions) {
    cleanupIdentitySubscriptions();
    cleanupIdentitySubscriptions = null;
  }

  const card = document.createElement('div');
  card.className = 'settings-about-hero';

  card.innerHTML = `
    <div class="settings-about-hero-main">
      <div class="settings-about-logo-box">
        <img src="assets/logo/nexus-parent.svg" alt="Nexus Parent Logo" class="settings-about-logo-img" />
      </div>
      <div class="settings-about-meta">
        <div class="settings-about-title-row">
          <span class="settings-about-name">Nexus Parent</span>
          <span id="about-tab-version-badge" class="settings-val-badge">v0.1.0-alpha</span>
          <span id="about-tab-update-badge" class="settings-about-status-pill">Ready</span>
        </div>
        <span class="settings-about-id">com.nexus.parent • Zero-Cloud P2P Parental Console</span>
        <span class="settings-label-desc">
          Encrypted direct WebRTC device surveillance, live telemetry, and remote parental controls.
        </span>
      </div>
    </div>
    <div class="settings-about-actions">
      <button type="button" id="btn-about-copy-summary" class="settings-btn settings-btn-secondary">
        Copy System Info
      </button>
      <button type="button" id="btn-about-check-updates" class="settings-btn settings-btn-primary">
        Check Updates
      </button>
    </div>
  `;

  const versionBadge = card.querySelector<HTMLElement>('#about-tab-version-badge');
  const updateBadge = card.querySelector<HTMLElement>('#about-tab-update-badge');
  const copyBtn = card.querySelector<HTMLButtonElement>('#btn-about-copy-summary');
  const updateBtn = card.querySelector<HTMLButtonElement>('#btn-about-check-updates');

  let latestInfo: ParentSystemInfo | null = null;
  let latestStatus: UpdateStatusPayload | null = null;
  let disposed = false;

  const applyStatus = (payload: UpdateStatusPayload | null): void => {
    if (disposed) return;
    latestStatus = payload;

    if (versionBadge && payload?.currentVersion) {
      versionBadge.textContent = `v${escapeHtml(payload.currentVersion)}`;
    }
    if (updateBadge) {
      updateBadge.textContent = formatUpdateStatusBadge(payload);
      updateBadge.classList.toggle('is-ready', payload?.status === 'ready');
    }
    if (updateBtn) {
      const busy = payload?.status === 'checking' || payload?.status === 'downloading';
      updateBtn.disabled = busy;
      updateBtn.textContent = payload?.status === 'ready' ? 'Restart to update' : 'Check Updates';
    }
  };

  if (isTauriRuntime()) {
    void getAppVersion().then((ver) => {
      if (!disposed && ver && versionBadge) {
        versionBadge.textContent = `v${ver}`;
      }
    });
    void getParentSystemInfo().then((info) => {
      if (!disposed) {
        latestInfo = info;
      }
    });
    void getUpdateStatus().then(applyStatus);

    let unlistenStatus: (() => void) | null = null;
    let wasMounted = false;

    const dispose = (): void => {
      if (disposed) return;
      disposed = true;
      if (unlistenStatus) {
        unlistenStatus();
        unlistenStatus = null;
      }
    };
    cleanupIdentitySubscriptions = dispose;

    void listenTauriEvent<UpdateStatusPayload>(EVENT_UPDATE_STATUS, (payload) => {
      if (card.isConnected === true) {
        wasMounted = true;
      } else if (wasMounted && card.isConnected === false) {
        dispose();
        return;
      }
      applyStatus(payload);
    }).then((unlisten) => {
      if (disposed) {
        unlisten();
      } else {
        unlistenStatus = unlisten;
      }
    });
  } else {
    applyStatus({
      status: 'idle',
      currentVersion: '0.1.0-alpha',
      availableVersion: null,
      releaseUrl: null,
      sha256Verified: false,
      errorMessage: null,
    });
  }

  copyBtn?.addEventListener('click', async () => {
    const info = latestInfo ?? (isTauriRuntime() ? await getParentSystemInfo() : null);
    const summary = buildParentDiagnosticsSummary(info, latestStatus);
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(summary);
        showToast('Parent system info copied to clipboard');
      } else {
        showToast('Clipboard unavailable in this environment');
      }
    } catch (err) {
      console.warn('[About:Identity] Failed to copy diagnostics summary:', err);
      showToast('Failed to copy system info to clipboard');
    }
  });

  updateBtn?.addEventListener('click', async () => {
    if (latestStatus?.status === 'ready') {
      deviceState.disconnect();
      await restartToUpdate();
      return;
    }

    if (updateBtn) updateBtn.disabled = true;
    if (isTauriRuntime()) {
      const updated = await runManualUpdateCheck();
      applyStatus(updated);
    } else {
      const result = await checkForUpdates(true);
      applyStatus(result);
      showToast('Update checks require the Nexus Parent desktop application');
    }
  });

  return createSettingsGroup('APPLICATION IDENTITY & RELEASES', [card]);
}
