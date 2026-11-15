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

  // Initialize settings dialog listeners
  initSettings();

  console.log('[Nexus Parent UI] Initialized successfully.');
});
