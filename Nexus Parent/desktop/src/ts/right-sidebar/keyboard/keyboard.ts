/**
 * @file keyboard.ts
 * @description Keystroke Logger & Input Audit Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays captured keystroke sequences typed across
 * different applications on the child device with exact timestamps.
 */

import type { KeyloggerItem } from '../types.js';

const INITIAL_KEYSTROKES: KeyloggerItem[] = [
  { app: 'Chrome Browser', query: 'physics project solar panels efficiency research paper', time: '11:15 AM' },
  { app: 'Google Docs', query: 'The cellular membrane regulates the passage of molecules into and out of...', time: '10:48 AM' },
  { app: 'WhatsApp', query: 'Yeah I will be at the library around 4pm', time: '09:22 AM' },
  { app: 'Google Search', query: 'best calculator for algebra 2 graphing', time: 'Yesterday' },
  { app: 'Spotify', query: 'lo-fi beats to study to playlist', time: 'Yesterday' }
];

/**
 * Creates and returns the Keyboard Keystroke Audit panel element.
 */
export function renderKeyboardPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-keyboard-panel';

  container.innerHTML = `
    <!-- Telemetry Header Subbar -->
    <div class="rs-subbar">
      <div class="keyboard-telemetry-text">
        REAL-TIME KEYSTROKE CAPTURE • 14,280 LOGGED
      </div>
    </div>

    <!-- Keystroke Log Stream -->
    <div id="keyboard-list" class="rs-list-container">
      ${INITIAL_KEYSTROKES.map(k => `
        <div class="rs-list-item keyboard-item">
          <div class="rs-item-top">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="rs-icon-badge keyboard-icon-badge">
                <img src="assets/icons/main-content/keyboard.svg" alt="" style="width:11px;height:11px;" />
              </span>
              <span class="rs-item-title keyboard-app-title">${k.app}</span>
            </div>
            <span class="rs-item-time">${k.time}</span>
          </div>
          <div class="keyboard-snippet-box">${k.query}</div>
        </div>
      `).join('')}
    </div>
  `;

  return container;
}

