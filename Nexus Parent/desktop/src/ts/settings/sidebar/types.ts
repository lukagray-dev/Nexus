/**
 * @file types.ts
 * @description Type definitions and registry constants for the Settings Panel.
 *
 * Hey friend! This file centralizes:
 * 1. The 5 core Settings category tab IDs.
 * 2. Navigation item metadata (icon path, labels, descriptions).
 * 3. Complete strongly typed settings state schema across all 5 operational domains.
 * 4. Factory function for deterministic default configurations.
 */

import { VoicePresetId } from '../main-content/audio-voice-intercom/audio-voice-intercom.js';

/**
 * Union of all valid settings navigation tab identifiers.
 */
export type SettingsTabId =
  | 'audio'
  | 'surveillance'
  | 'general'
  | 'devices'
  | 'about';

/**
 * Contract representing an item in the Settings left navigation sidebar.
 */
export interface SettingsTabItem {
  id: SettingsTabId;
  label: string;
  description: string;
  iconPath: string;
  preserveIconColors?: boolean;
  badge?: string;
}

/**
 * Complete strongly-typed Settings values contract across all 5 categories.
 */
export interface SettingsValues {
  // 1. Audio & Voice Intercom
  defaultVoicePreset: VoicePresetId;
  inputDeviceId: string;
  outputDeviceId: string;
  noiseSuppression: boolean;
  loudspeakerVolume: 'max' | 'high' | 'normal';

  // 2. Surveillance & DVR
  screenQuality: '1080p' | '720p' | '540p';
  preferredCameraLens: 'rear' | 'front';
  dvrBufferDuration: '15m' | '30m' | '60m';
  dvrClipFormat: 'mp4' | 'webm';
  hardwareAcceleration: boolean;

  // 3. General & System
  downloadsDirectory: string;
  askBeforeDownload: boolean;
  autoConnectLastDevice: boolean;
  launchOnStartup: boolean;
  startMinimized: boolean;
  alwaysOnTop: boolean;
  keepScreenAwake: boolean;
  closeAction: 'tray' | 'exit';
  autoCheckUpdates: boolean;
  alertIntruderSelfie: boolean;
  alertLowBattery: boolean;
  alertChildDisconnected: boolean;
  alertChildActiveCall: boolean;
}

/**
 * Ordered registry of all 5 Settings tabs with vector icons.
 */
export const SETTINGS_TABS: SettingsTabItem[] = [
  {
    id: 'general',
    label: 'General & System',
    description: 'Downloads, window, tray & alert behavior',
    iconPath: 'assets/icons/settings/general.svg',
  },
  {
    id: 'audio',
    label: 'Audio & Intercom',
    description: 'Voice changer & audio routing',
    iconPath: 'assets/icons/settings/audio.svg',
  },
  {
    id: 'surveillance',
    label: 'Surveillance & DVR',
    description: 'Camera, screen & video recording',
    iconPath: 'assets/icons/settings/surveillance.svg',
  },
  {
    id: 'devices',
    label: 'Paired Devices',
    description: 'Child phone hardware & telemetry',
    iconPath: 'assets/icons/settings/devices.svg',
  },
  {
    id: 'about',
    label: 'About Nexus Parent',
    description: 'App info, runtime specs & workspace data',
    iconPath: 'assets/logo/nexus-parent.svg',
    preserveIconColors: true,
  },
];

/**
 * Creates a clean baseline set of default configuration values.
 */
export function getDefaultSettingsValues(): SettingsValues {
  return {
    // 1. Audio & Voice Intercom
    defaultVoicePreset: 'original',
    inputDeviceId: 'default',
    outputDeviceId: 'default',
    noiseSuppression: true,
    loudspeakerVolume: 'max',

    // 2. Surveillance & DVR
    screenQuality: '1080p',
    preferredCameraLens: 'rear',
    dvrBufferDuration: '30m',
    dvrClipFormat: 'mp4',
    hardwareAcceleration: true,

    // 3. General & System
    downloadsDirectory: '',
    askBeforeDownload: false,
    autoConnectLastDevice: false,
    launchOnStartup: false,
    startMinimized: false,
    alwaysOnTop: false,
    keepScreenAwake: true,
    closeAction: 'tray',
    autoCheckUpdates: true,
    alertIntruderSelfie: true,
    alertLowBattery: true,
    alertChildDisconnected: true,
    alertChildActiveCall: true,
  };
}
