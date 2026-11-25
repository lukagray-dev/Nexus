/**
 * @file types.ts
 * @description Type definitions and registry constants for the Settings Panel.
 *
 * Hey friend! This file centralizes:
 * 1. The 7 core Settings category tab IDs.
 * 2. Navigation item metadata (icon path, labels, descriptions).
 * 3. Complete strongly typed settings state schema across all 7 operational domains.
 * 4. Factory function for deterministic default configurations.
 */

import { VoicePresetId } from '../main-content/audio-voice-intercom/audio-voice-intercom.js';

/**
 * Union of all valid settings navigation tab identifiers.
 */
export type SettingsTabId =
  | 'audio'
  | 'surveillance'
  | 'connection'
  | 'security'
  | 'general'
  | 'devices'
  | 'diagnostics';

/**
 * Contract representing an item in the Settings left navigation sidebar.
 */
export interface SettingsTabItem {
  id: SettingsTabId;
  label: string;
  description: string;
  iconPath: string;
  badge?: string;
}

/**
 * Complete strongly-typed Settings values contract across all 7 categories.
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

  // 3. Connection & WebRTC
  autoReconnect: boolean;
  primaryStunServer: string;
  secondaryTurnServer: string;
  iceTransportPolicy: 'all' | 'relay';
  iceGatherTimeout: number; // in milliseconds
  signalingHeartbeat: number; // in seconds

  // 4. Security & Forensics
  remotePin: string;
  intruderSelfie: boolean;
  lockoutStrikes: '3' | '5' | 'disabled';
  maskPasswords: boolean;
  stealthConfirmation: boolean;
  alertIntruderSelfie: boolean;
  alertLowBattery: boolean;
  alertChildDisconnected: boolean;

  // 5. General & System
  downloadsDirectory: string;
  askBeforeDownload: boolean;
  autoConnectLastDevice: boolean;
  launchOnStartup: boolean;
  startMinimized: boolean;
  alwaysOnTop: boolean;
  keepScreenAwake: boolean;
  closeAction: 'tray' | 'exit';
  autoCheckUpdates: boolean;

  // 6. Paired Devices
  deviceAlias: string;

  // 7. Diagnostics & Storage
  diagnosticLogging: boolean;
}

/**
 * Ordered registry of all 7 Settings tabs with vector icons.
 */
export const SETTINGS_TABS: SettingsTabItem[] = [
  {
    id: 'general',
    label: 'General & System',
    description: 'Downloads, window & tray behavior',
    iconPath: 'assets/icons/settings/general.svg',
  },
  {
    id: 'connection',
    label: 'Connection & WebRTC',
    description: 'P2P network & ICE signaling',
    iconPath: 'assets/icons/settings/connection.svg',
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
    id: 'security',
    label: 'Security & App Lock',
    description: 'PIN guard, anti-tamper & alerts',
    iconPath: 'assets/icons/settings/security.svg',
  },
  {
    id: 'devices',
    label: 'Paired Devices',
    description: 'Child phone hardware & pairing',
    iconPath: 'assets/icons/settings/devices.svg',
    badge: '1 Online',
  },
  {
    id: 'diagnostics',
    label: 'Diagnostics & Storage',
    description: 'Cache, logs & system metrics',
    iconPath: 'assets/icons/settings/diagnostics.svg',
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

    // 3. Connection & WebRTC
    autoReconnect: true,
    primaryStunServer: 'stun:stun.l.google.com:19302',
    secondaryTurnServer: 'turn:turn.nexus-relay.net:3478',
    iceTransportPolicy: 'all',
    iceGatherTimeout: 400,
    signalingHeartbeat: 30,

    // 4. Security & Forensics
    remotePin: '123456',
    intruderSelfie: true,
    lockoutStrikes: '3',
    maskPasswords: true,
    stealthConfirmation: true,
    alertIntruderSelfie: true,
    alertLowBattery: true,
    alertChildDisconnected: true,

    // 5. General & System
    downloadsDirectory: '',
    askBeforeDownload: false,
    autoConnectLastDevice: false,
    launchOnStartup: false,
    startMinimized: false,
    alwaysOnTop: false,
    keepScreenAwake: true,
    closeAction: 'tray',
    autoCheckUpdates: true,

    // 6. Paired Devices
    deviceAlias: 'Lucas - Pixel 8',

    // 7. Diagnostics & Storage
    diagnosticLogging: true,
  };
}
