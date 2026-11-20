/**
 * @file location.ts
 * @description Location Tracking View controller for Nexus Parent desktop main content area.
 *
 * Hey friend! Here is how this component coordinates real-time GPS tracking:
 * 1. Full-Bleed Interactive Map:
 *    - Uses Leaflet embedded inside WebView2 to render smooth pan/zoom maps.
 *    - Defaults to high-resolution Satellite Imagery (Esri World Imagery + boundary labels)
 *      with a one-click toggle to CartoDB Dark Matter vector streets!
 * 2. Tactical Pulsing Target Marker:
 *    - Pinned to the child's exact physical coordinates with a pulsing radar ring and
 *      GPS accuracy halo circle showing signal precision.
 * 3. Command-and-Control Protocol over WebRTC DataChannel:
 *    - Clicking "Start Live Tracking" sends LOCATION_ON and flips the button to an animated loading spinner (loading.svg).
 *    - When the Android child confirms LOCATION_STARTED, the spinner flips to the active "Stop Tracking" button (pause.svg).
 *    - Clicking "Stop Tracking" sends LOCATION_OFF with a loading spinner; when confirmed by LOCATION_STOPPED, flips back to Start.
 * 4. Movement-Only Displacement Streaming:
 *    - The child phone only sends coordinates when it physically moves 5 meters or more.
 *    - Stationary phones do not spam packets, preserving child device battery!
 * 5. Dynamic Telemetry Card:
 *    - Displays physical address (reverse-geocoded via Nominatim), exact cardinal coordinates,
 *      accuracy margin, speed, and real-time relative ping timestamp.
 * 6. Disconnection Fail-Safe:
 *    - Automatically resets state to IDLE and powers off live badges if WebRTC drops.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import {
  type LocationTrackingState,
  type LocationTelemetryData,
  type LocationUpdatePayload,
  CMD_LOCATION_ON,
  CMD_LOCATION_OFF,
  RESP_LOCATION_STARTED,
  RESP_LOCATION_STOPPED,
  RESP_LOCATION_ERROR_PREFIX,
  TYPE_LOCATION_UPDATE,
  formatCoordinates,
  formatSpeed,
  formatAccuracy,
  formatRelativeTime
} from './types.js';
import { MapManager } from './map-manager.js';

// Module-level map manager instance so map state persists across feature switches
const globalLocationMapManager = new MapManager();

// Track current tracking state globally across view mounts
let globalTrackingState: LocationTrackingState = 'IDLE';
let globalLatestTelemetry: LocationTelemetryData | null = null;

/**
 * Hey friend! Leaflet calculates container pixel boundaries dynamically.
 * When the Location tab was hidden in the background (display: none), its pixel
 * dimensions were temporarily 0x0.
 *
 * Calling this hook upon tab activation triggers map.invalidateSize(), telling
 * Leaflet to immediately recalculate map tiles, center the camera, and render
 * all marker pins crisp and distortion-free!
 */
export function onLocationViewActivated(): void {
  requestAnimationFrame(() => {
    globalLocationMapManager.invalidateSize();
  });
}

/**
 * Creates and returns the full-area Location Tracking DOM element.
 */
export function renderLocationView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'location-container';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Location Tracking</span>
      </div>
    </div>

    <!-- Map Canvas Viewport Area -->
    <div class="map-canvas-area">
      <!-- Real Interactive Leaflet Map Surface -->
      <div id="location-map" class="location-map-surface"></div>

      <!-- Paused Stream Overlay (shown initially until tracking starts) -->
      <div id="location-paused-overlay" class="location-paused-overlay" style="${globalTrackingState === 'TRACKING' ? 'display: none;' : 'display: flex;'}">
        <div class="location-paused-pill">
          <img src="assets/icons/main-content/pause.svg" alt="" style="width: 13px; height: 13px; filter: invert(1);" />
          <span>Location Tracking Paused • Click Start to Begin</span>
        </div>
      </div>

      <!-- Floating Telemetry Card (Top Left) -->
      <div class="location-telemetry-card">
        <div class="telemetry-card-header">
          <div class="telemetry-status-pill">
            <span id="telemetry-status-dot" class="telemetry-dot ${globalTrackingState === 'TRACKING' ? 'is-active' : ''}"></span>
            <span id="location-telemetry-status" class="telemetry-status-text">
              ${globalTrackingState === 'TRACKING' ? 'Live Tracking' : 'Paused'}
            </span>
          </div>
          <span id="location-telemetry-ping" class="telemetry-ping-time">
            ${globalLatestTelemetry ? formatRelativeTime(globalLatestTelemetry.timestamp) : '--'}
          </span>
        </div>

        <div class="telemetry-body">
          <div class="telemetry-row primary-address">
            <span class="telemetry-label">Address:</span>
            <span id="location-telemetry-address" class="telemetry-value address-value">
              ${globalLatestTelemetry?.address || 'Waiting for position fix...'}
            </span>
          </div>
          <div class="telemetry-grid">
            <div class="telemetry-grid-item">
              <span class="telemetry-label">Coords:</span>
              <span id="location-telemetry-coords" class="telemetry-value">
                ${globalLatestTelemetry ? formatCoordinates(globalLatestTelemetry.latitude, globalLatestTelemetry.longitude) : '--'}
              </span>
            </div>
            <div class="telemetry-grid-item">
              <span class="telemetry-label">Accuracy:</span>
              <span id="location-telemetry-accuracy" class="telemetry-value">
                ${globalLatestTelemetry ? formatAccuracy(globalLatestTelemetry.accuracy) : '--'}
              </span>
            </div>
            <div class="telemetry-grid-item">
              <span class="telemetry-label">Velocity:</span>
              <span id="location-telemetry-speed" class="telemetry-value">
                ${globalLatestTelemetry ? formatSpeed(globalLatestTelemetry.speed) : '--'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Floating Bottom Tactical Controls -->
      <div class="floating-controls">
        <!-- Start / Stop Button with Loading Spinner -->
        <button id="btn-location-play-pause" class="control-pill-btn primary-action ${globalTrackingState === 'TRACKING' ? 'active' : ''}" title="Start Live GPS Tracking">
          <img id="location-play-icon" src="assets/icons/main-content/${globalTrackingState === 'TRACKING' ? 'pause.svg' : 'play.svg'}" alt="" style="width: 13px; height: 13px; filter: invert(1);" />
          <span id="location-play-label">${globalTrackingState === 'TRACKING' ? 'Stop Tracking' : 'Start Live Tracking'}</span>
        </button>

        <div class="control-separator"></div>

        <!-- Center Target Button -->
        <button id="btn-location-center" class="control-pill-btn" title="Center Map on Target">
          <img src="assets/icons/main-content/location/crosshair.svg" alt="" style="width: 13px; height: 13px; filter: invert(1);" />
          <span>Center Target</span>
        </button>

        <div class="control-separator"></div>

        <!-- Zoom Controls -->
        <button id="btn-location-zoom-in" class="control-pill-btn icon-only" title="Zoom In">
          <span>+</span>
        </button>
        <button id="btn-location-zoom-out" class="control-pill-btn icon-only" title="Zoom Out">
          <span>−</span>
        </button>
      </div>
    </div>
  `;

  // Query DOM elements
  const mapElement = container.querySelector<HTMLElement>('#location-map');
  const playPauseBtn = container.querySelector<HTMLButtonElement>('#btn-location-play-pause');
  const playIcon = container.querySelector<HTMLImageElement>('#location-play-icon');
  const playLabel = container.querySelector<HTMLElement>('#location-play-label');
  const pausedOverlay = container.querySelector<HTMLElement>('#location-paused-overlay');
  const telemetryStatus = container.querySelector<HTMLElement>('#location-telemetry-status');
  const telemetryStatusDot = container.querySelector<HTMLElement>('#telemetry-status-dot');
  const telemetryAddress = container.querySelector<HTMLElement>('#location-telemetry-address');
  const telemetryCoords = container.querySelector<HTMLElement>('#location-telemetry-coords');
  const telemetryAccuracy = container.querySelector<HTMLElement>('#location-telemetry-accuracy');
  const telemetrySpeed = container.querySelector<HTMLElement>('#location-telemetry-speed');
  const telemetryPing = container.querySelector<HTMLElement>('#location-telemetry-ping');
  const centerBtn = container.querySelector<HTMLButtonElement>('#btn-location-center');
  const zoomInBtn = container.querySelector<HTMLButtonElement>('#btn-location-zoom-in');
  const zoomOutBtn = container.querySelector<HTMLButtonElement>('#btn-location-zoom-out');

  let confirmationTimeout: ReturnType<typeof setTimeout> | null = null;
  let pingInterval: ReturnType<typeof setInterval> | null = null;

  /**
   * Updates Play/Pause button and controls bar based on tracking state.
   */
  function setTrackingState(state: LocationTrackingState): void {
    globalTrackingState = state;
    if (!playPauseBtn || !playIcon || !playLabel) return;

    switch (state) {
      case 'IDLE':
        playIcon.src = 'assets/icons/main-content/play.svg';
        playIcon.style.filter = 'invert(1)';
        playLabel.textContent = 'Start Live Tracking';
        playPauseBtn.classList.remove('is-loading', 'active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Start Live GPS Tracking');
        if (pausedOverlay) pausedOverlay.style.display = 'flex';
        if (telemetryStatus) telemetryStatus.textContent = 'Paused';
        if (telemetryStatusDot) telemetryStatusDot.classList.remove('is-active');
        sidebarState.setFeatureLive('location', false);
        break;

      case 'STARTING':
        playIcon.src = 'assets/icons/animations/loading.svg';
        playIcon.style.filter = 'none';
        playLabel.textContent = 'Starting GPS...';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.classList.remove('active');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Requesting location from child device...');
        if (telemetryStatus) telemetryStatus.textContent = 'Connecting...';
        break;

      case 'TRACKING':
        playIcon.src = 'assets/icons/main-content/pause.svg';
        playIcon.style.filter = 'invert(1)';
        playLabel.textContent = 'Stop Tracking';
        playPauseBtn.classList.remove('is-loading');
        playPauseBtn.classList.add('active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Stop GPS Tracking');
        if (pausedOverlay) pausedOverlay.style.display = 'none';
        if (telemetryStatus) telemetryStatus.textContent = 'Live Tracking';
        if (telemetryStatusDot) telemetryStatusDot.classList.add('is-active');
        sidebarState.setFeatureLive('location', true);
        break;

      case 'STOPPING':
        playIcon.src = 'assets/icons/animations/loading.svg';
        playIcon.style.filter = 'none';
        playLabel.textContent = 'Stopping GPS...';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Stopping location tracking...');
        if (telemetryStatus) telemetryStatus.textContent = 'Stopping...';
        break;
    }
  }

  /**
   * Updates telemetry card values on screen.
   */
  function updateTelemetryUi(data: LocationTelemetryData): void {
    if (telemetryCoords) {
      telemetryCoords.textContent = formatCoordinates(data.latitude, data.longitude);
    }
    if (telemetryAccuracy) {
      telemetryAccuracy.textContent = formatAccuracy(data.accuracy);
    }
    if (telemetrySpeed) {
      telemetrySpeed.textContent = formatSpeed(data.speed);
    }
    if (telemetryAddress && data.address) {
      telemetryAddress.textContent = data.address;
    }
    if (telemetryPing) {
      telemetryPing.textContent = formatRelativeTime(data.timestamp);
    }
  }

  // Initialize Leaflet map
  if (mapElement) {
    // Small timeout ensures container dimensions have resolved
    setTimeout(() => {
      globalLocationMapManager.initMap(mapElement, (resolvedAddress) => {
        if (telemetryAddress) {
          telemetryAddress.textContent = resolvedAddress;
        }
      });
      if (globalLatestTelemetry) {
        globalLocationMapManager.updateLocation(globalLatestTelemetry);
        updateTelemetryUi(globalLatestTelemetry);
      }
    }, 50);
  }

  // Update relative ping timer counter every 5 seconds
  pingInterval = setInterval(() => {
    if (globalLatestTelemetry && telemetryPing) {
      telemetryPing.textContent = formatRelativeTime(globalLatestTelemetry.timestamp);
    }
  }, 5000);

  // 1. Play / Pause Control Button
  playPauseBtn?.addEventListener('click', () => {
    if (globalTrackingState === 'IDLE') {
      console.log('[Location] Start clicked: Sending LOCATION_ON command...');
      setTrackingState('STARTING');

      const sent = webrtcManager.sendCommand(CMD_LOCATION_ON);
      if (!sent) {
        console.warn('[Location] Cannot send LOCATION_ON: DataChannel is not open.');
        setTrackingState('IDLE');
        if (telemetryStatus) telemetryStatus.textContent = 'DataChannel Closed';
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (globalTrackingState === 'STARTING') {
          console.warn('[Location] Timeout waiting for LOCATION_STARTED from Child. Reverting to IDLE.');
          setTrackingState('IDLE');
          if (telemetryStatus) telemetryStatus.textContent = 'Request Timeout';
        }
      }, 20000);
    } else if (globalTrackingState === 'TRACKING') {
      console.log('[Location] Stop clicked: Sending LOCATION_OFF command...');
      setTrackingState('STOPPING');

      const sent = webrtcManager.sendCommand(CMD_LOCATION_OFF);
      if (!sent) {
        console.warn('[Location] Cannot send LOCATION_OFF: DataChannel is not open.');
        setTrackingState('IDLE');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (globalTrackingState === 'STOPPING') {
          console.warn('[Location] Timeout waiting for LOCATION_STOPPED from Child. Reverting to IDLE.');
          setTrackingState('IDLE');
        }
      }, 20000);
    }
  });

  // 2. Center Target Button
  centerBtn?.addEventListener('click', () => {
    console.log('[Location] Centering map target...');
    globalLocationMapManager.recenter();
  });

  // 3. Zoom Controls
  zoomInBtn?.addEventListener('click', () => {
    globalLocationMapManager.zoomIn();
  });
  zoomOutBtn?.addEventListener('click', () => {
    globalLocationMapManager.zoomOut();
  });

  // 4. DataChannel Control & Telemetry Listener
  const unsubscribeMessage = webrtcManager.onMessage((rawMessage: string) => {
    const trimmed = rawMessage.trim();

    // Case 1: Location Started Confirmation
    if (trimmed === RESP_LOCATION_STARTED) {
      console.log('[Location] Child confirmed: LOCATION_STARTED');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setTrackingState('TRACKING');
      globalLocationMapManager.invalidateSize();
      return;
    }

    // Case 2: Location Stopped Confirmation
    if (trimmed === RESP_LOCATION_STOPPED) {
      console.log('[Location] Child confirmed: LOCATION_STOPPED');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setTrackingState('IDLE');
      return;
    }

    // Case 3: Error Message from Child
    if (trimmed.startsWith(RESP_LOCATION_ERROR_PREFIX)) {
      const errorDetail = trimmed.slice(RESP_LOCATION_ERROR_PREFIX.length);
      console.error('[Location] Error reported by Child:', errorDetail);
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setTrackingState('IDLE');
      if (telemetryStatus) {
        telemetryStatus.textContent = errorDetail;
      }
      return;
    }

    // Case 4: Incoming Telemetry JSON Packet
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      try {
        const payload: LocationUpdatePayload = JSON.parse(trimmed);
        if (payload.type === TYPE_LOCATION_UPDATE) {
          const lat = payload.latitude ?? payload.coords?.[0];
          const lng = payload.longitude ?? payload.coords?.[1];

          if (typeof lat === 'number' && typeof lng === 'number') {
            const telemetry: LocationTelemetryData = {
              latitude: lat,
              longitude: lng,
              accuracy: payload.accuracy ?? 10,
              altitude: payload.altitude ?? 0,
              speed: payload.speed ?? 0,
              bearing: payload.bearing ?? 0,
              timestamp: payload.timestamp ?? Date.now(),
              address: globalLatestTelemetry?.address
            };

            globalLatestTelemetry = telemetry;
            updateTelemetryUi(telemetry);

            globalLocationMapManager.updateLocation(telemetry, (resolvedAddr) => {
              if (telemetryAddress) {
                telemetryAddress.textContent = resolvedAddr;
              }
            });
          }
        }
      } catch {
        // Not a JSON location update packet; ignore
      }
    }
  });

  // 6. WebRTC Disconnection Fail-Safe
  const unsubscribeConnection = webrtcManager.onConnectionStateChange((state) => {
    if (state === 'disconnected' || state === 'failed' || state === 'closed') {
      console.warn(`[Location] WebRTC connection ${state}. Resetting tracking state to IDLE.`);
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setTrackingState('IDLE');
    }
  });

  // 7. Cleanup upon component unmount
  const observer = new MutationObserver(() => {
    if (!document.body.contains(container)) {
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      if (pingInterval) clearInterval(pingInterval);
      unsubscribeMessage();
      unsubscribeConnection();
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  return container;
}
