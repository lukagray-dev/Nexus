/**
 * @file hardware-acceleration.ts
 * @description GPU Video Hardware Acceleration toggle setting.
 *
 * Hey friend! Controls whether WebView2 launches with native GPU decoding enabled
 * (via Rust `WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS` on boot) and prioritizes GPU-friendly
 * video codecs on WebRTC connections. Since WebView2 GPU flags apply at process startup,
 * toggling away from the launch state displays an inline Restart Required notice.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  restartApplication,
  setHardwareAccelerationSetting,
} from '../../../shared/ipc.js';
import { deviceState } from '../../../shared/device-state.js';
import { showToast } from '../../../shared/toast.js';

let lastSyncedHardwareAccel: boolean | null = null;
let launchHardwareAccel: boolean | null = null;
let unsubscribeHwAccelSync: (() => void) | null = null;

/**
 * Records the value confirmed by Rust so reactive listeners avoid duplicate IPC calls.
 */
export function markHardwareAccelerationSynced(enabled: boolean): void {
  lastSyncedHardwareAccel = enabled;
}

/**
 * Records the GPU acceleration mode active when the desktop process started.
 */
export function setLaunchHardwareAcceleration(enabled: boolean): void {
  launchHardwareAccel = enabled;
}

/**
 * Returns true if the persisted setting differs from the currently running WebView2 GPU state.
 */
export function isHardwareAccelerationRestartPending(currentValue: boolean): boolean {
  return launchHardwareAccel !== null && launchHardwareAccel !== currentValue;
}

/**
 * Persists `hardwareAcceleration` in the Rust native backend.
 */
export async function applyHardwareAccelerationSetting(enabled: boolean): Promise<boolean> {
  try {
    const snapshot = await setHardwareAccelerationSetting(enabled);
    if (snapshot) {
      lastSyncedHardwareAccel = enabled;
      if (typeof snapshot.launchHardwareAcceleration === 'boolean') {
        launchHardwareAccel = snapshot.launchHardwareAcceleration;
      }
    } else {
      lastSyncedHardwareAccel = enabled;
    }
    return true;
  } catch (err) {
    console.error('[Surveillance:HardwareAccel] Failed to persist GPU setting:', err);
    return false;
  }
}

/**
 * Syncs `hardwareAcceleration` to Rust only when it differs from the last confirmed value.
 */
export async function syncHardwareAccelerationIfChanged(enabled: boolean): Promise<boolean> {
  if (lastSyncedHardwareAccel === enabled) {
    return true;
  }
  return await applyHardwareAccelerationSetting(enabled);
}

/**
 * Renders the GPU Video Hardware Acceleration toggle setting row.
 */
export function renderHardwareAccelerationSetting(): HTMLElement {
  if (unsubscribeHwAccelSync) {
    unsubscribeHwAccelSync();
    unsubscribeHwAccelSync = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const enabled = settingsState.getValue('hardwareAcceleration');
  const restartPending = isHardwareAccelerationRestartPending(enabled);

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">GPU Video Hardware Acceleration</span>
      <span class="settings-label-desc">Uses native GPU decoding inside WebView2 for smooth zero-lag playback</span>
      <span id="setting-hw-accel-hint" class="settings-label-hint" style="display: ${
        restartPending ? 'block' : 'none'
      };">Restart required for WebView2 GPU process change to take effect</span>
    </div>
    <div class="settings-device-select-group">
      <button
        type="button"
        id="btn-restart-hw-accel"
        class="settings-btn-inline"
        style="display: ${restartPending ? 'inline-flex' : 'none'};"
        title="Restart Nexus Parent to apply GPU acceleration change"
      >
        Restart Now
      </button>
      <label class="settings-toggle">
        <input type="checkbox" id="setting-hardware-acceleration" ${enabled ? 'checked' : ''} />
        <span class="settings-toggle-slider"></span>
      </label>
    </div>
  `;

  const hwToggle = row.querySelector<HTMLInputElement>('#setting-hardware-acceleration');
  const hintEl = row.querySelector<HTMLElement>('#setting-hw-accel-hint');
  const restartBtn = row.querySelector<HTMLButtonElement>('#btn-restart-hw-accel');

  const updateRestartUi = (currentEnabled: boolean): void => {
    const showRestart = isHardwareAccelerationRestartPending(currentEnabled);
    if (hintEl) {
      hintEl.style.display = showRestart ? 'block' : 'none';
    }
    if (restartBtn) {
      restartBtn.style.display = showRestart ? 'inline-flex' : 'none';
    }
  };

  hwToggle?.addEventListener('change', async () => {
    const nextValue = hwToggle.checked;
    hwToggle.disabled = true;

    const ok = await applyHardwareAccelerationSetting(nextValue);
    hwToggle.disabled = false;

    if (ok) {
      settingsState.setValue('hardwareAcceleration', nextValue);
      updateRestartUi(nextValue);
      showToast(
        isHardwareAccelerationRestartPending(nextValue)
          ? `GPU hardware acceleration ${nextValue ? 'enabled' : 'disabled'} — restart app to apply to WebView2`
          : `GPU hardware acceleration ${nextValue ? 'enabled' : 'disabled'}`
      );
    } else {
      hwToggle.checked = !nextValue;
      showToast('Failed to save GPU hardware acceleration setting');
    }
  });

  restartBtn?.addEventListener('click', async () => {
    restartBtn.disabled = true;
    deviceState.disconnect();
    await restartApplication();
  });

  unsubscribeHwAccelSync = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      unsubscribeHwAccelSync?.();
      unsubscribeHwAccelSync = null;
      return;
    }
    if (hwToggle && hwToggle.checked !== values.hardwareAcceleration) {
      hwToggle.checked = values.hardwareAcceleration;
    }
    updateRestartUi(values.hardwareAcceleration);
  });

  return row;
}
