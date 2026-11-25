/**
 * @file voice-preset.ts
 * @description Default Intercom Voice preset selector setting.
 *
 * Hey friend! Lets the parent choose which DSP voice preset is active and
 * stays bidirectionally synchronized with the floating Microphone panel dropdown.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  VOICE_PRESETS,
  VoicePresetId,
  getVoicePreset,
} from '../../../main-content/mic/voice-presets.js';
import { showToast } from '../../../shared/toast.js';

let unsubscribeVoiceSync: (() => void) | null = null;

/**
 * Renders the Default Intercom Voice preset dropdown setting row.
 */
export function renderVoicePresetSetting(): HTMLElement {
  if (unsubscribeVoiceSync) {
    unsubscribeVoiceSync();
    unsubscribeVoiceSync = null;
  }

  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentPreset = settingsState.getValue('defaultVoicePreset');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Default Intercom Voice</span>
      <span class="settings-label-desc">Active DSP preset applied for push-to-talk speech (synced with Mic panel)</span>
    </div>
    <select id="setting-voice-preset" class="settings-select">
      ${VOICE_PRESETS.map(
        (p) =>
          `<option value="${p.id}" ${p.id === currentPreset ? 'selected' : ''}>${p.name} - ${p.description}</option>`
      ).join('')}
    </select>
  `;

  const voiceSelect = row.querySelector<HTMLSelectElement>('#setting-voice-preset');
  voiceSelect?.addEventListener('change', () => {
    const selectedId = voiceSelect.value as VoicePresetId;
    settingsState.setValue('defaultVoicePreset', selectedId);
    const preset = getVoicePreset(selectedId);
    showToast(`Intercom voice set to ${preset.name}`);
  });

  // Reactively sync dropdown if changed from the floating Mic panel or Reset Defaults
  unsubscribeVoiceSync = settingsState.onValuesChange((values) => {
    if (row.isConnected === false) {
      unsubscribeVoiceSync?.();
      unsubscribeVoiceSync = null;
      return;
    }
    if (voiceSelect && voiceSelect.value !== values.defaultVoicePreset) {
      voiceSelect.value = values.defaultVoicePreset;
    }
  });

  return row;
}
