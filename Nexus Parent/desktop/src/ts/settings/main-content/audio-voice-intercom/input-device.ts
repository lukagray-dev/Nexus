/**
 * @file input-device.ts
 * @description Real-time Microphone Input Device enumeration & selection setting.
 *
 * Hey friend! Lists the PC's actual audio input devices from `navigator.mediaDevices`:
 * 1. Enumerates silently on open without popping an unexpected OS mic prompt.
 * 2. If WebView2 masks hardware labels until permission is granted, displays a
 *    "Detect Devices" button so the parent can unlock device names on demand.
 * 3. Updates live when microphones or headsets are plugged in or disconnected.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  enumerateAudioDevices,
  requestAudioDevicePermission,
  subscribeAudioDeviceChanges,
} from '../../../main-content/mic/audio-device-service.js';
import { escapeHtml } from '../helpers.js';
import { showToast } from '../../../shared/toast.js';

let cleanupInputSubscriptions: (() => void) | null = null;

/**
 * Renders the Microphone Input Device dropdown setting row.
 */
export function renderInputDeviceSetting(): HTMLElement {
  if (cleanupInputSubscriptions) {
    cleanupInputSubscriptions();
    cleanupInputSubscriptions = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentDevice = settingsState.getValue('inputDeviceId');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Microphone Input Device</span>
      <span class="settings-label-desc">PC microphone used for parent speech broadcast</span>
      <span id="setting-input-device-hint" class="settings-label-hint" style="display: none;"></span>
    </div>
    <div class="settings-device-select-group">
      <select id="setting-input-device" class="settings-select">
        <option value="default" ${currentDevice === 'default' ? 'selected' : ''}>System Default Input</option>
        ${
          currentDevice !== 'default'
            ? `<option value="${escapeHtml(currentDevice)}" selected>Saved Microphone</option>`
            : ''
        }
      </select>
      <button
        type="button"
        id="btn-detect-input-devices"
        class="settings-btn-inline"
        style="display: none;"
        title="Grant temporary mic access to reveal hardware microphone names"
      >
        Detect Devices
      </button>
    </div>
  `;

  const inputSelect = row.querySelector<HTMLSelectElement>('#setting-input-device');
  const detectBtn = row.querySelector<HTMLButtonElement>('#btn-detect-input-devices');
  const hintEl = row.querySelector<HTMLElement>('#setting-input-device-hint');

  let isDisposed = false;
  let refreshInFlight: Promise<void> | null = null;

  const populateDevices = async (): Promise<void> => {
    if (isDisposed || !inputSelect) return;

    const snapshot = await enumerateAudioDevices();
    if (isDisposed || !inputSelect) return;

    const selectedId = settingsState.getValue('inputDeviceId') || 'default';
    const hasMatch =
      selectedId === 'default' || snapshot.inputs.some((d) => d.deviceId === selectedId);

    const optionsHtml = [
      `<option value="default"${selectedId === 'default' ? ' selected' : ''}>System Default Input</option>`,
      ...snapshot.inputs.map(
        (d) =>
          `<option value="${escapeHtml(d.deviceId)}"${
            d.deviceId === selectedId ? ' selected' : ''
          }>${escapeHtml(d.label)}</option>`
      ),
    ];

    if (!hasMatch) {
      optionsHtml.push(
        `<option value="${escapeHtml(selectedId)}" selected>Unavailable Saved Microphone</option>`
      );
      if (hintEl) {
        hintEl.textContent =
          'Saved microphone is disconnected; System Default will be used until reconnected.';
        hintEl.style.display = 'block';
      }
    } else if (hintEl) {
      hintEl.textContent = '';
      hintEl.style.display = 'none';
    }

    inputSelect.innerHTML = optionsHtml.join('');
    inputSelect.value = selectedId;

    if (detectBtn) {
      detectBtn.style.display = snapshot.supported && snapshot.hasMaskedLabels ? 'inline-flex' : 'none';
    }
  };

  const triggerRefresh = (): Promise<void> => {
    refreshInFlight = populateDevices().catch((err) => {
      console.warn('[AudioSettings:InputDevice] Failed to refresh microphones:', err);
    });
    return refreshInFlight;
  };

  inputSelect?.addEventListener('change', () => {
    const nextId = inputSelect.value || 'default';
    settingsState.setValue('inputDeviceId', nextId);
    const label =
      inputSelect.options?.[inputSelect.selectedIndex]?.textContent?.trim() || 'Selected input';
    showToast(`Microphone input set to ${label}`);
  });

  detectBtn?.addEventListener('click', async () => {
    detectBtn.disabled = true;
    const granted = await requestAudioDevicePermission();
    detectBtn.disabled = false;

    if (granted) {
      await triggerRefresh();
      showToast('Audio hardware devices detected');
    } else {
      showToast('Microphone permission is required to read hardware device names');
    }
  });

  const unsubscribeChanges = subscribeAudioDeviceChanges(() => {
    if (row.isConnected === false) {
      dispose();
      return;
    }
    void triggerRefresh();
  });

  const unsubscribeValues = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      dispose();
      return;
    }
    if (inputSelect && inputSelect.value !== values.inputDeviceId) {
      void triggerRefresh();
    }
  });

  const dispose = () => {
    if (isDisposed) return;
    isDisposed = true;
    unsubscribeChanges();
    unsubscribeValues();
  };

  cleanupInputSubscriptions = dispose;
  void triggerRefresh();

  return row;
}
