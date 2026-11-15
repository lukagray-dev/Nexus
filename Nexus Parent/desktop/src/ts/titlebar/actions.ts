/**
 * @file actions.ts
 * @description Window action controls for Nexus Parent.
 *
 * Hey friend! This module handles minimize, maximize/restore, close,
 * and keeps the maximize/restore icon synchronized with the reactive appState!
 */

import { minimizeWindow, maximizeWindow, closeWindow, isWindowMaximized } from '../shared/ipc.js';
import { appState } from '../shared/state.js';

/**
 * Initializes native window actions: minimize, maximize/restore, and close.
 */
export function setupWindowActions(): void {
  const minBtn = document.getElementById('btn-minimize');
  const maxBtn = document.getElementById('btn-maximize');
  const closeBtn = document.getElementById('btn-close');
  const maxIcon = document.getElementById('icon-max-restore');

  // Minimize window
  minBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    minimizeWindow();
  });

  // Maximize / Restore window
  maxBtn?.addEventListener('click', async (e) => {
    e.stopPropagation();
    await maximizeWindow();
    const isMax = await isWindowMaximized();
    appState.setIsMaximized(isMax);
  });

  // Close window
  closeBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeWindow();
  });

  // Initial synchronization: check if launched maximized
  isWindowMaximized().then((isMax) => {
    appState.setIsMaximized(isMax);
  });

  // Keep maximize icon synchronized whenever reactive appState changes
  appState.subscribe(() => {
    if (maxIcon) {
      const isMax = appState.getIsMaximized();
      if (isMax) {
        maxIcon.classList.remove('icon-maximize');
        maxIcon.classList.add('icon-unmaxmize');
        maxBtn?.setAttribute('title', 'Restore');
      } else {
        maxIcon.classList.remove('icon-unmaxmize');
        maxIcon.classList.add('icon-maximize');
        maxBtn?.setAttribute('title', 'Maximize');
      }
    }
  });

  // Listen to window resize events to keep the maximize icon synchronized
  window.addEventListener('resize', async () => {
    const isMax = await isWindowMaximized();
    appState.setIsMaximized(isMax);
  });
}
