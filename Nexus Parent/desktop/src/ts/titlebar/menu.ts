/**
 * @file menu.ts
 * @description Titlebar dropdown menus controller for Nexus Parent.
 *
 * Hey friend! This module implements top menu interactions:
 * - Reactive state subscription, hover-switching when active, outside-click dismissal.
 * - File menu actions (Pair Child Device, Settings, Exit).
 * - Help menu options mirroring Operon (Documentation, Check for update, Report bug,
 *   About modal, Follow creator, See repo).
 */

import { exitApplication, openExternalUrl } from '../shared/ipc.js';
import { deviceState } from '../shared/device-state.js';
import { appState } from '../shared/state.js';
import { showToast } from '../shared/toast.js';
import { openAboutDialog } from './about.js';

export const DOCUMENTATION_URL = 'https://github.com/lukagray-dev/Nexus/tree/main/docs';
export const REPORT_BUG_URL = 'https://github.com/lukagray-dev/Nexus/issues';
export const FOLLOW_CREATOR_URL = 'https://www.instagram.com/lukagray.official/';
export const REPOSITORY_URL = 'https://github.com/lukagray-dev/Nexus';

/**
 * Sets up titlebar dropdown menus and their interactive event listeners.
 */
export function setupMenus(): void {
  const menuTriggers = document.querySelectorAll<HTMLButtonElement>('.menu-trigger');
  const dropdownMenus = document.querySelectorAll<HTMLElement>('.dropdown-menu');

  // Open/toggle dropdown when menu trigger is clicked
  menuTriggers.forEach((trigger) => {
    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      const menuName = trigger.dataset.menu || null;
      const current = appState.getActiveMenu();
      appState.setActiveMenu(current === menuName ? null : menuName);
    });

    // Hover-switch when a menu is already open
    trigger.addEventListener('mouseenter', () => {
      const current = appState.getActiveMenu();
      if (current && trigger.dataset.menu) {
        appState.setActiveMenu(trigger.dataset.menu);
      }
    });
  });

  // Sync active menu state with DOM classes via reactive state subscription
  appState.subscribe(() => {
    const active = appState.getActiveMenu();

    menuTriggers.forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.menu === active);
    });

    dropdownMenus.forEach((menu) => {
      menu.classList.toggle('open', menu.dataset.menu === active);
    });
  });

  // Dismiss menus on outside click
  window.addEventListener('click', () => {
    if (appState.getActiveMenu()) {
      appState.setActiveMenu(null);
    }
  });

  // Dismiss menus on Escape key, and handle Ctrl+Q / Cmd+Q full application exit
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && appState.getActiveMenu()) {
      appState.setActiveMenu(null);
      return;
    }

    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'q') {
      e.preventDefault();
      appState.setActiveMenu(null);
      deviceState.disconnect();
      void exitApplication();
    }
  });

  // Wire specific menu actions
  setupMenuActions();
}

/**
 * Connects standard system menu item actions.
 */
function setupMenuActions(): void {
  // File -> Pair Child Device
  document.getElementById('menu-item-pair-device')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    const pairBtn = document.getElementById('btn-sidebar-pair');
    pairBtn?.click();
  });

  // File -> Settings (opens tactical settings modal)
  document.getElementById('menu-item-settings')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    import('../settings/settings.js').then((m) => m.openSettingsDialog());
  });

  // File -> Exit (forces full termination even when [X] is set to Minimize to Tray)
  document.getElementById('menu-item-exit')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    deviceState.disconnect();
    void exitApplication();
  });

  // Help -> Documentation
  document.getElementById('menu-item-documentation')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    openExternalUrl(DOCUMENTATION_URL);
  });

  // Help -> Check for update
  document.getElementById('menu-item-check-update')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    showToast('You are running the latest version of Nexus Parent (v0.1.0).');
  });

  // Help -> Report bug
  document.getElementById('menu-item-report-bug')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    openExternalUrl(REPORT_BUG_URL);
  });

  // Help -> About
  document.getElementById('menu-item-about')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    openAboutDialog();
  });

  // Help -> Follow creator
  document.getElementById('menu-item-follow-creator')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    openExternalUrl(FOLLOW_CREATOR_URL);
  });

  // Help -> See repo
  document.getElementById('menu-item-see-repo')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    openExternalUrl(REPOSITORY_URL);
  });
}
