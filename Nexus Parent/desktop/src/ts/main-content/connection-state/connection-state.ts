/**
 * @file connection-state.ts
 * @description Connected Information Dashboard (Phase 2 of Empty State) for Nexus Parent.
 *
 * Hey friend! This module renders the empty state when a child phone is actively paired
 * and connected, but no specific feature has been selected from the left sidebar yet.
 * - Live connected device telemetry bar (model, ID, battery %, network signal).
 * - Working "Disconnect" button wired to deviceState.
 * - Quick-launch cards matching each sidebar capability for 1-click navigation.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { rightSidebarState, type RightSidebarPanelId } from '../../right-sidebar/state.js';
import { deviceState, type DeviceInfo } from '../../shared/device-state.js';

/**
 * Renders the Phase 2 Connected Information Dashboard.
 *
 * @param device The active connected device information
 * @param onDisconnected Callback invoked when device is disconnected
 * @returns The container HTMLElement
 */
export function renderConnectedEmptyState(device: DeviceInfo, onDisconnected: () => void): HTMLElement {
  const container = document.createElement('div');
  container.className = 'empty-info-dashboard';

  container.innerHTML = `
    <!-- Top Connected Device Status Bar -->
    <div class="connected-device-bar">
      <div class="device-bar-left">
        <div class="device-avatar">
          <img src="assets/icons/main-content/connection-state/smartphone.svg" alt="" />
        </div>
        <div class="device-info-text">
          <div class="device-name-row">
            <span class="device-name">${device.name}</span>
            <span class="device-status-badge">
              <span class="status-pulse-dot active"></span>
              ONLINE
            </span>
          </div>
          <div class="device-meta-row">
            <span class="device-model">${device.model}</span>
            <span class="meta-dot">•</span>
            <span class="device-id mono">${device.id}</span>
          </div>
        </div>
      </div>

      <div class="device-bar-right">
        <div class="device-telemetry-chip" title="Device Battery Level">
          <img src="assets/icons/main-content/connection-state/battery-full.svg" alt="" />
          <span>${device.batteryPercent}%</span>
        </div>
        <div class="device-telemetry-chip" title="Network Signal Strength">
          <img src="assets/icons/main-content/connection-state/wifi.svg" alt="" />
          <span>${device.signalStatus}</span>
        </div>
        <button id="btn-disconnect-device" class="btn-disconnect" title="Disconnect this child device">
          <img src="assets/icons/main-content/connection-state/unlink.svg" alt="" />
          <span>Disconnect</span>
        </button>
      </div>
    </div>

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
            <p class="feature-card-desc">View real-time Android screen activity with zero latency.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- Location Card -->
        <div class="empty-feature-card" data-feature="location">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/map-pin.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">Live GPS Location</span>
              <span class="feature-badge">RADAR</span>
            </div>
            <p class="feature-card-desc">Pinpoint real-time coordinates, speed, and geofence status.</p>
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
              <span class="feature-card-title">Storage & Files</span>
              <span class="feature-badge">EXPLORER</span>
            </div>
            <p class="feature-card-desc">Browse local downloads, photos, documents, and APKs.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- SMS Card -->
        <div class="empty-feature-card" data-feature="sms">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/message-square.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">SMS Messages</span>
              <span class="feature-badge">SYNC</span>
            </div>
            <p class="feature-card-desc">Read inbound and outbound text conversations and timestamps.</p>
          </div>
          <div class="feature-card-arrow">
            <img src="assets/icons/main-content/arrow-right.svg" alt="" />
          </div>
        </div>

        <!-- App Lock Card -->
        <div class="empty-feature-card" data-feature="applock">
          <div class="feature-card-icon">
            <img src="assets/icons/left-sidebar/lock.svg" alt="" />
          </div>
          <div class="feature-card-content">
            <div class="feature-card-header-row">
              <span class="feature-card-title">App Lock Guard</span>
              <span class="feature-badge">GUARD</span>
            </div>
            <p class="feature-card-desc">Remotely restrict access to installed apps with PIN lock.</p>
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

  // Attach event listeners
  const disconnectBtn = container.querySelector('#btn-disconnect-device');
  disconnectBtn?.addEventListener('click', () => {
    deviceState.disconnect();
    onDisconnected();
  });

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
