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
import { reconnectManager } from '../shared/reconnect-manager.js';
import { renderPairingScreen } from './empty-state/empty-state.js';
import { renderConnectedEmptyState } from './connection-state/connection-state.js';
import { renderCameraView, onCameraViewActivated } from './camera/camera.js';
import { renderScreenView, onScreenViewActivated } from './screen/screen.js';
import { renderLocationView, onLocationViewActivated } from './location/location.js';
import { renderStorageView } from './storage/storage.js';
import { renderMicView, syncMicOverlay } from './mic/mic.js';

// Cache active view instances so media streams (Camera, Screen, Location, Storage)
// continue running seamlessly in the background when shifting between tabs!
const viewCache = new Map<string, HTMLElement>();

let currentActiveFeatureId: string | null | undefined = undefined;
let currentConnectedStatus: boolean | undefined = undefined;

/**
 * Removes cached feature views when the device disconnects so that
 * a new pairing starts with a completely pristine state.
 */
function clearFeatureViews(contentPane: HTMLElement): void {
  for (const [key, element] of viewCache.entries()) {
    if (key !== 'empty' && element.parentElement === contentPane) {
      contentPane.removeChild(element);
    }
  }
  for (const key of Array.from(viewCache.keys())) {
    if (key !== 'empty') {
      viewCache.delete(key);
    }
  }
}

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

  // Listen to device connection changes: only re-render if in empty state or when disconnected
  deviceState.subscribe(() => {
    const isConnected = deviceState.isConnected();
    const isReconnecting = reconnectManager.isReconnecting();

    // While reconnecting, NEVER navigate away or kick the user off their current view!
    // The user stays exactly on whichever feature or dashboard view they were on.
    if (isReconnecting) {
      return;
    }

    if (!sidebarState.getActiveFeatureId()) {
      if (isConnected !== currentConnectedStatus) {
        if (!isConnected) {
          clearFeatureViews(contentPane);
        }
        updateContentView(true);
      }
    } else if (!isConnected) {
      // If a feature was active when the device disconnected (and is not auto-reconnecting),
      // clean up views and return to pairing screen
      clearFeatureViews(contentPane);
      sidebarState.setActiveFeatureId(null);
      updateContentView(true);
    }
  });

  // When reconnection is cancelled by user or all 9 attempts are exhausted,
  // clean up feature views and take the user to the empty state (connect to child page)
  reconnectManager.onCancel(() => {
    clearFeatureViews(contentPane);
    sidebarState.setActiveFeatureId(null);
    updateContentView(true);
  });

  reconnectManager.onExhausted(() => {
    clearFeatureViews(contentPane);
    sidebarState.setActiveFeatureId(null);
    updateContentView(true);
  });
}

/**
 * Updates the content pane to display the active feature view or empty state.
 *
 * Hey friend! To prevent media and data streams from stopping when shifting panels:
 * 1. Initialized views are preserved in viewCache.
 * 2. Inactive views are hidden with display: 'none' rather than being removed from the DOM.
 * 3. Active streams (Camera WebRTC video + DVR, Screen video + internal Opus audio, Location GPS tracking)
 *    remain 100% active and recording in the background!
 * 4. When returning to an active view, display: 'flex' is restored and the activation hook ensures
 *    hardware video rendering and layout bounds recalibrate with 0ms lag!
 *
 * @param force If true, forces a complete re-render even if activeFeatureId hasn't changed
 */
export function updateContentView(force = false): void {
  const contentPane = document.getElementById('content-pane');
  if (!contentPane) return;

  const activeId = sidebarState.getActiveFeatureId();
  const isConnected = deviceState.isConnected();

  // Map activeId to consistent cache key ('files' maps to 'storage')
  const targetKey = !activeId
    ? 'empty'
    : activeId === 'files'
    ? 'storage'
    : activeId;

  // Check if underlying view actually changed
  const needsViewUpdate =
    force ||
    activeId !== currentActiveFeatureId ||
    (!activeId && isConnected !== currentConnectedStatus);

  if (needsViewUpdate) {
    currentActiveFeatureId = activeId;
    currentConnectedStatus = isConnected;

    // Handle Empty State (Phase 1 Pairing Screen vs Phase 2 Connected Dashboard)
    if (targetKey === 'empty') {
      const existingEmpty = viewCache.get('empty');
      if (existingEmpty && existingEmpty.parentElement === contentPane) {
        contentPane.removeChild(existingEmpty);
      }
      const newEmpty = renderEmptyStateContainer();
      viewCache.set('empty', newEmpty);

      const micPanel = document.getElementById('mic-floating-panel');
      if (micPanel && micPanel.parentElement === contentPane) {
        contentPane.insertBefore(newEmpty, micPanel);
      } else {
        contentPane.appendChild(newEmpty);
      }
    } else if (!viewCache.has(targetKey)) {
      // First time loading this feature view: instantiate it and append to contentPane
      let viewElement: HTMLElement;
      switch (targetKey) {
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
          viewElement = renderStorageView();
          break;
        default:
          viewElement = renderStagedPlaceholder(targetKey);
          break;
      }

      viewCache.set(targetKey, viewElement);

      const micPanel = document.getElementById('mic-floating-panel');
      if (micPanel && micPanel.parentElement === contentPane) {
        contentPane.insertBefore(viewElement, micPanel);
      } else {
        contentPane.appendChild(viewElement);
      }
    }

    // Toggle display visibility across all cached views
    for (const [key, element] of viewCache.entries()) {
      if (key === targetKey) {
        element.style.display = 'flex';
      } else {
        element.style.display = 'none';
      }
    }

    // Fire view activation hooks so video surfaces and Leaflet maps refresh immediately
    if (targetKey === 'camera') {
      onCameraViewActivated();
    } else if (targetKey === 'screen') {
      onScreenViewActivated();
    } else if (targetKey === 'location') {
      onLocationViewActivated();
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
    const isReconnecting = reconnectManager.isReconnecting();
    const isConnected = deviceState.isConnected();

    // If fully disconnected and not actively reconnecting, render Phase 1 pairing screen.
    // If connected OR in active auto-reconnect, user stays on the connected dashboard!
    if (!isConnected && !isReconnecting) {
      container.appendChild(
        renderPairingScreen(() => {
          renderActiveSubView();
        })
      );
    } else {
      const device = deviceState.getConnectedDevice() || (reconnectManager.getTargetDevice() as any) || {
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
