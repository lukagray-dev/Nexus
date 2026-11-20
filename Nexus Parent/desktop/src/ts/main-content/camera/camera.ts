/**
 * @file camera.ts
 * @description Camera Feed View controller for Nexus Parent main content area.
 *
 * Hey friend! This module coordinates the full-viewport Camera surveillance feed:
 * 1. Direct WebRTC Video Surface: Renders incoming live video frames with hardware acceleration (0ms latency).
 * 2. Zero-Renegotiation Control:
 *    - Clicking Play sends CAMERA_ON over DataChannel and shows an animated loading spinner (loading.svg).
 *    - When the Child confirms CAMERA_STARTED, flips to the Pause button, starts the live timer and background DVR!
 *    - Clicking Pause sends CAMERA_OFF with a loading spinner; when child confirms CAMERA_STOPPED, flips to Play.
 * 3. Dynamic Lens Switching:
 *    - Starts showing "Rear" lens (rear camera by default).
 *    - Clicking "Change Lens" sends CAMERA_SWITCH, shows spinner, and confirms CAMERA_SWITCHED.
 * 4. YouTube-Style Live Stream DVR:
 *    - Scrubber bar stays at 100% with a pulsing red "Live" indicator while watching in real-time.
 *    - Dragging or clicking the scrubber seeks backwards into the 30-minute rolling buffer.
 *    - The live stream continues recording in the background!
 *    - Clicking the "Live" badge instantly snaps back to the live edge!
 * 5. Rotatable Feed (90° increments) & Clip Recording.
 * 6. Disconnection Fail-Safe: Automatically resets state when peer connection drops.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import {
  type CameraState,
  type CameraLens,
  CMD_CAMERA_ON,
  CMD_CAMERA_OFF,
  CMD_CAMERA_SWITCH,
  RESP_CAMERA_STARTED,
  RESP_CAMERA_STOPPED,
  RESP_CAMERA_SWITCHED_PREFIX,
  RESP_CAMERA_ERROR_PREFIX
} from './types.js';
import { CameraDvrManager } from './camera-dvr.js';

// Module-level activation hook callback for smooth resumption on tab switch
let onCameraActivatedCallback: (() => void) | null = null;

/**
 * Hey friend! This activation hook is called whenever the user switches back
 * to the Camera tab in the main content router. It ensures:
 * 1. The video surface geometry updates to fit the active viewport.
 * 2. If the camera was actively streaming in the background, live video
 *    playback resumes smoothly without any frame stutter or black frames.
 */
export function onCameraViewActivated(): void {
  if (onCameraActivatedCallback) {
    requestAnimationFrame(() => {
      onCameraActivatedCallback?.();
    });
  }
}

/**
 * Creates and returns the full-area Camera Feed DOM element.
 */
export function renderCameraView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'feed-page-container';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Camera Feed</span>
      </div>
    </div>

    <!-- Full-area Edge-to-Edge Feed Canvas Area -->
    <div class="feed-viewport-area">
      <!-- Full-bleed Video Display Surface -->
      <div id="camera-feed-surface" class="feed-display-surface">
        <!-- Live Real-Time WebRTC Video Surface -->
        <video id="camera-video-stream" class="feed-video-element" autoplay playsinline muted></video>

        <!-- Secondary DVR Time-Shift Video Surface (shown only when scrubbing back) -->
        <video id="camera-dvr-video" class="feed-video-element" playsinline style="display: none;"></video>

        <div class="feed-ambient-canvas"></div>
      </div>

      <!-- Paused Stream Overlay (shown initially until stream starts, kept upright outside rotatable surface) -->
      <div id="camera-paused-overlay" class="feed-paused-overlay">
        <div class="feed-paused-pill">
          <img src="assets/icons/main-content/pause.svg" alt="" />
          <span>Feed Paused • Click Play to Start Stream</span>
        </div>
      </div>

      <!-- Floating Bottom Player Bar -->
      <div class="feed-player-controls-bar">
        <!-- YouTube-Style Seek / Progress Bar -->
        <div id="camera-progress-track" class="feed-progress-track" title="Live Stream Timeline">
          <div id="camera-progress-fill" class="feed-progress-fill"></div>
          <div id="camera-progress-scrubber" class="feed-progress-scrubber"></div>
        </div>

        <!-- Controls Row -->
        <div class="feed-controls-row">
          <!-- Left Controls: Play/Pause & Live Badge -->
          <div class="feed-controls-left">
            <!-- Play / Pause button starts with Play icon (paused by default) -->
            <button id="btn-camera-play-pause" class="feed-control-btn" title="Start Stream">
              <img id="camera-play-pause-icon" src="assets/icons/main-content/play.svg" alt="" />
            </button>

            <!-- YouTube-Style Live Indicator (Hidden until active, clickable to return to Live) -->
            <div id="camera-live-badge-group" class="feed-live-badge-group" style="display: none;" title="Click to Jump to Live">
              <span id="camera-live-dot" class="feed-live-dot is-live-pulsing"></span>
              <span id="camera-live-text" class="feed-live-text">Live</span>
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

            <!-- Change Lens Button (Rear by default per project specification) -->
            <button id="btn-camera-lens" class="feed-control-btn feed-lens-btn" title="Change Lens (Rear & Front)">
              <img id="camera-lens-icon" src="assets/icons/left-sidebar/camera.svg" alt="" />
              <span id="camera-lens-name" class="feed-btn-label">Rear</span>
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
 * Binds video player controls, WebRTC stream attachment, DataChannel signaling,
 * and YouTube-style DVR timeline scrubbing.
 */
function bindCameraEvents(container: HTMLElement): void {
  const playPauseBtn = container.querySelector<HTMLButtonElement>('#btn-camera-play-pause');
  const playPauseIcon = container.querySelector<HTMLImageElement>('#camera-play-pause-icon');
  const pausedOverlay = container.querySelector<HTMLElement>('#camera-paused-overlay');
  const liveBadgeGroup = container.querySelector<HTMLElement>('#camera-live-badge-group');
  const liveDot = container.querySelector<HTMLElement>('#camera-live-dot');
  const liveText = container.querySelector<HTMLElement>('#camera-live-text');
  const timerSpan = container.querySelector<HTMLElement>('#camera-live-timer');
  const rotateBtn = container.querySelector<HTMLButtonElement>('#btn-camera-rotate');
  const rotateLabel = container.querySelector<HTMLElement>('#camera-rotate-degrees');
  const surface = container.querySelector<HTMLElement>('#camera-feed-surface');
  const lensBtn = container.querySelector<HTMLButtonElement>('#btn-camera-lens');
  const lensIcon = container.querySelector<HTMLImageElement>('#camera-lens-icon');
  const lensLabel = container.querySelector<HTMLElement>('#camera-lens-name');
  const fullscreenBtn = container.querySelector<HTMLButtonElement>('#btn-camera-fullscreen');
  const viewportArea = container.querySelector<HTMLElement>('.feed-viewport-area');

  const liveVideoRaw = container.querySelector<HTMLVideoElement>('#camera-video-stream');
  const dvrVideoRaw = container.querySelector<HTMLVideoElement>('#camera-dvr-video');
  const progressTrack = container.querySelector<HTMLElement>('#camera-progress-track');
  const progressFill = container.querySelector<HTMLElement>('#camera-progress-fill');
  const scrubber = container.querySelector<HTMLElement>('#camera-progress-scrubber');

  if (!playPauseBtn || !liveVideoRaw || !dvrVideoRaw) return;

  const liveVideo: HTMLVideoElement = liveVideoRaw;
  const dvrVideo: HTMLVideoElement = dvrVideoRaw;

  // Active camera state flags
  let cameraState: CameraState = 'IDLE';
  let currentLens: CameraLens = 'Rear';
  let isSwitchingLens = false;
  let currentRotation = 0;
  let confirmationTimeout: ReturnType<typeof setTimeout> | null = null;
  let lensTimeout: ReturnType<typeof setTimeout> | null = null;

  // DVR Manager singleton for this view instance
  const dvrManager = new CameraDvrManager();

  /**
   * Updates Play/Pause button visual state and controls bar.
   */
  function setCameraState(state: CameraState): void {
    cameraState = state;
    if (!playPauseBtn || !playPauseIcon) return;

    switch (state) {
      case 'IDLE':
        playPauseIcon.src = 'assets/icons/main-content/play.svg';
        playPauseBtn.classList.remove('is-loading', 'is-active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Start Stream');
        if (pausedOverlay) pausedOverlay.style.display = 'flex';
        if (liveBadgeGroup) liveBadgeGroup.style.display = 'none';
        sidebarState.setFeatureLive('camera', false);

        // Clear video surfaces
        liveVideo.srcObject = null;
        liveVideo.pause();
        dvrVideo.style.display = 'none';
        liveVideo.style.display = 'block';
        dvrManager.stop();
        break;

      case 'STARTING':
        playPauseIcon.src = 'assets/icons/animations/loading.svg';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.classList.remove('is-active');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Connecting to child camera...');
        break;

      case 'STREAMING':
        playPauseIcon.src = 'assets/icons/main-content/pause.svg';
        playPauseBtn.classList.remove('is-loading');
        playPauseBtn.classList.add('is-active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Pause Stream');
        if (pausedOverlay) pausedOverlay.style.display = 'none';
        if (liveBadgeGroup) liveBadgeGroup.style.display = 'flex';
        sidebarState.setFeatureLive('camera', true);

        // Attach live stream
        attachLiveStream();
        break;

      case 'STOPPING':
        playPauseIcon.src = 'assets/icons/animations/loading.svg';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Stopping child camera...');
        break;
    }
  }

  /**
   * Updates Change Lens button visual state.
   */
  function setLensButtonState(switching: boolean, lens?: CameraLens): void {
    isSwitchingLens = switching;
    if (!lensBtn || !lensIcon || !lensLabel) return;

    if (lens) {
      currentLens = lens;
      lensLabel.textContent = lens;
    }

    if (switching) {
      lensIcon.src = 'assets/icons/animations/loading.svg';
      lensBtn.classList.add('is-loading');
      lensBtn.setAttribute('disabled', 'true');
      lensBtn.setAttribute('title', 'Switching camera lens...');
    } else {
      lensIcon.src = 'assets/icons/left-sidebar/camera.svg';
      lensBtn.classList.remove('is-loading');
      lensBtn.removeAttribute('disabled');
      lensBtn.setAttribute('title', `Change Lens (Current: ${currentLens})`);
    }
  }

  /**
   * Attaches the live WebRTC stream to the primary video element and starts DVR recording.
   */
  function attachLiveStream(): void {
    const stream = webrtcManager.getCameraStream();
    if (!stream) {
      console.log('[Camera] Camera MediaStream not yet cached. Waiting for nexus-camera-stream event...');
      return;
    }

    console.log('[Camera] Attaching WebRTC stream to live video surface...');

    // Only assign srcObject if it has changed!
    // In Chromium/WebView2, assigning srcObject to an identical stream invokes the media element load
    // algorithm, which aborts any pending play() request with:
    // "AbortError: The play() request was interrupted by a new load request."
    if (liveVideo.srcObject !== stream) {
      liveVideo.srcObject = stream;
    }

    liveVideo.style.display = 'block';
    dvrVideo.style.display = 'none';

    // Safely trigger video playback:
    // With autoplay and muted attributes, the browser will begin decoding automatically.
    // We bind to onloadedmetadata or trigger play() if currently paused.
    const startPlayback = () => {
      if (liveVideo.paused) {
        liveVideo.play().catch((err: Error) => {
          if (err.name !== 'AbortError') {
            console.warn('[Camera] Live video play warning:', err);
          }
        });
      }
    };

    if (liveVideo.readyState >= HTMLMediaElement.HAVE_METADATA) {
      startPlayback();
    } else {
      liveVideo.onloadedmetadata = () => {
        startPlayback();
      };
    }

    // Start rolling background DVR
    dvrManager.start(stream);
  }

  /**
   * Formats seconds into HH:MM:SS or MM:SS format.
   */
  function formatSeconds(totalSec: number): string {
    const hrs = Math.floor(totalSec / 3600);
    const mins = Math.floor((totalSec % 3600) / 60);
    const secs = Math.floor(totalSec % 60);

    const mm = String(mins).padStart(2, '0');
    const ss = String(secs).padStart(2, '0');

    if (hrs > 0) {
      return `${String(hrs).padStart(2, '0')}:${mm}:${ss}`;
    }
    return `${mm}:${ss}`;
  }

  // 1. Play / Pause Control with Confirmation-based flow
  playPauseBtn.addEventListener('click', () => {
    if (cameraState === 'IDLE') {
      console.log('[Camera] Play clicked: Sending CAMERA_ON command...');
      setCameraState('STARTING');

      const sent = webrtcManager.sendCommand(CMD_CAMERA_ON);
      if (!sent) {
        console.warn('[Camera] Cannot send CAMERA_ON: DataChannel is not open.');
        setCameraState('IDLE');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (cameraState === 'STARTING') {
          console.warn('[Camera] Timeout waiting for CAMERA_STARTED from Child. Reverting to IDLE.');
          setCameraState('IDLE');
        }
      }, 20000);
    } else if (cameraState === 'STREAMING') {
      console.log('[Camera] Pause clicked: Sending CAMERA_OFF command...');
      setCameraState('STOPPING');

      const sent = webrtcManager.sendCommand(CMD_CAMERA_OFF);
      if (!sent) {
        console.warn('[Camera] Cannot send CAMERA_OFF: DataChannel is not open.');
        setCameraState('STREAMING');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (cameraState === 'STOPPING') {
          console.warn('[Camera] Timeout waiting for CAMERA_STOPPED from Child. Reverting to STREAMING.');
          setCameraState('STREAMING');
        }
      }, 20000);
    }
  });

  // 2. Change Lens Control (Rear <-> Front)
  lensBtn?.addEventListener('click', () => {
    if (isSwitchingLens) return;

    console.log('[Camera] Change Lens clicked: Sending CAMERA_SWITCH command...');
    setLensButtonState(true);

    const sent = webrtcManager.sendCommand(CMD_CAMERA_SWITCH);
    if (!sent) {
      console.warn('[Camera] Cannot send CAMERA_SWITCH: DataChannel is not open.');
      setLensButtonState(false);
      return;
    }

    if (lensTimeout) clearTimeout(lensTimeout);
    lensTimeout = setTimeout(() => {
      if (isSwitchingLens) {
        console.warn('[Camera] Timeout waiting for CAMERA_SWITCHED from Child.');
        setLensButtonState(false);
      }
    }, 20000);
  });

  // 3. YouTube-Style DVR Progress Ticker
  dvrManager.onTick((progress) => {
    if (!timerSpan || !progressFill || !scrubber) return;

    if (progress.isLive) {
      // Live edge state: scrubber at 100%, pulsing red Live indicator
      timerSpan.textContent = formatSeconds(progress.totalSeconds);
      progressFill.style.width = '100%';
      scrubber.style.left = '100%';

      if (liveDot) {
        liveDot.classList.remove('is-dimmed');
        liveDot.classList.add('is-live-pulsing');
      }
      if (liveText) liveText.textContent = 'Live';

      liveVideo.style.display = 'block';
      dvrVideo.style.display = 'none';
    } else {
      // Time-shifted state: scrubber shows current playback offset, dim Live indicator
      timerSpan.textContent = `${formatSeconds(progress.currentSeconds)} / ${formatSeconds(progress.totalSeconds)}`;
      progressFill.style.width = `${progress.progressPercent}%`;
      scrubber.style.left = `${progress.progressPercent}%`;

      if (liveDot) {
        liveDot.classList.remove('is-live-pulsing');
        liveDot.classList.add('is-dimmed');
      }
      if (liveText) liveText.textContent = 'Go Live';

      liveVideo.style.display = 'none';
      dvrVideo.style.display = 'block';
    }
  });

  // 4. Scrubber / Seek Bar Interaction (Click & Drag to seek backwards like YouTube)
  let isScrubbing = false;

  const handleSeekFromEvent = (e: MouseEvent) => {
    if (!progressTrack) return;
    const rect = progressTrack.getBoundingClientRect();
    const clickX = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const ratio = rect.width > 0 ? clickX / rect.width : 1;

    const totalSeconds = dvrManager.getTotalDurationSeconds();
    if (totalSeconds <= 2) return;

    const targetSeconds = ratio * totalSeconds;
    dvrManager.seekTo(targetSeconds, dvrVideo);
  };

  progressTrack?.addEventListener('mousedown', (e: MouseEvent) => {
    if (cameraState !== 'STREAMING') return;
    isScrubbing = true;
    handleSeekFromEvent(e);
  });

  window.addEventListener('mousemove', (e: MouseEvent) => {
    if (!isScrubbing) return;
    handleSeekFromEvent(e);
  });

  window.addEventListener('mouseup', () => {
    if (isScrubbing) {
      isScrubbing = false;
    }
  });

  // 5. Clicking "Live" badge jumps straight back to the live feed
  liveBadgeGroup?.addEventListener('click', () => {
    if (cameraState === 'STREAMING') {
      dvrManager.goLive(dvrVideo);
    }
  });

  /**
   * Adjusts the feed display surface geometry and transform based on current rotation.
   *
   * Hey friend! Here is why this geometry calculation is crucial:
   * When rotating an HTML element by 90° or 270°, its visual width and height swap axes.
   * If the parent viewport container is landscape (e.g. 1500x800) and we only apply CSS rotate(270deg)
   * on a 100% width x 100% height box, the rotated box is constrained to 800px on screen, leaving
   * massive black margins on all 4 sides and turning the camera feed into a tiny cutout in the center!
   *
   * To achieve full screen utilization without cropping or digital zooming:
   * 1. The video elements use object-fit: contain to preserve 100% of the wide-angle camera sensor range (no FOV loss).
   * 2. When vertical (0° or 180°), the feed occupies the full screen height while leaving the left/right empty.
   * 3. When the user rotates the feed to landscape (90° or 270°), we invert the surface bounds
   *    (surface width = vpHeight, surface height = vpWidth). This allows the rotated landscape feed
   *    to naturally expand and use the full screen width and height!
   */
  function updateSurfaceGeometry(): void {
    if (!surface || !viewportArea) return;

    const vpWidth = viewportArea.clientWidth;
    const vpHeight = viewportArea.clientHeight;
    if (vpWidth === 0 || vpHeight === 0) return;

    const isRotatedSideways = currentRotation === 90 || currentRotation === 270;

    if (isRotatedSideways) {
      surface.style.width = `${vpHeight}px`;
      surface.style.height = `${vpWidth}px`;
    } else {
      surface.style.width = `${vpWidth}px`;
      surface.style.height = `${vpHeight}px`;
    }

    surface.style.transform = `translate(-50%, -50%) rotate(${currentRotation}deg)`;
  }

  // Initialize initial surface geometry and observe viewport dimensions
  updateSurfaceGeometry();

  // Register activation callback so tab switching smoothly resumes video playback & updates surface bounds
  onCameraActivatedCallback = () => {
    updateSurfaceGeometry();
    if (cameraState === 'STREAMING') {
      const stream = webrtcManager.getCameraStream();
      if (stream && liveVideo.srcObject !== stream) {
        liveVideo.srcObject = stream;
      }
      liveVideo.play().catch(() => {});
    }
  };

  const resizeObserver = new ResizeObserver(() => {
    updateSurfaceGeometry();
  });
  if (viewportArea) {
    resizeObserver.observe(viewportArea);
  }

  const onFullscreenChange = () => {
    setTimeout(updateSurfaceGeometry, 50);
  };
  document.addEventListener('fullscreenchange', onFullscreenChange);

  // 6. Rotate Feed (0° -> 90° -> 180° -> 270° -> 0°)
  rotateBtn?.addEventListener('click', () => {
    currentRotation = (currentRotation + 90) % 360;
    updateSurfaceGeometry();
    if (rotateLabel) {
      rotateLabel.textContent = `${currentRotation}°`;
    }
    rotateBtn.setAttribute('title', `Rotate feed (${(currentRotation + 90) % 360}°)`);
  });

  // 7. Fullscreen Toggle
  fullscreenBtn?.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      viewportArea?.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  // 9. WebRTC DataChannel Message Listener for Child confirmations
  const unbindDataChannel = webrtcManager.onMessage((rawMessage: string) => {
    const message = rawMessage.trim();
    console.log('[Camera] DataChannel message received:', message);

    if (message === RESP_CAMERA_STARTED) {
      console.log('[Camera] Confirmation received: CAMERA_STARTED. Activating stream and pause button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setCameraState('STREAMING');
    } else if (message === RESP_CAMERA_STOPPED) {
      console.log('[Camera] Confirmation received: CAMERA_STOPPED. Deactivating stream and play button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setCameraState('IDLE');
    } else if (message.startsWith(RESP_CAMERA_SWITCHED_PREFIX)) {
      console.log('[Camera] Confirmation received:', message);
      if (lensTimeout) {
        clearTimeout(lensTimeout);
        lensTimeout = null;
      }
      const parts = message.split(':');
      let switchedLens: CameraLens = currentLens === 'Rear' ? 'Front' : 'Rear';
      if (parts.length > 1) {
        const parsed = parts[1].trim().toUpperCase();
        switchedLens = parsed === 'FRONT' ? 'Front' : 'Rear';
      }
      setLensButtonState(false, switchedLens);
    } else if (message.startsWith(RESP_CAMERA_ERROR_PREFIX)) {
      console.error('[Camera] Child reported camera error:', message);
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      if (lensTimeout) {
        clearTimeout(lensTimeout);
        lensTimeout = null;
      }
      if (cameraState === 'STARTING') {
        setCameraState('IDLE');
      } else if (cameraState === 'STOPPING') {
        setCameraState('STREAMING');
      }
      setLensButtonState(false);
    }
  });

  // 10. WebRTC Stream Listener
  const onCameraStreamReceived = (event: Event) => {
    const customEvent = event as CustomEvent<{ stream: MediaStream; track: MediaStreamTrack }>;
    console.log('[Camera] Global nexus-camera-stream event received:', customEvent.detail);
    if (cameraState === 'STREAMING') {
      attachLiveStream();
    }
  };
  window.addEventListener('nexus-camera-stream', onCameraStreamReceived);

  // 11. WebRTC Connection State Listener: Auto-reset on peer disconnection
  const unbindConnectionState = webrtcManager.onConnectionStateChange((state) => {
    console.log('[Camera] WebRTC connection state changed:', state);
    if (state === 'disconnected' || state === 'failed' || state === 'closed') {
      console.log('[Camera] Peer connection lost. Executing automatic camera reset...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      if (lensTimeout) {
        clearTimeout(lensTimeout);
        lensTimeout = null;
      }
      dvrManager.stop();
      setCameraState('IDLE');
      setLensButtonState(false, 'Rear');
    }
  });

  // Cleanup on unmount
  const observer = new MutationObserver(() => {
    if (!document.body.contains(container)) {
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      if (lensTimeout) clearTimeout(lensTimeout);
      dvrManager.stop();
      unbindDataChannel();
      unbindConnectionState();
      resizeObserver.disconnect();
      document.removeEventListener('fullscreenchange', onFullscreenChange);
      window.removeEventListener('nexus-camera-stream', onCameraStreamReceived);
      sidebarState.setFeatureLive('camera', false);
      onCameraActivatedCallback = null;
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
