/**
 * @file audio-voice-intercom.ts
 * @description Audio & Voice Intercom Settings category compositor.
 *
 * Hey friend! Combines all individual Audio & Voice Intercom setting modules
 * into the category content view.
 */

import { VoicePresetId } from '../../../main-content/mic/voice-presets.js';
import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderVoicePresetSetting } from './voice-preset.js';
import { renderLoudspeakerVolumeSetting } from './loudspeaker-volume.js';
import { renderInputDeviceSetting } from './input-device.js';
import { renderOutputDeviceSetting } from './output-device.js';
import { renderNoiseSuppressionSetting } from './noise-suppression.js';

export type { VoicePresetId };

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
