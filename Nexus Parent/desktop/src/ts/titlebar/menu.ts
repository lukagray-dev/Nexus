/**
 * @file menu.ts
 * @description Titlebar dropdown menus controller for Nexus Parent.
 *
 * Hey friend! This module implements top menu interactions:
 * reactive state subscription, hover-switching when active, outside-click dismissal,
 * and keyboard navigation.
 */

import { closeWindow } from '../shared/ipc.js';
import { appState } from '../shared/state.js';

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

  // Dismiss menus on Escape key
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && appState.getActiveMenu()) {
      appState.setActiveMenu(null);
    }
  });

  // Wire specific menu actions
  setupMenuActions();
}

/**
 * Connects standard system menu item actions.
 */
function setupMenuActions(): void {
  // File -> Close Window
  document.getElementById('menu-item-close-window')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    closeWindow();
  });

  // File -> Exit
  document.getElementById('menu-item-exit')?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.setActiveMenu(null);
    closeWindow();
  });

  // Interactive handler for other menu items
  const menuItems = document.querySelectorAll<HTMLElement>('.dropdown-item');
  menuItems.forEach((item) => {
    if (item.id !== 'menu-item-close-window' && item.id !== 'menu-item-exit') {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        appState.setActiveMenu(null);
        const actionLabel = item.querySelector('span')?.textContent || item.id;
        console.log(`[Menu Action] Triggered: ${actionLabel}`);
      });
    }
  });
}
