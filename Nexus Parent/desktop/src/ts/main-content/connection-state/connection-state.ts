/**
 * @file connection-state.ts
 * @description Connected Information Dashboard (Phase 2 of Empty State) for Nexus Parent.
 *
 * Hey friend! This module renders the central monitoring dashboard when a child phone is actively
 * paired and connected, and no specific feature has been selected from the left sidebar yet.
 * - Displays brand logo and encrypted connection status.
 * - Displays instructions to select any surveillance/monitoring module from the left sidebar.
 * - Footer shortcut tip for toggling the sidebar with Ctrl+B.
 */

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
        <img src="assets/logo/nexus-parent.svg" alt="Nexus Parent Logo" />
      </div>
      <h2 class="empty-hero-title">Device Connected & Monitoring Ready</h2>
      <p class="empty-hero-desc">
        Your child's device is actively synchronized over encrypted WebRTC. Select any feature from the left sidebar to start live monitoring, stream video, inspect logs, or control device features.
      </p>

      <!-- Footer Tip -->
      <div class="empty-hero-footer">
        <img src="assets/icons/main-content/empty-state/info.svg" alt="" />
        <span>Tip: Press <strong>Ctrl+B</strong> to toggle the left feature navigation sidebar at any time.</span>
      </div>
    </div>
  `;

  return container;
}
