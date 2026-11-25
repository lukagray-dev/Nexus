/**
 * @file voice-preset.ts
 * @description Default Intercom Voice preset selector setting.
 *
 * Hey friend! Lets the parent choose which DSP voice preset is pre-selected
 * whenever push-to-talk speech is activated.
 */

import { settingsState } from '../../sidebar/state.js';
import { VOICE_PRESETS, VoicePresetId } from '../../../main-content/mic/voice-presets.js';

/**
 * Renders the Default Intercom Voice preset dropdown setting row.
 */
export function renderVoicePresetSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentPreset = settingsState.getValue('defaultVoicePreset');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Default Intercom Voice</span>
      <span class="settings-label-desc">Initial DSP preset applied when activating push-to-talk speech</span>
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
    settingsState.setValue('defaultVoicePreset', voiceSelect.value as VoicePresetId);
  });

  return row;
}
