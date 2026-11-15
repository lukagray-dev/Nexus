/**
 * @file main-content.ts
 * @description Master router and coordinator for the full-viewport Main Content Area.
 *
 * Hey friend! This module coordinates the main content views:
 * - When disconnected: Renders Phase 1 Pairing Screen (empty-state).
 * - When connected with no feature selected: Renders Phase 2 Connected Dashboard (connection-state).
 * - When a feature is clicked (Camera, Screen, Location, Storage): Displays that view full-screen!
 * - Persists the floating draggable Mic panel seamlessly on top of ANY view without re-rendering the view!
 * - Optimized lifecycle: Collapsing sidebar sections, searching, or toggling mic panel NEVER resets or refreshes the active view!
 */

import { sidebarState } from '../left-sidebar/state.js';
import { deviceState } from '../shared/device-state.js';
import { renderPairingScreen } from './empty-state/empty-state.js';
import { renderConnectedEmptyState } from './connection-state/connection-state.js';
import { renderCameraView } from './camera/camera.js';
import { renderScreenView } from './screen/screen.js';
import { renderLocationView } from './location/location.js';
import { renderStorageView } from './storage/storage.js';
import { renderMicView, syncMicOverlay } from './mic/mic.js';

// Cache active view state so we only re-render the view when the selected feature actually changes
let currentActiveFeatureId: string | null | undefined = undefined;
let currentConnectedStatus: boolean | undefined = undefined;
let currentViewElement: HTMLElement | null = null;

/**
 * Initializes the main content area router.
 */
export function initMainContent(): void {
  const contentPane = document.getElementById('content-pane');
  if (!contentPane) return;

  // Render initial view
  updateContentView();

  // Listen to sidebar state changes: only change view if activeFeatureId changed!
  sidebarState.subscribe(() => {
    const nextFeatureId = sidebarState.getActiveFeatureId();
    if (nextFeatureId !== currentActiveFeatureId) {
      updateContentView();
    } else {
      // Feature didn't change: just sync the floating mic overlay without touching the active view DOM!
      syncMicOverlay(contentPane);
    }
  });

  // Listen to device connection changes: only re-render if in empty state and connection status changed
  deviceState.subscribe(() => {
    if (!sidebarState.getActiveFeatureId()) {
      const isConnected = deviceState.isConnected();
      if (isConnected !== currentConnectedStatus) {
        updateContentView(true);
      }
    }
  });
}

/**
 * Updates the content pane to display the active feature view or empty state.
 *
 * @param force If true, forces a complete re-render even if activeFeatureId hasn't changed
 */
export function updateContentView(force = false): void {
  const contentPane = document.getElementById('content-pane');
  if (!contentPane) return;

  const activeId = sidebarState.getActiveFeatureId();
  const isConnected = deviceState.isConnected();

  // Check if underlying view actually changed
  const needsViewUpdate =
    force ||
    activeId !== currentActiveFeatureId ||
    (!activeId && isConnected !== currentConnectedStatus);

  if (needsViewUpdate) {
    currentActiveFeatureId = activeId;
    currentConnectedStatus = isConnected;

    // Remove existing view element cleanly while preserving the floating mic panel if attached
    if (currentViewElement && currentViewElement.parentElement === contentPane) {
      contentPane.removeChild(currentViewElement);
    } else {
      // Remove any children that are not the floating mic panel
      const existingChildren = Array.from(contentPane.children);
      for (const child of existingChildren) {
        if (child.id !== 'mic-floating-panel') {
          contentPane.removeChild(child);
        }
      }
    }

    let viewElement: HTMLElement;

    if (!activeId) {
      // Render Empty State (Phase 1 Pairing or Phase 2 Connected Dashboard)
      viewElement = renderEmptyStateContainer();
    } else {
      switch (activeId) {
        case 'camera':
          viewElement = renderCameraView();
          break;
        case 'mic':
          viewElement = renderMicView();
          break;
        case 'screen':
          viewElement = renderScreenView();
          break;
        case 'location':
          viewElement = renderLocationView();
          break;
        case 'storage':
        case 'files':
          viewElement = renderStorageView();
          break;
        default:
          viewElement = renderStagedPlaceholder(activeId || 'unknown');
          break;
      }
    }

    currentViewElement = viewElement;

    // Insert view behind mic panel if mic panel is present, or append
    const micPanel = document.getElementById('mic-floating-panel');
    if (micPanel && micPanel.parentElement === contentPane) {
      contentPane.insertBefore(viewElement, micPanel);
    } else {
      contentPane.appendChild(viewElement);
    }
  }

  // Always ensure the floating mic panel overlay matches sidebarState.getIsMicOpen()
  syncMicOverlay(contentPane);
}

/**
 * Renders the dual-phase empty state container.
 */
function renderEmptyStateContainer(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'empty-state-viewport';

  const renderActiveSubView = (): void => {
    container.innerHTML = '';
    const isConnected = deviceState.isConnected();

    if (!isConnected) {
      container.appendChild(
        renderPairingScreen(() => {
          renderActiveSubView();
        })
      );
    } else {
      const device = deviceState.getConnectedDevice() || {
        id: 'NX84-2881-2340',
        name: 'Pixel 8 Pro',
        model: 'Google Pixel 8 Pro (Android 14)',
        batteryPercent: 88,
        signalStatus: '5G • Excellent',
        isOnline: true,
        lastSeen: 'Just now'
      };

      container.appendChild(
        renderConnectedEmptyState(device, () => {
          sidebarState.setActiveFeatureId(null);
          renderActiveSubView();
        })
      );
    }
  };

  renderActiveSubView();
  return container;
}

/**
 * Fallback view for features whose full-page views are staged.
 */
function renderStagedPlaceholder(featureId: string): HTMLElement {
  const container = document.createElement('div');
  container.className = 'view-body';

  const formattedName = featureId.charAt(0).toUpperCase() + featureId.slice(1);

  container.innerHTML = `
    <div style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; padding:32px; color:var(--text-muted); background-color:#000000; height:100%;">
      <h2 style="font-size:18px; font-weight:600; color:#ffffff; margin-bottom:8px;">${formattedName} Monitoring View</h2>
      <p style="font-size:13px; max-width:420px; line-height:1.5;">This monitoring view is synchronized and waiting for live device data stream.</p>
    </div>
  `;

  return container;
}
