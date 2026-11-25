/**
 * @file titlebar.ts
 * @description Main Titlebar coordinator for Nexus Parent.
 *
 * Hey friend! This coordinates both the native window controls (minimize, maximize, close)
 * and the desktop dropdown menus (File, Devices, View, Safety, Help).
 */

import { setupWindowActions } from './actions.js';
import { setupMenus } from './menu.js';
import { setupTitlebarUpdateButton } from './updater-ui.js';

/**
 * Initializes and wires the complete native titlebar and menus.
 */
export function initTitlebar(): void {
  setupWindowActions();
  setupMenus();
  setupTitlebarUpdateButton();
}
