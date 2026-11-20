/**
 * @file screen.ts
 * @description Screen Feed View controller for Nexus Parent main content area.
 *
 * Hey friend! This module coordinates full-viewport Full HD Screen surveillance:
 * 1. Direct WebRTC Video Surface: Renders incoming live screen frames with hardware acceleration (0ms latency).
 * 2. Zero-Renegotiation Control:
 *    - Clicking Play sends SCREEN_ON over DataChannel and displays an animated loading spinner (loading.svg).
 *    - When the Child confirms SCREEN_STARTED, flips to the Pause button, starts the live timer, DVR, and system audio!
 *    - Clicking Pause sends SCREEN_OFF with a loading spinner; when child confirms SCREEN_STOPPED, flips to Play.
 * 3. Synchronized Internal Audio Playback:
 *    - Child captures internal system/game audio on Android 10+ via AudioPlaybackCapture.
 *    - Hardware OpusEncoder sends 20ms frames across DataChannel prefixed with "OPUS_AUDIO:<base64>".
 *    - OpusAudioPlayer decodes packets with WebCodecs and schedules them seamlessly on AudioContext.
 * 4. YouTube-Style Live Stream DVR:
 *    - Scrubber bar stays at 100% with a pulsing red "Live" indicator while watching in real-time.
 *    - Dragging or clicking the scrubber seeks backwards into the 30-minute rolling buffer.
 *    - The live stream continues recording in the background!
 *    - Clicking the "Live" badge instantly snaps back to the live edge!
 * 5. Rotatable Feed (90° increments) with dynamic aspect ratio bounding & Clip Recording.
 * 6. Disconnection Fail-Safe: Automatically resets state when peer connection drops.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import {
  type ScreenState,
  CMD_SCREEN_ON,
  CMD_SCREEN_OFF,
  RESP_SCREEN_STARTED,
  RESP_SCREEN_STOPPED,
  RESP_SCREEN_ERROR_PREFIX,
  HEADER_OPUS_AUDIO
} from './types.js';
import { ScreenDvrManager } from './screen-dvr.js';
import { OpusAudioPlayer } from './opus-audio-player.js';

// Module-level activation hook callback for smooth resumption on tab switch
let onScreenActivatedCallback: (() => void) | null = null;

/**
 * Hey friend! This activation hook is called whenever the user switches back
 * to the Screen tab in the main content router. It ensures:
 * 1. The screen video surface geometry updates to fit the active viewport.
 * 2. If the screen was actively streaming in the background, live video
 *    playback resumes smoothly without any frame stutter or black frames.
 */
export function onScreenViewActivated(): void {
  if (onScreenActivatedCallback) {
    requestAnimationFrame(() => {
      onScreenActivatedCallback?.();
    });
  }
}

/**
 * Creates and returns the full-area Screen Feed DOM element.
 */
export function renderScreenView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'feed-page-container';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Screen Feed</span>
      </div>
    </div>

    <!-- Full-area Edge-to-Edge Feed Canvas Area -->
    <div class="feed-viewport-area">
      <!-- Full-bleed Video Display Surface -->
      <div id="screen-feed-surface" class="feed-display-surface">
        <!-- Live Real-Time WebRTC Video Surface -->
        <video id="screen-video-stream" class="feed-video-element" autoplay playsinline muted></video>

        <!-- Secondary DVR Time-Shift Video Surface (shown only when scrubbing back) -->
        <video id="screen-dvr-video" class="feed-video-element" playsinline style="display: none;"></video>

        <div class="feed-ambient-canvas"></div>
      </div>

      <!-- Paused Stream Overlay (shown initially until stream starts) -->
      <div id="screen-paused-overlay" class="feed-paused-overlay">
        <div class="feed-paused-pill">
          <img src="assets/icons/main-content/pause.svg" alt="" />
          <span>Feed Paused • Click Play to Start Stream</span>
        </div>
      </div>

      <!-- Floating Bottom Player Bar -->
      <div class="feed-player-controls-bar">
        <!-- YouTube-Style Seek / Progress Bar -->
        <div id="screen-progress-track" class="feed-progress-track" title="Live Stream Timeline">
          <div id="screen-progress-fill" class="feed-progress-fill"></div>
          <div id="screen-progress-scrubber" class="feed-progress-scrubber"></div>
        </div>

        <!-- Controls Row -->
        <div class="feed-controls-row">
          <!-- Left Controls: Play/Pause & Live Badge -->
          <div class="feed-controls-left">
            <!-- Play / Pause button starts with Play icon (paused by default) -->
            <button id="btn-screen-play-pause" class="feed-control-btn" title="Start Stream">
              <img id="screen-play-pause-icon" src="assets/icons/main-content/play.svg" alt="" />
            </button>

            <!-- YouTube-Style Live Indicator (Hidden until active, clickable to return to Live) -->
            <div id="screen-live-badge-group" class="feed-live-badge-group" style="display: none;" title="Click to Jump to Live">
              <span id="screen-live-dot" class="feed-live-dot is-live-pulsing"></span>
              <span id="screen-live-text" class="feed-live-text">Live</span>
              <span class="live-separator">•</span>
              <span id="screen-live-timer" class="feed-live-timestamp">00:00</span>
            </div>
          </div>

          <!-- Right Controls: Rotate, Record, Fullscreen -->
          <div class="feed-controls-right">
            <!-- Rotate Feed Button -->
            <button id="btn-screen-rotate" class="feed-control-btn" title="Rotate feed (90°)">
              <img src="assets/icons/main-content/rotate-cw.svg" alt="" />
              <span id="screen-rotate-degrees" class="feed-btn-label">0°</span>
            </button>

            <!-- Fullscreen Button -->
            <button id="btn-screen-fullscreen" class="feed-control-btn" title="Toggle Fullscreen">
              <img src="assets/icons/main-content/maximize-2.svg" alt="" />
            </button>
          </div>
        </div>
      </div>
    </div>
  `;

  // Attach interactive control handlers
  setTimeout(() => {
    bindScreenEvents(container);
  }, 0);

  return container;
}

/**
 * Binds video player controls, WebRTC stream attachment, DataChannel signaling,
 * Opus system audio decoding, and YouTube-style DVR timeline scrubbing.
 */
function bindScreenEvents(container: HTMLElement): void {
  const playPauseBtn = container.querySelector<HTMLButtonElement>('#btn-screen-play-pause');
  const playPauseIcon = container.querySelector<HTMLImageElement>('#screen-play-pause-icon');
  const pausedOverlay = container.querySelector<HTMLElement>('#screen-paused-overlay');
  const liveBadgeGroup = container.querySelector<HTMLElement>('#screen-live-badge-group');
  const liveDot = container.querySelector<HTMLElement>('#screen-live-dot');
  const liveText = container.querySelector<HTMLElement>('#screen-live-text');
  const timerSpan = container.querySelector<HTMLElement>('#screen-live-timer');
  const rotateBtn = container.querySelector<HTMLButtonElement>('#btn-screen-rotate');
  const rotateLabel = container.querySelector<HTMLElement>('#screen-rotate-degrees');
  const surface = container.querySelector<HTMLElement>('#screen-feed-surface');
  const fullscreenBtn = container.querySelector<HTMLButtonElement>('#btn-screen-fullscreen');
  const viewportArea = container.querySelector<HTMLElement>('.feed-viewport-area');

  const liveVideoRaw = container.querySelector<HTMLVideoElement>('#screen-video-stream');
  const dvrVideoRaw = container.querySelector<HTMLVideoElement>('#screen-dvr-video');
  const progressTrack = container.querySelector<HTMLElement>('#screen-progress-track');
  const progressFill = container.querySelector<HTMLElement>('#screen-progress-fill');
  const scrubber = container.querySelector<HTMLElement>('#screen-progress-scrubber');

  if (!playPauseBtn || !liveVideoRaw || !dvrVideoRaw) return;

  const liveVideo: HTMLVideoElement = liveVideoRaw;
  const dvrVideo: HTMLVideoElement = dvrVideoRaw;

  // Active screen streaming state
  let screenState: ScreenState = 'IDLE';
  let currentRotation = 0;
  let confirmationTimeout: ReturnType<typeof setTimeout> | null = null;

  // DVR Manager and Opus Audio Player singletons for this view
  const dvrManager = new ScreenDvrManager();
  const opusAudioPlayer = new OpusAudioPlayer();

  /**
   * Updates Play/Pause button visual state, overlays, and sidebar indicators.
   */
  function setScreenState(state: ScreenState): void {
    screenState = state;
    if (!playPauseBtn || !playPauseIcon) return;

    switch (state) {
      case 'IDLE':
        playPauseIcon.src = 'assets/icons/main-content/play.svg';
        playPauseBtn.classList.remove('is-loading', 'is-active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Start Stream');
        if (pausedOverlay) pausedOverlay.style.display = 'flex';
        if (liveBadgeGroup) liveBadgeGroup.style.display = 'none';
        sidebarState.setFeatureLive('screen', false);

        // Clear video surfaces and stop DVR & audio
        liveVideo.srcObject = null;
        liveVideo.pause();
        dvrVideo.style.display = 'none';
        liveVideo.style.display = 'block';
        dvrManager.stop();
        opusAudioPlayer.reset();
        break;

      case 'STARTING':
        playPauseIcon.src = 'assets/icons/animations/loading.svg';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.classList.remove('is-active');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Connecting to child screen...');
        break;

      case 'STREAMING':
        playPauseIcon.src = 'assets/icons/main-content/pause.svg';
        playPauseBtn.classList.remove('is-loading');
        playPauseBtn.classList.add('is-active');
        playPauseBtn.removeAttribute('disabled');
        playPauseBtn.setAttribute('title', 'Pause Stream');
        if (pausedOverlay) pausedOverlay.style.display = 'none';
        if (liveBadgeGroup) liveBadgeGroup.style.display = 'flex';
        sidebarState.setFeatureLive('screen', true);

        // Attach live stream
        attachLiveStream();
        break;

      case 'STOPPING':
        playPauseIcon.src = 'assets/icons/animations/loading.svg';
        playPauseBtn.classList.add('is-loading');
        playPauseBtn.setAttribute('disabled', 'true');
        playPauseBtn.setAttribute('title', 'Stopping child screen...');
        break;
    }
  }

  /**
   * Attaches the live WebRTC stream to the primary video element and starts DVR recording.
   */
  function attachLiveStream(): void {
    const stream = webrtcManager.getScreenStream();
    if (!stream) {
      console.log('[Screen] Screen MediaStream not yet cached. Waiting for nexus-screen-stream event...');
      return;
    }

    console.log('[Screen] Attaching WebRTC stream to live video surface...');

    const tracks = stream.getVideoTracks();
    for (const t of tracks) {
      t.enabled = true;
    }

    if (liveVideo.srcObject !== stream) {
      liveVideo.srcObject = stream;
    }

    liveVideo.style.display = 'block';
    dvrVideo.style.display = 'none';

    updateSurfaceGeometry();

    const startPlayback = () => {
      liveVideo.play().catch((err: Error) => {
        if (err.name !== 'AbortError') {
          console.warn('[Screen] Live video play warning:', err);
        }
      });
    };

    startPlayback();
    liveVideo.onloadedmetadata = () => {
      startPlayback();
      updateSurfaceGeometry();
    };

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
    if (screenState === 'IDLE') {
      console.log('[Screen] Play clicked: Sending SCREEN_ON command...');
      setScreenState('STARTING');

      const sent = webrtcManager.sendCommand(CMD_SCREEN_ON);
      if (!sent) {
        console.warn('[Screen] Cannot send SCREEN_ON: DataChannel is not open.');
        setScreenState('IDLE');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (screenState === 'STARTING') {
          console.warn('[Screen] Timeout waiting for SCREEN_STARTED from Child. Reverting to IDLE.');
          setScreenState('IDLE');
        }
      }, 20000);
    } else if (screenState === 'STREAMING') {
      console.log('[Screen] Pause clicked: Sending SCREEN_OFF command...');
      setScreenState('STOPPING');

      const sent = webrtcManager.sendCommand(CMD_SCREEN_OFF);
      if (!sent) {
        console.warn('[Screen] Cannot send SCREEN_OFF: DataChannel is not open.');
        setScreenState('STREAMING');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (screenState === 'STOPPING') {
          console.warn('[Screen] Timeout waiting for SCREEN_STOPPED from Child. Reverting to STREAMING.');
          setScreenState('STREAMING');
        }
      }, 20000);
    }
  });

  // 2. YouTube-Style DVR Progress Ticker
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

  // 3. Scrubber / Seek Bar Interaction (Click & Drag to seek backwards like YouTube)
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
    if (screenState !== 'STREAMING') return;
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

  // 4. Clicking "Live" badge jumps straight back to the live feed
  liveBadgeGroup?.addEventListener('click', () => {
    if (screenState === 'STREAMING') {
      dvrManager.goLive(dvrVideo);
    }
  });

  /**
   * Adjusts the feed display surface geometry and transform based on current rotation.
   * When rotated sideways (90° or 270°), we invert surface bounds to expand full width/height.
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

  updateSurfaceGeometry();

  // Register activation callback so tab switching smoothly resumes video playback & updates surface bounds
  onScreenActivatedCallback = () => {
    updateSurfaceGeometry();
    if (screenState === 'STREAMING') {
      const stream = webrtcManager.getScreenStream();
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

  // 5. Rotate Feed (0° -> 90° -> 180° -> 270° -> 0°)
  rotateBtn?.addEventListener('click', () => {
    currentRotation = (currentRotation + 90) % 360;
    updateSurfaceGeometry();
    if (rotateLabel) {
      rotateLabel.textContent = `${currentRotation}°`;
    }
    rotateBtn.setAttribute('title', `Rotate feed (${(currentRotation + 90) % 360}°)`);
  });

  // 6. Fullscreen Toggle
  fullscreenBtn?.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      viewportArea?.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  // 8. Internal System Audio Stream Listener
  const onScreenAudioPacket = (event: Event) => {
    const customEvent = event as CustomEvent<{ packet: string }>;
    if (screenState === 'STREAMING' && customEvent.detail?.packet) {
      opusAudioPlayer.feedBase64Packet(customEvent.detail.packet);
    }
  };
  window.addEventListener('nexus-screen-audio', onScreenAudioPacket);

  // 9. WebRTC DataChannel Message Listener for Child confirmations
  const unbindDataChannel = webrtcManager.onMessage((rawMessage: string) => {
    const message = rawMessage.trim();
    if (message.startsWith(HEADER_OPUS_AUDIO)) {
      return; // Handled directly via nexus-screen-audio event to avoid logging overhead
    }

    console.log('[Screen] DataChannel message received:', message);

    if (message === RESP_SCREEN_STARTED) {
      console.log('[Screen] Confirmation received: SCREEN_STARTED. Activating stream and pause button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setScreenState('STREAMING');
    } else if (message === RESP_SCREEN_STOPPED) {
      console.log('[Screen] Confirmation received: SCREEN_STOPPED. Deactivating stream and play button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setScreenState('IDLE');
    } else if (message.startsWith(RESP_SCREEN_ERROR_PREFIX)) {
      console.error('[Screen] Child reported screen error:', message);
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      if (screenState === 'STARTING') {
        setScreenState('IDLE');
      } else if (screenState === 'STOPPING') {
        setScreenState('STREAMING');
      }
    }
  });

  // 10. WebRTC Stream Listener
  const onScreenStreamReceived = (event: Event) => {
    const customEvent = event as CustomEvent<{ stream: MediaStream; track: MediaStreamTrack }>;
    console.log('[Screen] Global nexus-screen-stream event received:', customEvent.detail);
    if (screenState === 'STREAMING') {
      attachLiveStream();
    }
  };
  window.addEventListener('nexus-screen-stream', onScreenStreamReceived);

  // 11. WebRTC Connection State Listener: Auto-reset on peer disconnection
  const unbindConnectionState = webrtcManager.onConnectionStateChange((state) => {
    console.log('[Screen] WebRTC connection state changed:', state);
    if (state === 'disconnected' || state === 'failed' || state === 'closed') {
      console.log('[Screen] Peer connection lost. Executing automatic screen reset...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      dvrManager.stop();
      opusAudioPlayer.reset();
      setScreenState('IDLE');
    }
  });

  // Cleanup on unmount
  const observer = new MutationObserver(() => {
    if (!document.body.contains(container)) {
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      dvrManager.stop();
      opusAudioPlayer.stop();
      unbindDataChannel();
      unbindConnectionState();
      resizeObserver.disconnect();
      document.removeEventListener('fullscreenchange', onFullscreenChange);
      window.removeEventListener('nexus-screen-stream', onScreenStreamReceived);
      window.removeEventListener('nexus-screen-audio', onScreenAudioPacket);
      sidebarState.setFeatureLive('screen', false);
      onScreenActivatedCallback = null;
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
