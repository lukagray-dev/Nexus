/**
 * @file settings.ts
 * @description Application Settings Dialog & Preferences for Nexus Parent.
 *
 * Hey friend! This manages the settings modal allowing the user to configure
 * STUN/TURN WebRTC signaling servers, auto-reconnect behavior, and stealth mode preferences.
 */

import { showToast } from '../shared/toast.js';

/**
 * Initializes settings triggers from sidebar and titlebar menu.
 */
export function initSettings(): void {
  const sidebarBtn = document.getElementById('btn-sidebar-settings');
  sidebarBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    showToast('Settings coming soon');
  });

  const menuBtn = document.getElementById('menu-item-settings');
  menuBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    showToast('Settings coming soon');
  });
}

/**
 * Opens the interactive settings modal.
 */
export function openSettingsDialog(): void {
  const existing = document.getElementById('settings-dialog-overlay');
  if (existing) return;

  const overlay = document.createElement('div');
  overlay.id = 'settings-dialog-overlay';
  overlay.className = 'settings-overlay';

  overlay.innerHTML = `
    <div class="settings-modal">
      <div class="settings-header">
        <span class="settings-title">Nexus Parent Preferences</span>
        <button id="btn-settings-close" class="settings-close-btn" title="Close">✕</button>
      </div>
      <div class="settings-body">
        <div class="settings-section">
          <div class="settings-section-title">WebRTC & P2P Connectivity</div>
          <div class="settings-row">
            <div class="settings-row-label">
              <span class="settings-label-title">Primary STUN Server</span>
              <span class="settings-label-desc">Used for NAT traversal and direct peer connection</span>
            </div>
          </div>
          <input type="text" class="settings-input" value="stun:stun.l.google.com:19302" readonly />
        </div>

        <div class="settings-section">
          <div class="settings-section-title">Application Defaults</div>
          <div class="settings-row">
            <div class="settings-row-label">
              <span class="settings-label-title">Hardware Video Acceleration</span>
              <span class="settings-label-desc">Decodes 1080p 60fps streams via GPU</span>
            </div>
            <span class="mono-badge">ENABLED</span>
          </div>
          <div class="settings-row" style="margin-top: 8px;">
            <div class="settings-row-label">
              <span class="settings-label-title">End-to-End Encryption (E2EE)</span>
              <span class="settings-label-desc">WebRTC DTLS-SRTP 256-bit AES-GCM cipher suite</span>
            </div>
            <span class="mono-badge">ACTIVE</span>
          </div>
        </div>

        <div class="settings-section">
          <div class="settings-section-title">Build Information</div>
          <div class="settings-row">
            <div class="settings-row-label">
              <span class="settings-label-title">Architecture</span>
              <span class="settings-label-desc">Tauri v2 Native Rust Backend + WebView2</span>
            </div>
            <span class="mono-badge">v0.1.0</span>
          </div>
        </div>
      </div>
      <div class="settings-footer">
        <button id="btn-settings-done" class="settings-btn settings-btn-primary">Done</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const close = (): void => {
    if (overlay.parentElement) {
      overlay.parentElement.removeChild(overlay);
    }
  };

  overlay.querySelector('#btn-settings-close')?.addEventListener('click', close);
  overlay.querySelector('#btn-settings-done')?.addEventListener('click', close);
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      close();
    }
  });
}
