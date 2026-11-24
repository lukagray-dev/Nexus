/**
 * @file audio-voice-intercom.ts
 * @description Audio & Voice Intercom Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Default Voice Changer preset used when the parent clicks Speak on the intercom.
 * 2. PC hardware audio input device (microphone) and output device (headphones/speakers).
 * 3. Browser-level acoustic echo cancellation and noise suppression toggles.
 * 4. Child phone loudspeaker volume boost override policy.
 */

import { settingsState } from '../sidebar/state.js';
import { VOICE_PRESETS, VoicePresetId } from '../../main-content/mic/voice-presets.js';

export type { VoicePresetId };

export function renderAudioVoiceIntercomContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Audio & Voice Intercom</h2>
      <p class="settings-content-desc">Configure local PC audio routing, DSP voice changer defaults, and child loudspeaker broadcast behavior.</p>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">VOICE CHANGER ENGINE</div>

      <!-- 1. Default Voice Preset -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Default Intercom Voice</span>
          <span class="settings-label-desc">Initial DSP preset applied when activating push-to-talk speech</span>
        </div>
        <select id="setting-voice-preset" class="settings-select">
          ${VOICE_PRESETS.map(
            (p) => `<option value="${p.id}" ${p.id === values.defaultVoicePreset ? 'selected' : ''}>${p.name} - ${p.description}</option>`
          ).join('')}
        </select>
      </div>

      <!-- 2. Intercom Loudspeaker Target Volume -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Loudspeaker Target Volume</span>
          <span class="settings-label-desc">Hardware volume forced on the child device when parent speaks</span>
        </div>
        <div class="settings-segmented-group" id="setting-speaker-volume">
          <button type="button" class="settings-segmented-btn ${values.loudspeakerVolume === 'max' ? 'is-active' : ''}" data-value="max">Max (100%)</button>
          <button type="button" class="settings-segmented-btn ${values.loudspeakerVolume === 'high' ? 'is-active' : ''}" data-value="high">High (85%)</button>
          <button type="button" class="settings-segmented-btn ${values.loudspeakerVolume === 'normal' ? 'is-active' : ''}" data-value="normal">Preserve Level</button>
        </div>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">HARDWARE ROUTING & DSP</div>

      <!-- 3. Microphone Input Device -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Microphone Input Device</span>
          <span class="settings-label-desc">PC microphone used for parent speech broadcast</span>
        </div>
        <select id="setting-input-device" class="settings-select">
          <option value="default" ${values.inputDeviceId === 'default' ? 'selected' : ''}>System Default Input</option>
          <option value="mic-internal" ${values.inputDeviceId === 'mic-internal' ? 'selected' : ''}>Realtek High Definition Audio (Built-in)</option>
          <option value="mic-usb" ${values.inputDeviceId === 'mic-usb' ? 'selected' : ''}>USB Studio Microphone (48kHz)</option>
        </select>
      </div>

      <!-- 4. Monitor Speaker Output Device -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Monitoring Output Speaker</span>
          <span class="settings-label-desc">Speakers or headphones used to listen to the child's environment</span>
        </div>
        <select id="setting-output-device" class="settings-select">
          <option value="default" ${values.outputDeviceId === 'default' ? 'selected' : ''}>System Default Output</option>
          <option value="speakers" ${values.outputDeviceId === 'speakers' ? 'selected' : ''}>Speakers (Realtek Audio)</option>
          <option value="headphones" ${values.outputDeviceId === 'headphones' ? 'selected' : ''}>Headphones / Bluetooth Audio</option>
        </select>
      </div>

      <!-- 5. Noise Suppression & AEC -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Hardware Noise Suppression & AEC</span>
          <span class="settings-label-desc">Acoustic echo cancellation, noise reduction, and auto-gain control</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-noise-suppression" ${values.noiseSuppression ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const voiceSelect = container.querySelector<HTMLSelectElement>('#setting-voice-preset');
  voiceSelect?.addEventListener('change', () => {
    settingsState.setValue('defaultVoicePreset', voiceSelect.value as VoicePresetId);
  });

  const inputSelect = container.querySelector<HTMLSelectElement>('#setting-input-device');
  inputSelect?.addEventListener('change', () => {
    settingsState.setValue('inputDeviceId', inputSelect.value);
  });

  const outputSelect = container.querySelector<HTMLSelectElement>('#setting-output-device');
  outputSelect?.addEventListener('change', () => {
    settingsState.setValue('outputDeviceId', outputSelect.value);
  });

  const noiseToggle = container.querySelector<HTMLInputElement>('#setting-noise-suppression');
  noiseToggle?.addEventListener('change', () => {
    settingsState.setValue('noiseSuppression', noiseToggle.checked);
  });

  const volumeButtons = container.querySelectorAll<HTMLButtonElement>('#setting-speaker-volume .settings-segmented-btn');
  volumeButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      volumeButtons.forEach((b) => b.classList.remove('is-active'));
      btn.classList.add('is-active');
      const val = btn.dataset.value as 'max' | 'high' | 'normal';
      settingsState.setValue('loudspeakerVolume', val);
    });
  });

  return container;
}
