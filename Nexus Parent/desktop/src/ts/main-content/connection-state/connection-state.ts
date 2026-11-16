/**
 * @file connection-state.ts
 * @description Connected Information Dashboard (Phase 2 of Empty State) for Nexus Parent.
 *
 * Hey friend! This module renders the central monitoring dashboard when a child phone is actively
 * paired and connected, and no specific feature has been selected from the left sidebar yet.
 * - Central Hero Information Section with verified monitoring status.
 * - Quick-launch cards matching each sidebar capability for 1-click navigation.
 * Note: Hardware telemetry (battery, storage, OS, network) and the Disconnect action
 * are cleanly hosted in the Left Sidebar's device button dropdown to avoid UI duplication.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { rightSidebarState, type RightSidebarPanelId } from '../../right-sidebar/state.js';
import type { DeviceInfo } from '../../shared/device-state.js';

/**
 * Renders the Phase 2 Connected Information Dashboard.
 *
 * @param _device The active connected device information
 * @param _onDisconnected Callback invoked when device is disconnected
 * @returns The container HTMLElement
 */
export function renderConnectedEmptyState(_device?: DeviceInfo, _onDisconnected?: () => void): HTMLElement {
  const container = document.createElement('div');
  container.className = 'empty-info-dashboard';

  container.innerHTML = `
    <!-- Central Hero Information Section -->
    <div class="empty-hero-section">
      <div class="empty-hero-icon-ring">
        <img src="assets/icons/main-content/connection-state/shield-check.svg" alt="" />
      </div>
      <h2 class="empty-hero-title">Device Connected & Monitoring Ready</h2>
      <p class="empty-hero-desc">
        Your child's device is actively synchronized over encrypted WebRTC. Select any feature from the left sidebar to start live monitoring, stream video, inspect logs, or control device features.
      </p>

      <!-- Quick Feature Launch Grid matching Left Sidebar -->
      <div class="empty-feature-grid">
        <!-- Camera Card -->
        <div class="empty-feature-card" data-feature="camera">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/camera.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">Live Camera</span>
              <span class="feature-badge">LIVE</span>
            </div>
            <p class="feature-card-desc">Stream front or back camera in real-time with night mode.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Screen Mirror Card -->
        <div class="empty-feature-card" data-feature="screen">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/monitor.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">Screen Mirror</span>
              <span class="feature-badge">MIRROR</span>
            </div>
            <p class="feature-card-desc">Low-latency screen streaming with touch & audio capture.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Mic Card -->
        <div class="empty-feature-card" data-feature="mic">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/mic.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">Live Mic</span>
              <span class="feature-badge">AUDIO</span>
            </div>
            <p class="feature-card-desc">Surrounding ambient audio monitor with noise suppression.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Location Card -->
        <div class="empty-feature-card" data-feature="location">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/location.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">GPS Location</span>
              <span class="feature-badge">MAP</span>
            </div>
            <p class="feature-card-desc">Real-time GPS tracker with route history and geofencing.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- SMS Card -->
        <div class="empty-feature-card" data-feature="sms">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/sms.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">SMS Messages</span>
              <span class="feature-badge">COMMS</span>
            </div>
            <p class="feature-card-desc">Sync inbound and outbound SMS threads and attachments.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Call Log Card -->
        <div class="empty-feature-card" data-feature="calllog">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/phone.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">Call History</span>
              <span class="feature-badge">CALLS</span>
            </div>
            <p class="feature-card-desc">View dialed, received, missed calls with caller duration.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Notifications Card -->
        <div class="empty-feature-card" data-feature="notification">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/bell.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">App Notifications</span>
              <span class="feature-badge">ALERTS</span>
            </div>
            <p class="feature-card-desc">Live notification interception with package filtering.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Storage Card -->
        <div class="empty-feature-card" data-feature="storage">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/folder.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">File Explorer</span>
              <span class="feature-badge">FILES</span>
            </div>
            <p class="feature-card-desc">Browse internal storage, DCIM photos, documents, and downloads.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>
      </div>

      <!-- Footer Tip -->
      <div class="empty-hero-footer">
        <img src="assets/icons/main-content/empty-state/info.svg" alt="" />
        <span>Tip: Press <strong>Ctrl+B</strong> to toggle the left feature navigation sidebar at any time.</span>
      </div>
    </div>
  `;

  // Attach quick-launch card clicks
  const cards = container.querySelectorAll<HTMLElement>('.empty-feature-card');
  cards.forEach((card) => {
    card.addEventListener('click', () => {
      const feature = card.dataset.feature;
      if (!feature) return;

      const rightSidebarItems = new Set([
        'sms', 'calllog', 'notification', 'keyboard', 'whatsapp', 'telegram',
        'chatgpt', 'claude', 'grok', 'chats', 'gmail', 'facebook', 'instagram',
        'snapchat', 'wellbeing', 'applock', 'vibrateflash', 'wallpaper'
      ]);

      if (rightSidebarItems.has(feature)) {
        rightSidebarState.openPanel(feature as RightSidebarPanelId);
      } else {
        sidebarState.setActiveFeatureId(feature);
      }
    });
  });

  return container;
}
