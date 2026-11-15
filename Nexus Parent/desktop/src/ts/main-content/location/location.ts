/**
 * @file location.ts
 * @description Location Tracking View component for Nexus Parent desktop main content area.
 *
 * Hey friend! This renders the full-viewport GPS Tracking display with tactical radar grid,
 * pulsing target pinpoint, telemetry card, and map controls.
 * - Starts in PAUSED state by default until the user explicitly starts live tracking.
 * - Communicates with sidebarState so 'Monitor Location' displays the LIVE badge only when actively tracking.
 */

import { sidebarState } from '../../left-sidebar/state.js';

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

    <!-- Map Canvas Area -->
    <div class="map-canvas-area">
      <!-- Radar Rings and Animation Sweep (starts paused) -->
      <div class="radar-ring ring-1"></div>
      <div class="radar-ring ring-2"></div>
      <div class="radar-ring ring-3"></div>
      <div id="radar-sweep" class="radar-sweep" style="animation-play-state: paused;"></div>

      <!-- Center GPS Target Marker -->
      <div class="gps-target-marker">
        <div class="gps-pulse-circle"></div>
        <div class="gps-target-label">
          <span>Child Phone (Active)</span>
        </div>
      </div>

      <!-- Telemetry Info Card -->
      <div class="location-telemetry-card">
        <div class="telemetry-row">
          <span class="telemetry-label">Tracking Status:</span>
          <span id="location-telemetry-status" class="telemetry-value">Paused</span>
        </div>
        <div class="telemetry-row">
          <span class="telemetry-label">Latitude:</span>
          <span class="telemetry-value">37.774929° N</span>
        </div>
        <div class="telemetry-row">
          <span class="telemetry-label">Longitude:</span>
          <span class="telemetry-value">-122.419416° W</span>
        </div>
        <div class="telemetry-row">
          <span class="telemetry-label">Approx Address:</span>
          <span class="telemetry-value">742 Evergreen Terrace, Springfield</span>
        </div>
        <div class="telemetry-row">
          <span class="telemetry-label">Velocity / Speed:</span>
          <span class="telemetry-value">0.0 km/h (Stationary)</span>
        </div>
        <div class="telemetry-row">
          <span class="telemetry-label">Last Position Ping:</span>
          <span class="telemetry-value">4 seconds ago</span>
        </div>
      </div>

      <!-- Floating Bottom Controls -->
      <div class="floating-controls">
        <!-- Start / Pause Live Tracking button (starts paused) -->
        <button id="btn-location-play-pause" class="control-pill-btn" title="Start Live GPS Tracking">
          <img id="location-play-icon" src="assets/icons/main-content/play.svg" alt="" style="width: 12px; height: 12px; filter: invert(1);" />
          <span id="location-play-label">Start Live Tracking</span>
        </button>
        <div class="control-separator"></div>

        <button id="btn-location-center" class="control-pill-btn" title="Center map on target">
          <span>Center Target</span>
        </button>
        <div class="control-separator"></div>

        <button id="btn-location-history" class="control-pill-btn" title="Show past 24h path history">
          <span>24h Route History</span>
        </button>
        <div class="control-separator"></div>

        <button id="btn-location-geofence" class="control-pill-btn" title="Configure Geofences">
          <span>Manage Safe Zones</span>
        </button>
        <div class="control-separator"></div>

        <button id="btn-location-zoom-in" class="control-pill-btn" title="Zoom In">
          <span>Zoom +</span>
        </button>
        <button id="btn-location-zoom-out" class="control-pill-btn" title="Zoom Out">
          <span>Zoom -</span>
        </button>
      </div>
    </div>
  `;

  // Wire interactive controls
  const playBtn = container.querySelector<HTMLButtonElement>('#btn-location-play-pause');
  const playIcon = container.querySelector<HTMLImageElement>('#location-play-icon');
  const playLabel = container.querySelector<HTMLElement>('#location-play-label');
  const telemetryStatus = container.querySelector<HTMLElement>('#location-telemetry-status');
  const radarSweep = container.querySelector<HTMLElement>('#radar-sweep');

  let isLive = false;

  playBtn?.addEventListener('click', () => {
    isLive = !isLive;
    if (isLive) {
      playBtn.classList.add('active');
      playBtn.setAttribute('title', 'Pause Live GPS Tracking');
      if (playIcon) playIcon.src = 'assets/icons/main-content/pause.svg';
      if (playLabel) playLabel.textContent = 'Pause Tracking';
      if (telemetryStatus) telemetryStatus.textContent = 'Live Tracking';
      if (radarSweep) radarSweep.style.animationPlayState = 'running';
      sidebarState.setFeatureLive('location', true);
    } else {
      playBtn.classList.remove('active');
      playBtn.setAttribute('title', 'Start Live GPS Tracking');
      if (playIcon) playIcon.src = 'assets/icons/main-content/play.svg';
      if (playLabel) playLabel.textContent = 'Start Live Tracking';
      if (telemetryStatus) telemetryStatus.textContent = 'Paused';
      if (radarSweep) radarSweep.style.animationPlayState = 'paused';
      sidebarState.setFeatureLive('location', false);
    }
  });

  const centerBtn = container.querySelector('#btn-location-center');
  centerBtn?.addEventListener('click', () => {
    console.log('[Location] Centering map target');
  });

  // Disconnect / cleanup on element removal
  const observer = new MutationObserver(() => {
    if (!document.body.contains(container)) {
      sidebarState.setFeatureLive('location', false);
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  return container;
}
