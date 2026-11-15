/**
 * @file applock.ts
 * @description App Lock Controls Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel allows parents to remotely toggle app lock enforcement
 * on specific installed apps with PIN guard protection.
 */

import { triggerSidebarToast } from '../shared.js';
import type { AppLockItem } from '../types.js';

const INITIAL_APPS_LIST: AppLockItem[] = [
  { name: 'Instagram', pkg: 'com.instagram.android', locked: true },
  { name: 'TikTok', pkg: 'com.zhiliaoapp.musically', locked: true },
  { name: 'Snapchat', pkg: 'com.snapchat.android', locked: true },
  { name: 'YouTube', pkg: 'com.google.android.youtube', locked: false },
  { name: 'Settings & System', pkg: 'com.android.settings', locked: true },
  { name: 'Google Play Store', pkg: 'com.android.vending', locked: false },
  { name: 'Chrome Browser', pkg: 'com.android.chrome', locked: false }
];

/**
 * Creates and returns the App Lock Controls panel element.
 */
export function renderAppLockPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-applock-panel';

  container.innerHTML = `
    <!-- Header Status Subbar -->
    <div class="rs-subbar">
      <div class="applock-header-row">
        <span>APP LOCK STATUS</span>
        <span class="applock-count-text">4 APPS LOCKED</span>
      </div>
    </div>

    <!-- Apps List with Toggle Switches -->
    <div id="applock-list" class="rs-list-container">
      ${INITIAL_APPS_LIST.map(a => `
        <div class="applock-row">
          <div class="applock-info">
            <span class="applock-app-name">${a.name}</span>
            <span class="applock-app-pkg">${a.pkg}</span>
          </div>
          <div class="switch-toggle ${a.locked ? 'active' : ''}" data-pkg="${a.pkg}">
            <div class="switch-thumb"></div>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  setTimeout(() => {
    const switches = container.querySelectorAll<HTMLElement>('.switch-toggle');
    switches.forEach(sw => {
      sw.addEventListener('click', () => {
        sw.classList.toggle('active');
        const isActive = sw.classList.contains('active');
        triggerSidebarToast(`${isActive ? 'Locked' : 'Unlocked'} ${sw.dataset.pkg}`);
      });
    });
  }, 0);

  return container;
}

