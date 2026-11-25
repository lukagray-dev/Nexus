/**
 * @file output-device.ts
 * @description Real-time Monitoring Output Speaker enumeration & selection setting.
 *
 * Hey friend! Selects which local speakers or headphones play incoming audio
 * from the child's microphone and screen system audio via `setSinkId()`.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  enumerateAudioDevices,
  subscribeAudioDeviceChanges,
} from '../../../main-content/mic/audio-device-service.js';
import { escapeHtml } from '../helpers.js';
import { showToast } from '../../../shared/toast.js';

let cleanupOutputSubscriptions: (() => void) | null = null;

/**
 * Renders the Monitoring Output Speaker dropdown setting row.
 */
export function renderOutputDeviceSetting(): HTMLElement {
  if (cleanupOutputSubscriptions) {
    cleanupOutputSubscriptions();
    cleanupOutputSubscriptions = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentDevice = settingsState.getValue('outputDeviceId');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Monitoring Output Speaker</span>
      <span class="settings-label-desc">Speakers or headphones used to listen to the child's microphone and screen audio</span>
      <span id="setting-output-device-hint" class="settings-label-hint" style="display: none;"></span>
    </div>
    <select id="setting-output-device" class="settings-select">
      <option value="default" ${currentDevice === 'default' ? 'selected' : ''}>System Default Output</option>
      ${
        currentDevice !== 'default'
          ? `<option value="${escapeHtml(currentDevice)}" selected>Saved Output Device</option>`
          : ''
      }
    </select>
  `;

  const outputSelect = row.querySelector<HTMLSelectElement>('#setting-output-device');
  const hintEl = row.querySelector<HTMLElement>('#setting-output-device-hint');

  let isDisposed = false;

  const populateOutputs = async (): Promise<void> => {
    if (isDisposed || !outputSelect) return;

    const snapshot = await enumerateAudioDevices();
    if (isDisposed || !outputSelect) return;

    const selectedId = settingsState.getValue('outputDeviceId') || 'default';
    const hasMatch =
      selectedId === 'default' || snapshot.outputs.some((d) => d.deviceId === selectedId);

    const optionsHtml = [
      `<option value="default"${selectedId === 'default' ? ' selected' : ''}>System Default Output</option>`,
      ...snapshot.outputs.map(
        (d) =>
          `<option value="${escapeHtml(d.deviceId)}"${
            d.deviceId === selectedId ? ' selected' : ''
          }>${escapeHtml(d.label)}</option>`
      ),
    ];

    if (!hasMatch) {
      optionsHtml.push(
        `<option value="${escapeHtml(selectedId)}" selected>Unavailable Saved Speaker</option>`
      );
      if (hintEl) {
        hintEl.textContent =
          'Saved speaker is disconnected; System Default Output will be used until reconnected.';
        hintEl.style.display = 'block';
      }
    } else if (hintEl) {
      hintEl.textContent = '';
      hintEl.style.display = 'none';
    }

    outputSelect.innerHTML = optionsHtml.join('');
    outputSelect.value = selectedId;
  };

  const triggerRefresh = (): Promise<void> => {
    return populateOutputs().catch((err) => {
      console.warn('[AudioSettings:OutputDevice] Failed to refresh speakers:', err);
    });
  };

  outputSelect?.addEventListener('change', () => {
    const nextId = outputSelect.value || 'default';
    settingsState.setValue('outputDeviceId', nextId);
    const label =
      outputSelect.options?.[outputSelect.selectedIndex]?.textContent?.trim() || 'Selected output';
    showToast(`Monitoring output set to ${label}`);
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
    if (outputSelect && outputSelect.value !== values.outputDeviceId) {
      void triggerRefresh();
    }
  });

  const dispose = () => {
    if (isDisposed) return;
    isDisposed = true;
    unsubscribeChanges();
    unsubscribeValues();
  };

  cleanupOutputSubscriptions = dispose;
  void triggerRefresh();

  return row;
}
