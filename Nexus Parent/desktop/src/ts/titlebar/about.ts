/**
 * @file about.ts
 * @description Unified About trigger for Nexus Parent.
 *
 * Hey friend! Clicking `Help -> About Nexus Parent` in the titlebar menu now opens
 * the Settings dialog directly on the `About Nexus Parent` tab so there is one
 * authoritative, live source of parent application and runtime details.
 */

import { openSettingsDialog } from '../settings/settings.js';

/**
 * Opens the unified Settings -> About Nexus Parent page.
 */
export function openAboutDialog(): void {
  openSettingsDialog('about');
}
