/**
 * @file applock.ts
 * @description App Lock Security & Intruder Selfie Monitor Panel for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel provides full parental command oversight over the child's device lock:
 * 1. Master Remote Lock Toggle: Enables or disables the 6-digit lock screen remotely with one click.
 * 2. Remote PIN Change: Opens a tactical dialog to push a new 6-digit PIN to the child device.
 * 3. Live & Offline Attempt Feed: Displays cards for every unlock attempt, including outcome,
 *    timestamp, failed attempt progression, and the captured silent front-camera selfie.
 * 4. High-Res Selfie Modal: Clicking any selfie opens an expanded inspection view.
 * 5. Telemetry Statistics: Aggregates total attempts, successful entries, failed entries, and lockouts.
 */

import { rightSidebarState } from '../state.js';
import { triggerSidebarToast } from '../shared.js';
import {
  type AppLockRecord,
  CMD_APPLOCK_TOGGLE,
  CMD_APPLOCK_SET_PIN,
  CMD_APPLOCK_CLEAR,
  formatRelativeTime
} from '../types.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';

let activeSearchQuery: string = '';

/**
 * Creates and returns the App Lock Controls & Intruder Audit panel element.
 */
export function renderAppLockPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-applock-panel';

  const isEnabled = rightSidebarState.isAppLockEnabled();
  const allRecords = rightSidebarState.getAppLockRecords();
  const filteredRecords = filterRecords(allRecords, activeSearchQuery);

  const totalAttempts = allRecords.length;
  const successCount = allRecords.filter(r => r.pinCorrect).length;
  const failedCount = allRecords.filter(r => !r.pinCorrect).length;
  const lockoutCount = allRecords.filter(r => r.isLockedOut).length;

  container.innerHTML = `
    <!-- Subbar: Master Toggle & Remote Actions -->
    <div class="rs-subbar" style="flex-direction:column; gap:10px; align-items:stretch; height:auto; padding:12px 14px;">
      <div style="display:flex; align-items:center; justify-content:space-between;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:11px; font-weight:700; letter-spacing:0.08em; text-transform:uppercase; color:var(--text-muted);">
            APP LOCK: <span style="color:${isEnabled ? 'var(--status-connected, #22C55E)' : 'var(--text-muted)'};">${isEnabled ? 'ACTIVE' : 'DISABLED'}</span>
          </span>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          <button id="btn-applock-change-pin" class="keyboard-clear-btn" style="padding:4px 8px; font-size:11px;" title="Remotely change 6-digit PIN">
            Change PIN
          </button>
          <button id="btn-applock-clear" class="keyboard-clear-btn" style="padding:4px 8px; font-size:11px;" title="Clear recorded attempts">
            Clear
          </button>
          <div id="switch-applock-toggle" class="switch-toggle ${isEnabled ? 'active' : ''}" style="cursor:pointer;" title="Toggle App Lock remotely">
            <div class="switch-thumb"></div>
          </div>
        </div>
      </div>

      <!-- Search Input Wrap -->
      <div class="rs-search-wrap" style="width:100%;">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="applock-search-input" class="rs-search-input" placeholder="Search attempts by status or date..." spellcheck="false" value="${escapeHtml(activeSearchQuery)}" />
      </div>

      <!-- Stats Metric Bar -->
      <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:6px; margin-top:2px;">
        <div style="background:var(--bg-card, #121212); border:1px solid var(--border-subtle, #262626); border-radius:6px; padding:6px; text-align:center;">
          <div style="font-size:14px; font-weight:700; color:#FFFFFF;">${totalAttempts}</div>
          <div style="font-size:9px; color:var(--text-muted); font-family:monospace;">TOTAL</div>
        </div>
        <div style="background:var(--bg-card, #121212); border:1px solid var(--border-subtle, #262626); border-radius:6px; padding:6px; text-align:center;">
          <div style="font-size:14px; font-weight:700; color:#22C55E;">${successCount}</div>
          <div style="font-size:9px; color:var(--text-muted); font-family:monospace;">SUCCESS</div>
        </div>
        <div style="background:var(--bg-card, #121212); border:1px solid var(--border-subtle, #262626); border-radius:6px; padding:6px; text-align:center;">
          <div style="font-size:14px; font-weight:700; color:#EAB308;">${failedCount}</div>
          <div style="font-size:9px; color:var(--text-muted); font-family:monospace;">FAILED</div>
        </div>
        <div style="background:var(--bg-card, #121212); border:1px solid var(--border-subtle, #262626); border-radius:6px; padding:6px; text-align:center;">
          <div style="font-size:14px; font-weight:700; color:#EF4444;">${lockoutCount}</div>
          <div style="font-size:9px; color:var(--text-muted); font-family:monospace;">LOCKOUTS</div>
        </div>
      </div>
    </div>

    <!-- Unlock Attempt Cards Stream -->
    <div id="applock-list" class="rs-list-container" style="flex:1; overflow-y:auto; padding:10px 14px; display:flex; flex-direction:column; gap:10px;">
      ${filteredRecords.length === 0 ? renderEmptyState(allRecords.length > 0) : renderAttemptItems(filteredRecords)}
    </div>
  `;

  // Attach event handlers
  setTimeout(() => {
    attachAppLockListeners(container);
  }, 0);

  return container;
}

/**
 * Renders individual unlock attempt cards with metadata and captured selfies.
 */
function renderAttemptItems(records: AppLockRecord[]): string {
  return records.map(record => {
    const formattedTime = formatRelativeTime(record.timestamp);
    const dateStr = new Date(record.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    let statusPill = '';
    if (record.pinCorrect) {
      statusPill = `<span style="display:inline-flex; align-items:center; gap:4px; font-size:10px; font-weight:700; font-family:monospace; background:rgba(34, 197, 94, 0.15); color:#22C55E; padding:2px 8px; border-radius:4px;">
        <img src="assets/icons/left-sidebar/shield-check.svg" alt="" style="width:10px;height:10px;" />
        UNLOCKED
      </span>`;
    } else if (record.isLockedOut) {
      statusPill = `<span style="display:inline-flex; align-items:center; gap:4px; font-size:10px; font-weight:700; font-family:monospace; background:rgba(239, 68, 68, 0.15); color:#EF4444; padding:2px 8px; border-radius:4px;">
        <img src="assets/icons/left-sidebar/alert-triangle.svg" alt="" style="width:10px;height:10px;" />
        LOCKED OUT (60s)
      </span>`;
    } else {
      statusPill = `<span style="display:inline-flex; align-items:center; gap:4px; font-size:10px; font-weight:700; font-family:monospace; background:rgba(234, 179, 8, 0.15); color:#EAB308; padding:2px 8px; border-radius:4px;">
        WRONG PIN
      </span>`;
    }

    const selfieHtml = record.selfie
      ? `<div class="applock-selfie-thumb" data-id="${record.id}" style="width:64px; height:64px; border-radius:6px; overflow:hidden; border:1px solid var(--border-medium, #383838); cursor:pointer; flex-shrink:0; background:#000;">
           <img src="data:image/jpeg;base64,${record.selfie}" alt="Intruder Photo" style="width:100%; height:100%; object-fit:cover;" />
         </div>`
      : `<div style="width:64px; height:64px; border-radius:6px; border:1px dashed var(--border-subtle, #262626); display:flex; align-items:center; justify-content:center; flex-shrink:0; background:rgba(255,255,255,0.02);">
           <img src="assets/icons/left-sidebar/camera.svg" alt="" style="width:18px;height:18px;opacity:0.2;filter:invert(1);" />
         </div>`;

    return `
      <div class="rs-list-item" style="display:flex; gap:12px; align-items:center; padding:10px; background:var(--bg-card, #121212); border:1px solid var(--border-subtle, #262626); border-radius:8px;">
        ${selfieHtml}
        <div style="flex:1; min-width:0; display:flex; flex-direction:column; gap:4px;">
          <div style="display:flex; align-items:center; justify-content:space-between; gap:6px;">
            ${statusPill}
            <span style="font-size:11px; color:var(--text-muted); font-family:monospace;">${formattedTime}</span>
          </div>
          <div style="font-size:11px; color:var(--text-secondary); margin-top:2px;">
            ${record.pinCorrect ? 'Access granted by valid 6-digit PIN' : `Consecutive failed attempts: ${record.failedAttempts} / 3`}
          </div>
          <div style="font-size:10px; color:var(--text-muted); font-family:monospace;">
            ${dateStr}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

/**
 * Renders empty state when no attempts are logged.
 */
function renderEmptyState(hasUnfilteredRecords: boolean): string {
  if (hasUnfilteredRecords) {
    return `
      <div style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:40px 20px; text-align:center; color:var(--text-muted);">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:24px; height:24px; opacity:0.3; filter:invert(1); margin-bottom:12px;" />
        <div style="font-size:13px; font-weight:600; color:#FFFFFF; margin-bottom:4px;">No Matching Attempts</div>
        <div style="font-size:11px;">Try adjusting your search criteria</div>
      </div>
    `;
  }

  return `
    <div style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:40px 20px; text-align:center; color:var(--text-muted);">
      <img src="assets/icons/left-sidebar/lock.svg" alt="" style="width:32px; height:32px; opacity:0.25; filter:invert(1); margin-bottom:12px;" />
      <div style="font-size:13px; font-weight:600; color:#FFFFFF; margin-bottom:4px;">No Unlock Attempts Recorded</div>
      <div style="font-size:11px; max-width:240px; line-height:1.4;">
        Attempts on the child device will be recorded with photos and streamed live or on-demand.
      </div>
    </div>
  `;
}

/**
 * Attaches DOM click and input listeners for App Lock controls.
 */
function attachAppLockListeners(container: HTMLElement): void {
  // 1. Search Input
  const searchInput = container.querySelector<HTMLInputElement>('#applock-search-input');
  searchInput?.addEventListener('input', (e) => {
    activeSearchQuery = (e.target as HTMLInputElement).value;
    const allRecords = rightSidebarState.getAppLockRecords();
    const filtered = filterRecords(allRecords, activeSearchQuery);
    const list = container.querySelector('#applock-list');
    if (list) {
      list.innerHTML = filtered.length === 0 ? renderEmptyState(allRecords.length > 0) : renderAttemptItems(filtered);
      attachSelfieModalListeners(list);
    }
  });

  // 2. Master Lock Toggle Switch
  const toggleSwitch = container.querySelector<HTMLElement>('#switch-applock-toggle');
  toggleSwitch?.addEventListener('click', () => {
    const currentState = rightSidebarState.isAppLockEnabled();
    const nextState = !currentState;

    const payload = JSON.stringify({
      type: CMD_APPLOCK_TOGGLE,
      enabled: nextState
    });

    const sent = webrtcManager.sendCommand(payload);
    if (sent) {
      rightSidebarState.setAppLockEnabled(nextState);
      toggleSwitch.classList.toggle('active', nextState);
      triggerSidebarToast(`App Lock remote command sent: ${nextState ? 'Enabled' : 'Disabled'}`);
    } else {
      triggerSidebarToast('Cannot send: DataChannel not connected');
    }
  });

  // 3. Change PIN Button
  const changePinBtn = container.querySelector('#btn-applock-change-pin');
  changePinBtn?.addEventListener('click', () => {
    showChangePinDialog();
  });

  // 4. Clear History Button
  const clearBtn = container.querySelector('#btn-applock-clear');
  clearBtn?.addEventListener('click', () => {
    rightSidebarState.clearAppLockRecords();
    webrtcManager.sendCommand(CMD_APPLOCK_CLEAR);
    triggerSidebarToast('App Lock attempt logs cleared');
  });

  // 5. Selfie Click Modal
  const list = container.querySelector('#applock-list');
  if (list) {
    attachSelfieModalListeners(list);
  }
}

/**
 * Attaches click handlers to selfie thumbnails to open the expanded modal.
 */
function attachSelfieModalListeners(container: Element): void {
  const thumbs = container.querySelectorAll<HTMLElement>('.applock-selfie-thumb');
  thumbs.forEach(thumb => {
    thumb.addEventListener('click', () => {
      const id = thumb.dataset.id;
      const record = rightSidebarState.getAppLockRecords().find(r => r.id === id);
      if (record && record.selfie) {
        showSelfieModal(record);
      }
    });
  });
}

/**
 * Displays an expanded modal with full-resolution intruder selfie and attempt metadata.
 */
function showSelfieModal(record: AppLockRecord): void {
  const existing = document.getElementById('nexus-selfie-modal');
  if (existing) existing.remove();

  const modal = document.createElement('div');
  modal.id = 'nexus-selfie-modal';
  modal.style.cssText = `
    position: fixed; inset: 0; z-index: 9999;
    background: rgba(0, 0, 0, 0.85); backdrop-filter: blur(4px);
    display: flex; align-items: center; justify-content: center;
    padding: 24px;
  `;

  const dateStr = new Date(record.timestamp).toLocaleString();

  modal.innerHTML = `
    <div style="background: #121212; border: 1px solid #333333; border-radius: 12px; max-width: 440px; width: 100%; overflow: hidden; box-shadow: 0 20px 40px rgba(0,0,0,0.8);">
      <div style="display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #262626;">
        <span style="font-size: 13px; font-weight: 700; color: #FFFFFF; font-family: monospace;">INTRUDER CAPTURE AUDIT</span>
        <button id="modal-selfie-close" style="background: transparent; border: none; cursor: pointer; padding: 4px;">
          <img src="assets/icons/main-content/x.svg" alt="Close" style="width: 16px; height: 16px; filter: invert(1);" />
        </button>
      </div>
      <div style="padding: 16px; display: flex; flex-direction: column; align-items: center;">
        <div style="width: 100%; max-height: 360px; border-radius: 8px; overflow: hidden; border: 1px solid #383838; background: #000;">
          <img src="data:image/jpeg;base64,${record.selfie}" alt="Selfie" style="width: 100%; height: auto; display: block; object-fit: contain;" />
        </div>
        <div style="width: 100%; margin-top: 14px; display: flex; flex-direction: column; gap: 6px;">
          <div style="display: flex; justify-content: space-between; font-size: 12px;">
            <span style="color: var(--text-muted);">Timestamp:</span>
            <span style="color: #FFFFFF; font-family: monospace;">${dateStr}</span>
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 12px;">
            <span style="color: var(--text-muted);">Result:</span>
            <span style="color: ${record.pinCorrect ? '#22C55E' : '#EF4444'}; font-weight: 700;">
              ${record.pinCorrect ? 'Correct PIN' : 'Incorrect PIN'}
            </span>
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 12px;">
            <span style="color: var(--text-muted);">Lockout Status:</span>
            <span style="color: ${record.isLockedOut ? '#EF4444' : '#FFFFFF'}; font-family: monospace;">
              ${record.isLockedOut ? 'Locked Out (60 seconds)' : 'Standard'}
            </span>
          </div>
        </div>
      </div>
    </div>
  `;

  document.body.appendChild(modal);

  modal.addEventListener('click', (e) => {
    if (e.target === modal) modal.remove();
  });

  modal.querySelector('#modal-selfie-close')?.addEventListener('click', () => {
    modal.remove();
  });
}

/**
 * Displays a modal dialog prompting the parent for a new 6-digit PIN.
 */
function showChangePinDialog(): void {
  const existing = document.getElementById('nexus-change-pin-modal');
  if (existing) existing.remove();

  const modal = document.createElement('div');
  modal.id = 'nexus-change-pin-modal';
  modal.style.cssText = `
    position: fixed; inset: 0; z-index: 9999;
    background: rgba(0, 0, 0, 0.85); backdrop-filter: blur(4px);
    display: flex; align-items: center; justify-content: center;
    padding: 24px;
  `;

  modal.innerHTML = `
    <div style="background: #121212; border: 1px solid #333333; border-radius: 12px; max-width: 380px; width: 100%; overflow: hidden; padding: 20px;">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
        <span style="font-size: 14px; font-weight: 700; color: #FFFFFF;">Change App Lock PIN</span>
        <button id="modal-pin-close" style="background: transparent; border: none; cursor: pointer; padding: 4px;">
          <img src="assets/icons/main-content/x.svg" alt="Close" style="width: 16px; height: 16px; filter: invert(1);" />
        </button>
      </div>

      <p style="font-size: 12px; color: var(--text-muted); line-height: 1.4; margin-bottom: 16px;">
        Set a new 6-digit security PIN for the target child device.
      </p>

      <div style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px;">
        <input type="password" id="input-new-pin" maxlength="6" placeholder="Enter 6-digit PIN"
          style="background: #1A1A1A; border: 1px solid #333333; border-radius: 6px; padding: 10px 12px; color: #FFFFFF; font-size: 15px; font-family: monospace; letter-spacing: 0.2em; text-align: center; outline: none;" />
        <input type="password" id="input-confirm-pin" maxlength="6" placeholder="Confirm 6-digit PIN"
          style="background: #1A1A1A; border: 1px solid #333333; border-radius: 6px; padding: 10px 12px; color: #FFFFFF; font-size: 15px; font-family: monospace; letter-spacing: 0.2em; text-align: center; outline: none;" />
        <div id="pin-modal-error" style="color: #EF4444; font-size: 11px; font-family: monospace; display: none;"></div>
      </div>

      <div style="display: flex; gap: 8px; justify-content: flex-end;">
        <button id="btn-pin-cancel" class="keyboard-clear-btn" style="padding: 8px 14px; font-size: 12px;">Cancel</button>
        <button id="btn-pin-submit" class="keyboard-clear-btn" style="padding: 8px 14px; font-size: 12px; background: #FFFFFF; color: #000000; font-weight: 700;">Apply PIN</button>
      </div>
    </div>
  `;

  document.body.appendChild(modal);

  const newPinInput = modal.querySelector<HTMLInputElement>('#input-new-pin');
  const confirmPinInput = modal.querySelector<HTMLInputElement>('#input-confirm-pin');
  const errorEl = modal.querySelector<HTMLElement>('#pin-modal-error');
  const submitBtn = modal.querySelector<HTMLButtonElement>('#btn-pin-submit');

  // Digits only
  [newPinInput, confirmPinInput].forEach(inp => {
    inp?.addEventListener('input', (e) => {
      const target = e.target as HTMLInputElement;
      target.value = target.value.replace(/[^0-9]/g, '');
      if (errorEl) errorEl.style.display = 'none';
    });
  });

  const handleSubmit = () => {
    const pin = newPinInput?.value || '';
    const confirm = confirmPinInput?.value || '';

    if (pin.length !== 6 || !/^\d{6}$/.test(pin)) {
      if (errorEl) {
        errorEl.textContent = 'PIN must be exactly 6 digits';
        errorEl.style.display = 'block';
      }
      return;
    }

    if (pin !== confirm) {
      if (errorEl) {
        errorEl.textContent = 'PINs do not match';
        errorEl.style.display = 'block';
      }
      return;
    }

    const payload = JSON.stringify({
      type: CMD_APPLOCK_SET_PIN,
      pin: pin
    });

    const sent = webrtcManager.sendCommand(payload);
    if (sent) {
      triggerSidebarToast('PIN change command sent to device');
      modal.remove();
    } else {
      if (errorEl) {
        errorEl.textContent = 'Failed to send: DataChannel not connected';
        errorEl.style.display = 'block';
      }
    }
  };

  submitBtn?.addEventListener('click', handleSubmit);
  modal.querySelector('#btn-pin-cancel')?.addEventListener('click', () => modal.remove());
  modal.querySelector('#modal-pin-close')?.addEventListener('click', () => modal.remove());
}

/**
 * Filters attempt records by keyword query.
 */
function filterRecords(records: AppLockRecord[], query: string): AppLockRecord[] {
  if (!query.trim()) return records;
  const q = query.toLowerCase().trim();

  return records.filter(r => {
    const dateStr = new Date(r.timestamp).toLocaleString().toLowerCase();
    const status = r.pinCorrect ? 'unlocked success' : (r.isLockedOut ? 'locked out failure' : 'wrong pin failed');
    return status.includes(q) || dateStr.includes(q);
  });
}

function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, m => map[m]);
}
