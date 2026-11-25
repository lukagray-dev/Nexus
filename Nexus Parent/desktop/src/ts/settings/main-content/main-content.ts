/**
 * @file main-content.ts
 * @description Central router and runtime wiring for all 7 Settings main-content categories.
 *
 * Hey friend! Every settings category now lives in its own folder under `main-content/`,
 * with one `.ts` file per individual setting. This module wires them together so `settings.ts`
 * can render any category cleanly and initialize native runtime integrations on startup.
 */

import { SettingsTabId } from '../sidebar/types.js';
import { renderAudioVoiceIntercomContent } from './audio-voice-intercom/audio-voice-intercom.js';
import { renderSurveillanceDvrContent } from './surveillance-dvr/surveillance-dvr.js';
import { renderConnectionWebrtcContent } from './connection-webrtc/connection-webrtc.js';
import { renderSecurityForensicsContent } from './security-forensics/security-forensics.js';
import {
  initGeneralSystemRuntime,
  renderGeneralSystemContent,
} from './general-system/general-system.js';
import { renderPairedDevicesContent } from './paired-devices/paired-devices.js';
import { renderDiagnosticsStorageContent } from './diagnostics-storage/diagnostics-storage.js';

/**
 * Builds the DOM section element for the given Settings category tab ID.
 */
export function renderSettingsMainContent(tabId: SettingsTabId): HTMLElement {
  switch (tabId) {
    case 'audio':
      return renderAudioVoiceIntercomContent();
    case 'surveillance':
      return renderSurveillanceDvrContent();
    case 'connection':
      return renderConnectionWebrtcContent();
    case 'security':
      return renderSecurityForensicsContent();
    case 'general':
      return renderGeneralSystemContent();
    case 'devices':
      return renderPairedDevicesContent();
    case 'diagnostics':
      return renderDiagnosticsStorageContent();
    default:
      return renderGeneralSystemContent();
  }
}

/**
 * Clears the mount container and renders the requested category view inside it.
 */
export function mountSettingsMainContent(container: HTMLElement, tabId: SettingsTabId): void {
  container.innerHTML = '';
  container.appendChild(renderSettingsMainContent(tabId));
}

/**
 * Initializes native runtime integrations across all settings categories (such as syncing
 * window close behavior, OS autostart, and system tray events with Rust).
 */
export function initSettingsMainContentRuntime(onOpenSettings?: () => void): () => void {
  const disposeGeneralSystem = initGeneralSystemRuntime(onOpenSettings);

  return () => {
    disposeGeneralSystem();
  };
}
