/**
 * @file camera.ts
 * @description Camera Feed View component for Nexus Parent main content area.
 *
 * Hey friend! This renders the full-viewport Camera Feed:
 * - Edge-to-edge video feed occupying the entire main content area.
 * - Starts in PAUSED state by default until user plays it.
 * - The 'Live' indicator appears only when the camera feed is actually live.
 * - Communicates with sidebarState so 'Monitor Camera' shows LIVE badge only while playing.
 * - Clean YouTube-style floating controls docked at the bottom.
 */

import { sidebarState } from '../../left-sidebar/state.js';

/**
 * Creates and returns the full-area Camera Feed DOM element.
 */
export function renderCameraView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'feed-page-container';

  container.innerHTML = `
    <!-- Clean Topbar Header without badges or refresh button -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Camera Feed</span>
      </div>
    </div>

    <!-- Full-area Edge-to-Edge Feed Canvas Area -->
    <div class="feed-viewport-area">
      <!-- Full-bleed Video Display Surface -->
      <div id="camera-feed-surface" class="feed-display-surface">
        <video id="camera-video-stream" class="feed-video-element" autoplay playsinline muted></video>
        <div class="feed-ambient-canvas"></div>

        <!-- Paused Stream Overlay (shown by default until user plays) -->
        <div id="camera-paused-overlay" class="feed-paused-overlay">
          <div class="feed-paused-pill">
            <img src="assets/icons/main-content/pause.svg" alt="" />
            <span>Feed Paused • Click Play to Start Stream</span>
          </div>
        </div>
      </div>

      <!-- Floating Bottom Player Bar -->
      <div class="feed-player-controls-bar">
        <!-- Seek / Progress Bar -->
        <div class="feed-progress-track" title="Live Stream Timeline">
          <div class="feed-progress-fill"></div>
          <div class="feed-progress-scrubber"></div>
        </div>

        <!-- Controls Row -->
        <div class="feed-controls-row">
          <!-- Left Controls: Play/Pause & Live Timestamp -->
          <div class="feed-controls-left">
            <!-- Play / Pause button starts with Play icon (paused by default) -->
            <button id="btn-camera-play-pause" class="feed-control-btn" title="Start Stream">
              <img id="camera-play-pause-icon" src="assets/icons/main-content/play.svg" alt="" />
            </button>

            <!-- Live Indicator with Timestamp (Hidden until actually live) -->
            <div id="camera-live-badge-group" class="feed-live-badge-group" style="display: none;">
              <span class="feed-live-dot"></span>
              <span class="feed-live-text">Live</span>
              <span class="live-separator">•</span>
              <span id="camera-live-timer" class="feed-live-timestamp">00:00</span>
            </div>
          </div>

          <!-- Right Controls: Rotate, Change Lens, Record, Fullscreen -->
          <div class="feed-controls-right">
            <!-- Rotate Feed Button -->
            <button id="btn-camera-rotate" class="feed-control-btn" title="Rotate feed (90°)">
              <img src="assets/icons/main-content/rotate-cw.svg" alt="" />
              <span id="camera-rotate-degrees" class="feed-btn-label">0°</span>
            </button>

            <!-- Change Lens Button (Front & Rear) - Camera specific -->
            <button id="btn-camera-lens" class="feed-control-btn feed-lens-btn" title="Change Lens (Front & Rear)">
              <img src="assets/icons/left-sidebar/camera.svg" alt="" />
              <span id="camera-lens-name" class="feed-btn-label">Front</span>
            </button>

            <!-- Record Button -->
            <button id="btn-camera-record" class="feed-control-btn feed-record-btn" title="Record Clip">
              <span class="feed-record-dot"></span>
              <span id="camera-record-label" class="feed-btn-label">Record</span>
              <span id="camera-record-timer" class="feed-record-timer" style="display: none;">00:00</span>
            </button>

            <!-- Fullscreen Button -->
            <button id="btn-camera-fullscreen" class="feed-control-btn" title="Toggle Fullscreen">
              <img src="assets/icons/main-content/maximize-2.svg" alt="" />
            </button>
          </div>
        </div>
      </div>
    </div>
  `;

  // Attach interactive control handlers
  setTimeout(() => {
    bindCameraEvents(container);
  }, 0);

  return container;
}

/**
 * Binds video player controls: play/pause, live counter, rotate, lens switch, and record.
 */
function bindCameraEvents(container: HTMLElement): void {
  const playPauseBtn = container.querySelector<HTMLButtonElement>('#btn-camera-play-pause');
  const playPauseIcon = container.querySelector<HTMLImageElement>('#camera-play-pause-icon');
  const pausedOverlay = container.querySelector<HTMLElement>('#camera-paused-overlay');
  const liveBadgeGroup = container.querySelector<HTMLElement>('#camera-live-badge-group');
  const timerSpan = container.querySelector<HTMLElement>('#camera-live-timer');
  const rotateBtn = container.querySelector<HTMLButtonElement>('#btn-camera-rotate');
  const rotateLabel = container.querySelector<HTMLElement>('#camera-rotate-degrees');
  const surface = container.querySelector<HTMLElement>('#camera-feed-surface');
  const lensBtn = container.querySelector<HTMLButtonElement>('#btn-camera-lens');
  const lensLabel = container.querySelector<HTMLElement>('#camera-lens-name');
  const recordBtn = container.querySelector<HTMLButtonElement>('#btn-camera-record');
  const recordLabel = container.querySelector<HTMLElement>('#camera-record-label');
  const recordTimer = container.querySelector<HTMLElement>('#camera-record-timer');
  const fullscreenBtn = container.querySelector<HTMLButtonElement>('#btn-camera-fullscreen');
  const viewportArea = container.querySelector<HTMLElement>('.feed-viewport-area');

  // 1. Play / Pause Stream state (Paused by default!)
  let isPlaying = false;
  let elapsedSeconds = 0;
  let liveInterval: ReturnType<typeof setInterval> | null = null;

  const startPlayback = () => {
    isPlaying = true;
    playPauseBtn?.setAttribute('title', 'Pause Stream');
    if (playPauseIcon) playPauseIcon.src = 'assets/icons/main-content/pause.svg';
    if (pausedOverlay) pausedOverlay.style.display = 'none';
    if (liveBadgeGroup) liveBadgeGroup.style.display = 'flex';
    sidebarState.setFeatureLive('camera', true);

    if (!liveInterval) {
      liveInterval = setInterval(() => {
        elapsedSeconds++;
        const hrs = Math.floor(elapsedSeconds / 3600);
        const mins = Math.floor((elapsedSeconds % 3600) / 60);
        const secs = elapsedSeconds % 60;
        if (timerSpan) {
          if (hrs > 0) {
            timerSpan.textContent = `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
          } else {
            timerSpan.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
          }
        }
      }, 1000);
    }
  };

  const pausePlayback = () => {
    isPlaying = false;
    playPauseBtn?.setAttribute('title', 'Start Stream');
    if (playPauseIcon) playPauseIcon.src = 'assets/icons/main-content/play.svg';
    if (pausedOverlay) pausedOverlay.style.display = 'flex';
    if (liveBadgeGroup) liveBadgeGroup.style.display = 'none';
    sidebarState.setFeatureLive('camera', false);

    if (liveInterval) {
      clearInterval(liveInterval);
      liveInterval = null;
    }
  };

  playPauseBtn?.addEventListener('click', () => {
    if (isPlaying) {
      pausePlayback();
    } else {
      startPlayback();
    }
  });

  // 2. Rotate Feed (0° -> 90° -> 180° -> 270° -> 0°)
  let currentRotation = 0;
  rotateBtn?.addEventListener('click', () => {
    currentRotation = (currentRotation + 90) % 360;
    if (surface) {
      surface.style.transform = `rotate(${currentRotation}deg)`;
    }
    if (rotateLabel) {
      rotateLabel.textContent = `${currentRotation}°`;
    }
    rotateBtn.setAttribute('title', `Rotate feed (${(currentRotation + 90) % 360}°)`);
  });

  // 3. Change Lens (Front <-> Rear)
  let currentLens: 'Front' | 'Rear' = 'Front';
  lensBtn?.addEventListener('click', () => {
    currentLens = currentLens === 'Front' ? 'Rear' : 'Front';
    if (lensLabel) lensLabel.textContent = currentLens;
  });

  // 4. Record Video
  let isRecording = false;
  let recordSeconds = 0;
  let recordInterval: ReturnType<typeof setInterval> | null = null;

  recordBtn?.addEventListener('click', () => {
    isRecording = !isRecording;
    if (isRecording) {
      recordBtn.classList.add('is-recording');
      if (recordLabel) recordLabel.style.display = 'none';
      if (recordTimer) {
        recordTimer.style.display = 'inline';
        recordTimer.textContent = '00:00';
      }
      recordSeconds = 0;
      recordInterval = setInterval(() => {
        recordSeconds++;
        const mins = String(Math.floor(recordSeconds / 60)).padStart(2, '0');
        const secs = String(recordSeconds % 60).padStart(2, '0');
        if (recordTimer) recordTimer.textContent = `${mins}:${secs}`;
      }, 1000);
    } else {
      recordBtn.classList.remove('is-recording');
      if (recordInterval) {
        clearInterval(recordInterval);
        recordInterval = null;
      }
      if (recordLabel) recordLabel.style.display = 'inline';
      if (recordTimer) recordTimer.style.display = 'none';
    }
  });

  // 5. Fullscreen Toggle
  fullscreenBtn?.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      viewportArea?.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  // Cleanup on unmount
  const observer = new MutationObserver(() => {
    if (!document.body.contains(container)) {
      if (liveInterval) clearInterval(liveInterval);
      if (recordInterval) clearInterval(recordInterval);
      sidebarState.setFeatureLive('camera', false);
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
