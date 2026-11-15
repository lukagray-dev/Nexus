/**
 * @file wallpaper.ts
 * @description Remote Wallpaper Control Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel allows parents to view and remotely set the child device's
 * wallpaper on Home Screen, Lock Screen, or Both.
 */

import { triggerSidebarToast } from '../shared.js';

/**
 * Creates and returns the Wallpaper Remote Control panel element.
 */
export function renderWallpaperPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-wallpaper-panel';

  container.innerHTML = `
    <div class="wp-container">
      <!-- Device Mockup Preview -->
      <div class="wp-preview-frame">
        <div class="wp-preview-notch"></div>
        <div class="wp-preview-inner" id="wp-preview-display">
          <span>Active Wallpaper</span>
        </div>
      </div>

      <!-- Target Selection Buttons -->
      <div class="wp-target-selector">
        <button class="wp-target-btn active" data-target="home">Home Screen</button>
        <button class="wp-target-btn" data-target="lock">Lock Screen</button>
        <button class="wp-target-btn" data-target="both">Both</button>
      </div>

      <!-- Apply Button -->
      <button id="btn-apply-wallpaper" class="wp-apply-btn">
        <img src="assets/icons/main-content/image.svg" alt="" style="width:14px;height:14px;" />
        <span>Push Wallpaper to Device</span>
      </button>
    </div>
  `;

  // Attach target toggle and apply triggers
  setTimeout(() => {
    const targetBtns = container.querySelectorAll<HTMLButtonElement>('.wp-target-btn');
    targetBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        targetBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    container.querySelector('#btn-apply-wallpaper')?.addEventListener('click', () => {
      triggerSidebarToast('Wallpaper successfully applied to child device');
    });
  }, 0);

  return container;
}

