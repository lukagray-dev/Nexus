/**
 * @file keep-screen-awake.ts
 * @description Keep Screen On When Connected setting and runtime wake-lock synchronizer.
 *
 * Hey friend! Even when the parent is not actively streaming camera, screen, or mic,
 * keeping the parent workstation awake while connected ensures the P2P WebRTC bridge
 * and background monitoring telemetry stay uninterrupted.
 *
 * Whenever `keepScreenAwake === true` AND (`deviceState.isConnected()` OR
 * `deviceState.isReconnecting()`), Rust activates an OS display/idle wake lock (`keepawake`).
 * As soon as the device disconnects or the user disables this toggle, the wake lock releases!
 */

import { settingsState } from '../../sidebar/state.js';
import { deviceState } from '../../../shared/device-state.js';
import { setKeepScreenAwake } from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';

let lastAppliedWakeLock: boolean | null = null;

/**
 * Pure decision function determining whether the OS screen wake lock should be held.
 */
export function computeShouldKeepScreenAwake(
  settingEnabled: boolean,
  isConnected: boolean,
  isReconnecting: boolean
): boolean {
  return settingEnabled && (isConnected || isReconnecting);
}

/**
 * Synchronizes the OS wake lock with the current setting and device connection state.
 */
export async function syncKeepScreenAwakeState(): Promise<boolean> {
  const shouldKeepAwake = computeShouldKeepScreenAwake(
    settingsState.getValue('keepScreenAwake'),
    deviceState.isConnected(),
    deviceState.isReconnecting()
  );

  if (lastAppliedWakeLock === shouldKeepAwake) {
    return shouldKeepAwake;
  }

  lastAppliedWakeLock = shouldKeepAwake;
  await setKeepScreenAwake(shouldKeepAwake);
  return shouldKeepAwake;
}

/**
 * Resets internal wake-lock memoization (primarily for clean test isolation).
 */
export function resetKeepScreenAwakeSyncCache(): void {
  lastAppliedWakeLock = null;
}

/**
 * Initializes reactive subscriptions so wake lock updates immediately on connect,
 * disconnect, reconnect, or setting toggle changes.
 */
export function initKeepScreenAwakeRuntime(): () => void {
  void syncKeepScreenAwakeState();

  const unsubDevice = deviceState.subscribe(() => {
    void syncKeepScreenAwakeState();
  });

  const unsubSettings = settingsState.onValuesChange(() => {
    void syncKeepScreenAwakeState();
  });

  return () => {
    unsubDevice();
    unsubSettings();
  };
}

/**
 * Renders the "Keep Screen On When Connected" toggle setting row.
 */
export function renderKeepScreenAwakeSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const isEnabled = settingsState.getValue('keepScreenAwake');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Keep Screen On When Connected</span>
      <span class="settings-label-desc">Prevents your PC display and system from sleeping while connected to a child device</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-keep-screen-awake" ${isEnabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const toggle = row.querySelector<HTMLInputElement>('#setting-keep-screen-awake');
  toggle?.addEventListener('change', () => {
    const nextValue = toggle.checked;
    settingsState.setValue('keepScreenAwake', nextValue);
    void syncKeepScreenAwakeState();
    showToast(
      nextValue
        ? 'Display will stay awake while connected to a child device'
        : 'Normal PC sleep behavior restored'
    );
  });

  return row;
}
