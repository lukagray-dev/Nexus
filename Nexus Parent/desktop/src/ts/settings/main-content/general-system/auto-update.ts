/**
 * @file auto-update.ts
 * @description Check for Updates Automatically setting and background GitHub release check scheduler.
 *
 * Hey friend! Controls whether Nexus Parent silently checks GitHub stable releases
 * (`lukagray-dev/Nexus`, `v*.*.*`) on startup and every 6 hours in the background:
 * - When a newer stable release is found, Rust downloads it in the background and surfaces
 *   a "Restart to update" button in the titlebar (or applies it silently after app exit).
 * - When already up to date, no update button is shown in the titlebar.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  UpdateStatusPayload,
  checkForUpdates,
  getUpdateStatus,
  isTauriRuntime,
  listenTauriEvent,
  restartToUpdate,
} from '../../../shared/ipc.js';
import { deviceState } from '../../../shared/device-state.js';
import { showToast } from '../../../shared/toast.js';

export const EVENT_UPDATE_STATUS = 'app://update-status';

const STARTUP_CHECK_DELAY_MS = 4000;
const PERIODIC_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

let unsubscribeStatusModalSync: (() => void) | null = null;

/**
 * Formats an `UpdateStatusPayload` into a concise monochrome status badge label.
 */
export function formatUpdateStatusBadge(status: UpdateStatusPayload | null): string {
  if (!status) return 'Ready';
  switch (status.status) {
    case 'checking':
      return 'Checking...';
    case 'downloading':
      return 'Downloading...';
    case 'ready':
      return status.availableVersion ? `v${status.availableVersion} ready` : 'Update ready';
    case 'up-to-date':
      return `v${status.currentVersion} (Latest)`;
    case 'error':
      return 'Check failed';
    default:
      return `v${status.currentVersion}`;
  }
}

/**
 * Triggers a manual update check with user-facing toast feedback.
 */
export async function runManualUpdateCheck(): Promise<UpdateStatusPayload | null> {
  if (!isTauriRuntime()) {
    showToast('Update checks require the Nexus Parent desktop application');
    return null;
  }

  showToast('Checking GitHub for stable Nexus releases...');
  try {
    const result = await checkForUpdates(true);
    if (!result) {
      return null;
    }

    if (result.status === 'ready' && result.availableVersion) {
      showToast(`Update v${result.availableVersion} downloaded — click "Restart to update"`);
    } else if (result.status === 'up-to-date') {
      showToast(`You are running the latest stable version (v${result.currentVersion})`);
    } else if (result.status === 'error' && result.errorMessage) {
      showToast(`Update check failed: ${result.errorMessage}`);
    }
    return result;
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Could not check for updates';
    showToast(`Update check failed: ${msg}`);
    return null;
  }
}

/**
 * Starts the startup + periodic background GitHub stable release check scheduler.
 */
export function initAutoUpdateRuntime(): () => void {
  let startupTimer: ReturnType<typeof setTimeout> | null = null;
  let periodicTimer: ReturnType<typeof setInterval> | null = null;

  const triggerBackgroundCheck = (): void => {
    if (!isTauriRuntime()) return;
    if (!settingsState.getValue('autoCheckUpdates')) return;
    void checkForUpdates(false).catch((err) => {
      console.warn('[GeneralSystem:AutoUpdate] Background update check failed:', err);
    });
  };

  if (isTauriRuntime() && settingsState.getValue('autoCheckUpdates')) {
    startupTimer = setTimeout(triggerBackgroundCheck, STARTUP_CHECK_DELAY_MS);
    periodicTimer = setInterval(triggerBackgroundCheck, PERIODIC_CHECK_INTERVAL_MS);
  }

  const unsubSettings = settingsState.onValuesChange((values) => {
    if (!isTauriRuntime()) return;

    if (values.autoCheckUpdates && !periodicTimer) {
      periodicTimer = setInterval(triggerBackgroundCheck, PERIODIC_CHECK_INTERVAL_MS);
    } else if (!values.autoCheckUpdates && periodicTimer) {
      clearInterval(periodicTimer);
      periodicTimer = null;
    }
  });

  return () => {
    if (startupTimer) {
      clearTimeout(startupTimer);
      startupTimer = null;
    }
    if (periodicTimer) {
      clearInterval(periodicTimer);
      periodicTimer = null;
    }
    unsubSettings();
  };
}

/**
 * Renders the "Check for Updates Automatically" toggle and manual Check Now / Restart action row.
 */
export function renderAutoUpdateSetting(): HTMLElement[] {
  if (unsubscribeStatusModalSync) {
    unsubscribeStatusModalSync();
    unsubscribeStatusModalSync = null;
  }

  const toggleRow = document.createElement('div');
  toggleRow.className = 'settings-row';

  const isEnabled = settingsState.getValue('autoCheckUpdates');

  toggleRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Check for Updates Automatically</span>
      <span class="settings-label-desc">Checks GitHub stable releases in the background and stages new updates automatically</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-auto-check-updates" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = toggleRow.querySelector<HTMLInputElement>('#setting-auto-check-updates');
  toggle?.addEventListener('change', () => {
    const nextValue = toggle.checked;
    settingsState.setValue('autoCheckUpdates', nextValue);
    showToast(
      nextValue
        ? 'Automatic GitHub stable release checks enabled'
        : 'Automatic update checks disabled'
    );
  });

  const actionRow = document.createElement('div');
  actionRow.className = 'settings-row';
  actionRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Release Channel & Status</span>
      <span class="settings-label-desc" id="setting-update-status-desc">Stable releases from GitHub (lukagray-dev/Nexus)</span>
    </div>
    <div class="settings-file-picker-group">
      <span id="setting-update-status-badge" class="settings-val-badge">Checking...</span>
      <button type="button" id="btn-check-updates-now" class="settings-btn-inline">Check Now</button>
    </div>
  `;

  const badgeEl = actionRow.querySelector<HTMLElement>('#setting-update-status-badge');
  const checkBtn = actionRow.querySelector<HTMLButtonElement>('#btn-check-updates-now');

  const applyPayload = (payload: UpdateStatusPayload | null): void => {
    if (badgeEl) {
      badgeEl.textContent = formatUpdateStatusBadge(payload);
    }
    if (checkBtn) {
      const busy = payload?.status === 'checking' || payload?.status === 'downloading';
      checkBtn.disabled = busy;
      checkBtn.textContent = payload?.status === 'ready' ? 'Restart to update' : 'Check Now';
    }
  };

  if (isTauriRuntime()) {
    void getUpdateStatus().then(applyPayload);
  } else {
    applyPayload({
      status: 'idle',
      currentVersion: '0.1.0-alpha',
      availableVersion: null,
      releaseUrl: null,
      sha256Verified: false,
      errorMessage: null,
    });
  }

  let disposed = false;
  void listenTauriEvent<UpdateStatusPayload>(EVENT_UPDATE_STATUS, (payload) => {
    if (!disposed) {
      applyPayload(payload);
    }
  }).then((unlisten) => {
    if (disposed) {
      unlisten();
    } else {
      unsubscribeStatusModalSync = () => {
        disposed = true;
        unlisten();
      };
    }
  });

  checkBtn?.addEventListener('click', async () => {
    const current = isTauriRuntime() ? await getUpdateStatus() : null;
    if (current?.status === 'ready') {
      deviceState.disconnect();
      await restartToUpdate();
      return;
    }

    if (checkBtn) checkBtn.disabled = true;
    const updated = await runManualUpdateCheck();
    applyPayload(updated);
  });

  return [toggleRow, actionRow];
}
