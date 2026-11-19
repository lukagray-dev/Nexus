/**
 * @file vibrateflash.ts
 * @description Vibrate & Flash Remote Hardware Trigger Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel allows parents to actuate physical hardware features on the child phone:
 * 1. Vibration Waveforms: Short Buzz, Double Tap, Pulse Wave, and emergency SOS Morse pattern.
 * 2. Camera Torch / Flash: Flashlight On, Flashlight Off, 4Hz Strobe, and Emergency Beacon.
 * 3. Combined Modes: Synchronized Alert Alarm, Panic SOS, and Pulse Strobe.
 * 4. Zero Live Monitoring Footer: This panel is an on-demand hardware trigger, so the bottom Play/Pause
 *    monitoring button is hidden, and execution confirmation is rendered directly in the panel's status banner.
 */

import { rightSidebarState } from '../state.js';
import {
  type VibratePattern,
  type FlashAction,
  type CombinedPattern,
  CMD_VIBRATE_PREFIX,
  CMD_FLASH_PREFIX,
  CMD_VIBRATE_FLASH_PREFIX
} from '../types.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import { triggerSidebarToast } from '../shared.js';

/**
 * Creates and returns the Vibrate & Flash panel element.
 */
export function renderVibrateFlashPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-vibrateflash-panel';

  const updateUi = () => {
    const status = rightSidebarState.getVibrateFlashStatus();
    const isError = status.lastSuccess === false;
    const hasStatus = status.statusMessage !== null;

    container.innerHTML = `
      <div class="vf-grid">
        <!-- Live Hardware Confirmation Banner -->
        <div class="vf-status-banner">
          <div class="vf-status-dot ${hasStatus ? (isError ? 'error' : '') : 'idle'}"></div>
          <span id="vf-status-text" style="flex:1;">
            ${status.statusMessage || 'Hardware Link Ready: Select a pattern to trigger'}
          </span>
        </div>

        <!-- Vibration Patterns Section -->
        <div class="vf-group">
          <span class="vf-group-title">VIBRATION PATTERNS</span>
          <div class="vf-btn-grid">
            <button class="vf-action-btn btn-vf-vibe" data-vibe="short" title="Short Buzz (200ms)">
              <img src="assets/icons/main-content/vibrate.svg" alt="" />
              <span>Short Buzz</span>
            </button>
            <button class="vf-action-btn btn-vf-vibe" data-vibe="double" title="Double Tap (200ms x2)">
              <img src="assets/icons/main-content/vibrate.svg" alt="" />
              <span>Double Tap</span>
            </button>
            <button class="vf-action-btn btn-vf-vibe" data-vibe="pulse" title="Pulse Wave">
              <img src="assets/icons/main-content/vibrate.svg" alt="" />
              <span>Pulse Wave</span>
            </button>
            <button class="vf-action-btn btn-vf-vibe btn-sos" data-vibe="sos" title="SOS Emergency Alarm">
              <img src="assets/icons/main-content/vibrate.svg" alt="" />
              <span>SOS Pattern</span>
            </button>
          </div>
        </div>

        <!-- Camera Flashlight Section -->
        <div class="vf-group">
          <span class="vf-group-title">CAMERA FLASHLIGHT STROBE</span>
          <div class="vf-btn-grid">
            <button class="vf-action-btn btn-vf-flash" data-flash="on" title="Turn Flashlight On">
              <img src="assets/icons/main-content/flashlight.svg" alt="" />
              <span>Flashlight On</span>
            </button>
            <button class="vf-action-btn btn-vf-flash" data-flash="off" title="Turn Flashlight Off">
              <img src="assets/icons/main-content/flashlight.svg" alt="" />
              <span>Flashlight Off</span>
            </button>
            <button class="vf-action-btn btn-vf-flash" data-flash="strobe" title="4Hz Strobe (10 cycles)">
              <img src="assets/icons/main-content/zap.svg" alt="" />
              <span>Strobe (4 Hz)</span>
            </button>
            <button class="vf-action-btn btn-vf-flash btn-sos" data-flash="beacon" title="Emergency Beacon (6 pulses)">
              <img src="assets/icons/main-content/zap.svg" alt="" />
              <span>Emergency Beacon</span>
            </button>
          </div>
        </div>

        <!-- Combined Vibration & Flashlight Section -->
        <div class="vf-group">
          <span class="vf-group-title">COMBINED VIBRATE & FLASH</span>
          <div class="vf-btn-grid">
            <button class="vf-action-btn btn-vf-combined" data-combined="alert" title="Alert: Double Buzz + Double Strobe">
              <img src="assets/icons/main-content/bell.svg" alt="" />
              <span>Alert Alarm</span>
            </button>
            <button class="vf-action-btn btn-vf-combined btn-sos" data-combined="sos" title="Emergency: Morse SOS Vibration + Beacon Flash">
              <img src="assets/icons/main-content/zap.svg" alt="" />
              <span>Panic SOS</span>
            </button>
            <button class="vf-action-btn btn-vf-combined" data-combined="pulse" title="Pulse: Pulse Wave + Strobe Flash">
              <img src="assets/icons/main-content/activity.svg" alt="" />
              <span>Pulse Strobe</span>
            </button>
          </div>
        </div>
      </div>
    `;

    attachButtonListeners(container);
  };

  // Initial render
  updateUi();

  return container;
}

/**
 * Attaches click triggers to all hardware pattern buttons.
 */
function attachButtonListeners(container: HTMLElement): void {
  // 1. Vibration Patterns
  container.querySelectorAll<HTMLButtonElement>('.btn-vf-vibe').forEach(btn => {
    btn.addEventListener('click', () => {
      const pattern = btn.dataset.vibe as VibratePattern;
      if (!pattern) return;

      flashButtonTrigger(btn);
      dispatchVibrationCommand(pattern);
    });
  });

  // 2. Flashlight Patterns
  container.querySelectorAll<HTMLButtonElement>('.btn-vf-flash').forEach(btn => {
    btn.addEventListener('click', () => {
      const action = btn.dataset.flash as FlashAction;
      if (!action) return;

      flashButtonTrigger(btn);
      dispatchFlashCommand(action);
    });
  });

  // 3. Combined Patterns
  container.querySelectorAll<HTMLButtonElement>('.btn-vf-combined').forEach(btn => {
    btn.addEventListener('click', () => {
      const pattern = btn.dataset.combined as CombinedPattern;
      if (!pattern) return;

      flashButtonTrigger(btn);
      dispatchCombinedCommand(pattern);
    });
  });
}

/**
 * Highlights a clicked button with an active feedback class for 600ms.
 */
function flashButtonTrigger(btn: HTMLButtonElement): void {
  btn.classList.add('active-trigger');
  setTimeout(() => {
    btn.classList.remove('active-trigger');
  }, 600);
}

/**
 * Dispatches a vibration pattern command across the WebRTC DataChannel.
 */
function dispatchVibrationCommand(pattern: VibratePattern): void {
  const cmd = `${CMD_VIBRATE_PREFIX}${pattern}`;
  console.log(`[VibrateFlashPanel] Dispatching: ${cmd}`);
  rightSidebarState.setVibrateFlashStatus(pattern, null, `Sending vibration '${pattern}' to child device...`);

  const sent = webrtcManager.sendCommand(cmd);
  if (!sent) {
    rightSidebarState.setVibrateFlashStatus(pattern, false, 'Failed: DataChannel not connected');
    triggerSidebarToast('Cannot send: DataChannel not connected');
  }
}

/**
 * Dispatches a camera flashlight action command across the WebRTC DataChannel.
 */
function dispatchFlashCommand(action: FlashAction): void {
  const cmd = `${CMD_FLASH_PREFIX}${action}`;
  console.log(`[VibrateFlashPanel] Dispatching: ${cmd}`);
  rightSidebarState.setVibrateFlashStatus(action, null, `Sending flashlight '${action}' to child device...`);

  const sent = webrtcManager.sendCommand(cmd);
  if (!sent) {
    rightSidebarState.setVibrateFlashStatus(action, false, 'Failed: DataChannel not connected');
    triggerSidebarToast('Cannot send: DataChannel not connected');
  }
}

/**
 * Dispatches a combined vibration & flash command across the WebRTC DataChannel.
 */
function dispatchCombinedCommand(pattern: CombinedPattern): void {
  const cmd = `${CMD_VIBRATE_FLASH_PREFIX}${pattern}`;
  console.log(`[VibrateFlashPanel] Dispatching: ${cmd}`);
  rightSidebarState.setVibrateFlashStatus(pattern, null, `Sending combined '${pattern}' to child device...`);

  const sent = webrtcManager.sendCommand(cmd);
  if (!sent) {
    rightSidebarState.setVibrateFlashStatus(pattern, false, 'Failed: DataChannel not connected');
    triggerSidebarToast('Cannot send: DataChannel not connected');
  }
}
