/**
 * @file main.ts
 * @description UI Entrypoint for Nexus Parent Desktop.
 *
 * Hey friend! This bootstraps the frontend components upon DOM ready:
 * 1. Native Titlebar controls & menus
 * 2. Collapsible Left Sidebar with Nexus monitoring features
 * 3. Full-viewport Main Content Router (Camera, Screen, Location, Storage feeds)
 * 4. Collapsible Right Sidebar for inspection panels and hardware controls
 * 5. Application Settings modal
 */

import { initTitlebar } from './titlebar/titlebar.js';
import { initSidebar } from './left-sidebar/sidebar.js';
import { initMainContent } from './main-content/main-content.js';
import { initRightSidebar } from './right-sidebar/right-sidebar.js';
import { initSettings } from './settings/settings.js';
import { runStartupAutoConnect } from './settings/main-content/general-system/auto-connect-last-device.js';
import { deviceState } from './shared/device-state.js';

window.addEventListener('DOMContentLoaded', () => {
  console.log('[Nexus Parent UI] DOM ready. Booting components...');

  // Initialize native titlebar controls (minimize, maximize, close, menus)
  initTitlebar();

  // Initialize collapsible left sidebar (surveillance, comms, apps, controls)
  initSidebar();

  // Initialize full-viewport main content router (Camera, Screen, Location, Storage)
  initMainContent();

  // Initialize collapsible right sidebar for inspection and controls
  initRightSidebar();

  // Initialize settings dialog listeners and runtime synchronizers
  initSettings();

  // Attempt auto-connect to last paired child device if enabled in General & System settings
  void runStartupAutoConnect();

  // Ensures active WebRTC session, DataChannel, and media streams are torn down cleanly when window closes
  window.addEventListener('beforeunload', () => {
    deviceState.disconnect();
  });

  console.log('[Nexus Parent UI] Initialized successfully.');
});
