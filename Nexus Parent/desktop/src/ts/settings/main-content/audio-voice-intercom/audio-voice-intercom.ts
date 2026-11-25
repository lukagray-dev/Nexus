/**
 * @file audio-voice-intercom.ts
 * @description Audio & Voice Intercom Settings category compositor and runtime synchronizer.
 *
 * Hey friend! This module:
 * 1. Composes all Audio & Voice Intercom setting modules into the category view.
 * 2. Keeps `defaultVoicePreset`, `inputDeviceId`, `outputDeviceId`, and `noiseSuppression`
 *    synchronized in real time with `ParentAudioController`, `VoiceChangerEngine`,
 *    the floating Microphone panel, and `OpusAudioPlayer`.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  VoicePresetId,
  saveVoicePresetId,
} from '../../../main-content/mic/voice-presets.js';
import { parentAudioController } from '../../../main-content/mic/parent-audio-controller.js';
import { EVENT_AUDIO_OUTPUT_CHANGED } from '../../../main-content/mic/audio-device-service.js';
import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderVoicePresetSetting } from './voice-preset.js';
import { renderLoudspeakerVolumeSetting } from './loudspeaker-volume.js';
import { renderInputDeviceSetting } from './input-device.js';
import { renderOutputDeviceSetting } from './output-device.js';
import { renderNoiseSuppressionSetting } from './noise-suppression.js';

export type { VoicePresetId };

export const EVENT_INTERCOM_VOICE_SYNC = 'nexus-intercom-voice-sync';

/**
 * Renders the Audio & Voice Intercom Settings main content section.
 */
export function renderAudioVoiceIntercomContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Audio & Voice Intercom',
      'Configure local PC audio routing, DSP voice changer defaults, and child loudspeaker broadcast behavior.'
    )
  );

  container.appendChild(
    createSettingsGroup('VOICE CHANGER ENGINE', [
      renderVoicePresetSetting(),
      renderLoudspeakerVolumeSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('HARDWARE ROUTING & DSP', [
      renderInputDeviceSetting(),
      renderOutputDeviceSetting(),
      renderNoiseSuppressionSetting(),
    ])
  );

  return container;
}

/**
 * Initializes live synchronization between `settingsState` and the desktop audio
 * capture, voice changer DSP, and output routing pipelines.
 */
export function initAudioVoiceIntercomRuntime(): () => void {
  const initialValues = settingsState.getValues();

  // Reconcile persisted Settings default voice preset into the intercom engine on boot
  let lastVoicePreset = initialValues.defaultVoicePreset;
  let lastInputDeviceId = initialValues.inputDeviceId;
  let lastOutputDeviceId = initialValues.outputDeviceId;
  let lastNoiseSuppression = initialValues.noiseSuppression;

  saveVoicePresetId(lastVoicePreset);
  parentAudioController.setVoice(lastVoicePreset);

  const unsubscribeValues = settingsState.onValuesChange((values) => {
    // 1. Voice Changer preset sync (Settings -> DSP Engine + Floating Mic Panel)
    if (values.defaultVoicePreset !== lastVoicePreset) {
      lastVoicePreset = values.defaultVoicePreset;
      saveVoicePresetId(values.defaultVoicePreset);
      parentAudioController.setVoice(values.defaultVoicePreset);

      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(
          new CustomEvent(EVENT_INTERCOM_VOICE_SYNC, {
            detail: { voiceId: values.defaultVoicePreset },
          })
        );
      }
    }

    // 2. Microphone Input Device or Noise Suppression / AEC change -> reconfigure capture
    if (
      values.inputDeviceId !== lastInputDeviceId ||
      values.noiseSuppression !== lastNoiseSuppression
    ) {
      lastInputDeviceId = values.inputDeviceId;
      lastNoiseSuppression = values.noiseSuppression;
      void parentAudioController.reconfigureAudioInput();
    }

    // 3. Monitoring Output Speaker change -> notify active <audio> and AudioContext sinks
    if (values.outputDeviceId !== lastOutputDeviceId) {
      lastOutputDeviceId = values.outputDeviceId;
      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(
          new CustomEvent(EVENT_AUDIO_OUTPUT_CHANGED, {
            detail: { deviceId: values.outputDeviceId },
          })
        );
      }
    }
  });

  return () => {
    unsubscribeValues();
  };
}
