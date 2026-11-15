/**
 * @file vibrateflash.ts
 * @description Vibrate & Flash Remote Hardware Trigger Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel allows parents to trigger hardware actions on the child device:
 * vibration patterns (short buzz, double tap, pulse wave, SOS alarm) and camera flashlight
 * controls (on, off, strobe, emergency beacon).
 */

import { triggerSidebarToast } from '../shared.js';

/**
 * Creates and returns the Vibrate & Flash panel element.
 */
export function renderVibrateFlashPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-vibrateflash-panel';

  container.innerHTML = `
    <div class="vf-grid">
      <!-- Vibration Patterns Section -->
      <div class="vf-group">
        <span class="vf-group-title">VIBRATION PATTERNS</span>
        <div class="vf-btn-grid">
          <button class="vf-action-btn btn-vf-pattern" data-vibe="short" title="Short Buzz">
            <img src="assets/icons/main-content/vibrate.svg" alt="" />
            <span>Short Buzz</span>
          </button>
          <button class="vf-action-btn btn-vf-pattern" data-vibe="double" title="Double Tap">
            <img src="assets/icons/main-content/vibrate.svg" alt="" />
            <span>Double Tap</span>
          </button>
          <button class="vf-action-btn btn-vf-pattern" data-vibe="pulse" title="Pulse Wave">
            <img src="assets/icons/main-content/vibrate.svg" alt="" />
            <span>Pulse Wave</span>
          </button>
          <button class="vf-action-btn btn-vf-pattern btn-sos" data-vibe="sos" title="SOS Emergency Alarm">
            <img src="assets/icons/main-content/vibrate.svg" alt="" />
            <span>SOS Pattern</span>
          </button>
        </div>
      </div>

      <!-- Flashlight Section -->
      <div class="vf-group">
        <span class="vf-group-title">CAMERA FLASHLIGHT STROBE</span>
        <div class="vf-btn-grid">
          <button class="vf-action-btn btn-flash-action" data-flash="on" title="Turn Flashlight On">
            <img src="assets/icons/main-content/flashlight.svg" alt="" />
            <span>Flashlight On</span>
          </button>
          <button class="vf-action-btn btn-flash-action" data-flash="off" title="Turn Flashlight Off">
            <img src="assets/icons/main-content/flashlight.svg" alt="" />
            <span>Flashlight Off</span>
          </button>
          <button class="vf-action-btn btn-flash-action" data-flash="strobe" title="4Hz Strobe">
            <img src="assets/icons/main-content/zap.svg" alt="" />
            <span>Strobe (4 Hz)</span>
          </button>
          <button class="vf-action-btn btn-flash-action btn-sos" data-flash="beacon" title="Emergency Beacon">
            <img src="assets/icons/main-content/zap.svg" alt="" />
            <span>Emergency Beacon</span>
          </button>
        </div>
      </div>
    </div>
  `;

  // Attach button triggers
  setTimeout(() => {
    container.querySelectorAll('.btn-vf-pattern').forEach(btn => {
      btn.addEventListener('click', () => {
        const type = (btn as HTMLElement).dataset.vibe;
        triggerSidebarToast(`Sent vibration command: ${type}`);
      });
    });

    container.querySelectorAll('.btn-flash-action').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = (btn as HTMLElement).dataset.flash;
        triggerSidebarToast(`Flashlight command dispatched: ${action}`);
      });
    });
  }, 0);

  return container;
}

