/**
 * @file mic.ts
 * @description Compact Floating Draggable Microphone Monitor component for Nexus Parent
 * with Parent-to-Child Voice Intercom and Real-Time Voice Changer Dropdown.
 *
 * Hey friend! This module implements a sleek floating audio monitor overlay with real-time
 * WebRTC audio playback and visualizer:
 *  1. Play / Pause / Loading button:
 *     - When user clicks Play, shows animated loading spinner (loading.svg) while sending MIC_ON.
 *     - When Child acknowledges with MIC_STARTED, flips to active Pause button and starts playback.
 *     - When user clicks Pause, shows animated loading spinner while sending MIC_OFF.
 *     - When Child acknowledges with MIC_STOPPED, flips back to Play button and stops audio.
 *  2. Real-Time Web Audio API Frequency Visualizer:
 *     - 36-bar canvas spectrum analyzing live audio PCM buffers from the child device.
 *  3. Split Intercom Control (Push-to-talk with Real-Time Voice Changer):
 *     - Primary Speak Button: Click to speak / stop with the active voice.
 *     - Dropdown Toggle Chevron: Click to open the tactical voice dropdown menu.
 *     - 10 Presets: Original, Robot Male, Robot Female, Human Male, Human Female,
 *       Male Monstar, Female Monstar, Overall Morphed, Echo, Alien.
 *     - Live Hot-Switching: Switch voices seamlessly during active speech without dropping audio!
 *     - LocalStorage Persistence: Remembers the parent's favorite voice across app sessions.
 */

import { sidebarState } from '../../left-sidebar/state.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import { settingsState } from '../../settings/sidebar/state.js';
import { showToast } from '../../shared/toast.js';
import { parentAudioController } from './parent-audio-controller.js';
import {
  EVENT_AUDIO_OUTPUT_CHANGED,
  applyAudioOutputDevice,
} from './audio-device-service.js';
import {
  VoicePresetId,
  VOICE_PRESETS,
  getVoicePreset,
} from './voice-presets.js';

// Cache the active floating panel DOM instance and its coordinates
let activePanelInstance: HTMLElement | null = null;
let lastPanelLeft: number | null = null;
let lastPanelTop: number | null = null;

// Global audio playback and analysis singletons for the mic module
let audioElement: HTMLAudioElement | null = null;
let audioContext: AudioContext | null = null;
let analyserNode: AnalyserNode | null = null;
let audioSourceNode: MediaStreamAudioSourceNode | null = null;

// Module-level tracking of active mic streaming state so overlay synchronization
// knows whether to preserve the live indicator when minimized
let globalMicState: MicButtonState = 'IDLE';

export type MicButtonState = 'IDLE' | 'STARTING' | 'STREAMING' | 'STOPPING';
export type SpeakButtonState = 'IDLE' | 'STARTING' | 'STREAMING' | 'STOPPING';

/**
 * Synchronizes the floating Mic overlay with the current content container.
 * If sidebarState.getIsMicOpen() is true, the small floating panel is attached
 * and displayed on top of whichever view is currently active.
 *
 * Hey friend! When closed (isOpen = false):
 * - The panel is hidden via CSS (display: 'none') rather than removed from the DOM.
 * - This prevents the MutationObserver from firing, so background audio playback
 *   and WebRTC listeners continue uninterrupted!
 * - If currently streaming, the sidebar LIVE indicator stays active!
 */
export function syncMicOverlay(container: HTMLElement): void {
  const isOpen = sidebarState.getIsMicOpen();

  // Get or create the floating panel element
  const panel = getOrCreateMicPanel();

  // Ensure panel is attached to the target container
  if (panel.parentElement !== container) {
    container.appendChild(panel);
  }

  if (!isOpen) {
    // Hide panel from view without removing it from the DOM
    panel.style.display = 'none';

    // Only turn off the live sidebar indicator if we are NOT actively streaming
    if (globalMicState !== 'STREAMING') {
      sidebarState.setFeatureLive('mic', false);
    }
    return;
  }

  // Panel is open: make it visible
  panel.style.display = 'flex';

  panel.style.right = 'auto';
  panel.style.bottom = 'auto';

  if (lastPanelLeft !== null && lastPanelTop !== null) {
    // Keep panel within current container bounds
    const parentWidth = container.clientWidth || 960;
    const parentHeight = container.clientHeight || 600;
    const panelWidth = panel.offsetWidth || 280;
    const panelHeight = panel.offsetHeight || 135;
    const safeLeft = Math.max(0, Math.min(lastPanelLeft, parentWidth - panelWidth));
    const safeTop = Math.max(0, Math.min(lastPanelTop, parentHeight - panelHeight));

    panel.style.left = `${safeLeft}px`;
    panel.style.top = `${safeTop}px`;
    lastPanelLeft = safeLeft;
    lastPanelTop = safeTop;
  } else {
    // Default to the bottom-right corner with 24px margin
    const positionDefault = () => {
      const containerRect =
        typeof container.getBoundingClientRect === 'function'
          ? container.getBoundingClientRect()
          : { width: 960, height: 600 };
      const parentWidth = container.clientWidth || containerRect.width;
      const parentHeight = container.clientHeight || containerRect.height;
      const panelWidth = panel.offsetWidth || 280;
      const panelHeight = panel.offsetHeight || 135;
      const initialLeft = Math.max(20, parentWidth - panelWidth - 24);
      const initialTop = Math.max(20, parentHeight - panelHeight - 24);
      panel.style.left = `${initialLeft}px`;
      panel.style.top = `${initialTop}px`;
      panel.style.right = 'auto';
      panel.style.bottom = 'auto';
      lastPanelLeft = initialLeft;
      lastPanelTop = initialTop;
    };

    positionDefault();
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(positionDefault);
    }
  }
}

/**
 * Returns the cached floating panel element or creates a new one if it doesn't exist.
 */
export function getOrCreateMicPanel(): HTMLElement {
  if (activePanelInstance) {
    return activePanelInstance;
  }

  activePanelInstance = renderMicPanel();
  return activePanelInstance;
}

/**
 * Creates and returns the compact floating draggable Mic panel element.
 */
export function renderMicPanel(): HTMLElement {
  const panel = document.createElement('div');
  panel.id = 'mic-floating-panel';
  panel.className = 'mic-floating-panel';

  const initialVoiceId = settingsState.getValue('defaultVoicePreset');
  const initialVoice = getVoicePreset(initialVoiceId);

  panel.innerHTML = `
    <!-- Draggable Header -->
    <div id="mic-panel-header" class="mic-panel-header">
      <div class="mic-panel-header-left">
        <span class="drag-grip-icon" title="Drag to move">
          <img src="assets/icons/main-content/grip-horizontal.svg" alt="" />
        </span>
        <span class="mic-panel-title">Microphone</span>
      </div>
      <div class="mic-panel-header-right">
        <button id="btn-panel-close" class="mic-header-btn btn-close-panel" title="Close Panel">
          <img src="assets/icons/main-content/x.svg" alt="" />
        </button>
      </div>
    </div>

    <!-- Dynamic Audio Wave Visualizer Canvas -->
    <div class="mic-wave-container">
      <canvas id="mic-wave-canvas" class="mic-wave-canvas" width="256" height="34"></canvas>
    </div>

    <!-- Core Action Controls Row -->
    <div class="mic-controls-row">
      <!-- 1. Play / Pause / Loading Button (starts in IDLE/paused state) -->
      <button id="btn-mic-play" class="mic-btn-icon" title="Play live audio stream">
        <img id="mic-play-icon" src="assets/icons/main-content/play.svg" alt="" />
      </button>

      <!-- 2. Split Speak & Voice Selector Control -->
      <div id="mic-speak-group" class="mic-speak-group">
        <!-- Main Speak Action Button -->
        <button id="btn-mic-speak" class="mic-btn-speak" title="Click to speak to child (Mic off)">
          <img id="mic-speak-icon" class="mic-speak-icon" src="assets/icons/main-content/mic.svg" alt="" />
          <img id="mic-speak-wave" class="mic-speak-wave" src="assets/icons/animations/wave.svg" alt="" style="display: none;" />
          <span id="mic-speak-label" class="mic-speak-label">Speak</span>
          <span id="mic-speak-voice-badge" class="mic-speak-voice-badge" title="Active Voice Preset">${initialVoice.name}</span>
        </button>

        <!-- Dropdown Chevron Trigger Button -->
        <button id="btn-mic-voice-toggle" class="mic-btn-voice-toggle" title="Select Voice Effect" aria-haspopup="true" aria-expanded="false">
          <img id="mic-voice-chevron" class="mic-voice-chevron" src="assets/icons/left-sidebar/chevron-down.svg" alt="" />
        </button>

        <!-- Tactical Voice Selection Dropdown Menu (pops up above the controls row) -->
        <div id="mic-voice-dropdown" class="mic-voice-dropdown drop-up" style="display: none;" role="menu">
          <div class="mic-voice-dropdown-header">
            <span class="mic-voice-dropdown-title">VOICE CHANGER</span>
            <span class="mic-voice-dropdown-subtitle">Real-time DSP</span>
          </div>
          <div id="mic-voice-dropdown-list" class="mic-voice-dropdown-list">
            <!-- Dynamically populated in bindMicPanelEvents -->
          </div>
        </div>
      </div>
    </div>
  `;

  // Bind dragging, WebRTC commands, and visualizer events to this panel
  setTimeout(() => {
    bindMicPanelEvents(panel);
  }, 0);

  return panel;
}

/**
 * Creates and returns the full-area workspace hosting the floating draggable Mic panel.
 */
export function renderMicView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'mic-workspace';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Microphone Monitor</span>
      </div>
    </div>
  `;

  const panel = renderMicPanel();
  container.appendChild(panel);

  return container;
}

/**
 * Attaches dragging, WebRTC DataChannel signaling, audio element playback,
 * real-time frequency waveform rendering, and voice changer dropdown handlers to the panel.
 */
function bindMicPanelEvents(panel: HTMLElement): void {
  const header = panel.querySelector<HTMLElement>('#mic-panel-header');
  const canvas = panel.querySelector<HTMLCanvasElement>('#mic-wave-canvas');
  const btnPlay = panel.querySelector<HTMLButtonElement>('#btn-mic-play');
  const playIcon = panel.querySelector<HTMLImageElement>('#mic-play-icon');
  const micSpeakGroup = panel.querySelector<HTMLElement>('#mic-speak-group');
  const btnSpeak = panel.querySelector<HTMLButtonElement>('#btn-mic-speak');
  const micSpeakIcon = panel.querySelector<HTMLImageElement>('#mic-speak-icon');
  const micSpeakWave = panel.querySelector<HTMLImageElement>('#mic-speak-wave');
  const micSpeakLabel = panel.querySelector<HTMLElement>('#mic-speak-label');
  const micSpeakVoiceBadge = panel.querySelector<HTMLElement>('#mic-speak-voice-badge');
  const btnVoiceToggle = panel.querySelector<HTMLButtonElement>('#btn-mic-voice-toggle');
  const micVoiceDropdown = panel.querySelector<HTMLElement>('#mic-voice-dropdown');
  const micVoiceDropdownList = panel.querySelector<HTMLElement>('#mic-voice-dropdown-list');
  const btnClose = panel.querySelector<HTMLButtonElement>('#btn-panel-close');

  if (!header) return;

  // State flags
  let micState: MicButtonState = 'IDLE';
  let speakState: SpeakButtonState = 'IDLE';
  let isSpeaking = false;
  let animFrameId: number | null = null;
  let confirmationTimeout: ReturnType<typeof setTimeout> | null = null;
  let speakTimeout: ReturnType<typeof setTimeout> | null = null;

  // Track the active voice preset synced with Settings
  let activeVoiceId: VoicePresetId = settingsState.getValue('defaultVoicePreset');

  /**
   * Synchronizes the Speak badge and voice dropdown item highlights to `presetId`.
   */
  function syncVoiceUi(presetId: VoicePresetId): void {
    activeVoiceId = presetId;
    const preset = getVoicePreset(presetId);
    if (micSpeakVoiceBadge) {
      micSpeakVoiceBadge.textContent = preset.name;
    }

    if (micVoiceDropdownList) {
      const items = micVoiceDropdownList.querySelectorAll<HTMLElement>('.mic-voice-item');
      items.forEach((item) => {
        const isMatch = item.dataset.voiceId === presetId;
        item.classList.toggle('is-active', isMatch);
        const checkIcon = item.querySelector<HTMLImageElement>('.mic-voice-item-check');
        if (checkIcon) {
          checkIcon.style.display = isMatch ? 'block' : 'none';
        }
      });
    }
  }

  /**
   * Refreshes the dropdown items DOM list with all 10 presets and marks the active voice.
   */
  function populateVoiceDropdown(): void {
    if (!micVoiceDropdownList) return;
    micVoiceDropdownList.innerHTML = '';

    VOICE_PRESETS.forEach((preset) => {
      const item = document.createElement('div');
      item.className = `mic-voice-item ${preset.id === activeVoiceId ? 'is-active' : ''}`;
      item.dataset.voiceId = preset.id;
      item.setAttribute('role', 'menuitem');
      item.setAttribute('tabindex', '0');

      item.innerHTML = `
        <img class="mic-voice-item-icon" src="${preset.iconPath}" alt="" />
        <div class="mic-voice-item-info">
          <span class="mic-voice-item-name">${preset.name}</span>
          <span class="mic-voice-item-desc">${preset.description}</span>
        </div>
        <img class="mic-voice-item-check" src="assets/icons/voice-presets/check.svg" alt="" style="display: ${
          preset.id === activeVoiceId ? 'block' : 'none'
        };" />
      `;

      item.addEventListener('click', (e) => {
        e.stopPropagation();
        selectVoicePreset(preset.id);
      });

      micVoiceDropdownList.appendChild(item);
    });
  }

  /**
   * Activates a chosen voice preset, updates badges, and crossfades Web Audio DSP.
   */
  function selectVoicePreset(presetId: VoicePresetId): void {
    parentAudioController.setVoice(presetId);
    settingsState.setValue('defaultVoicePreset', presetId);
    syncVoiceUi(presetId);
    closeVoiceDropdown();
  }

  /**
   * Opens the tactical voice selection dropdown menu with smart collision detection.
   */
  function openVoiceDropdown(): void {
    if (!micVoiceDropdown || !btnVoiceToggle) return;

    // Smart vertical placement: If panel is positioned near the top of the container,
    // open downwards so the menu is not clipped by viewport edges.
    const panelTop = panel.offsetTop;
    if (panelTop < 290) {
      micVoiceDropdown.classList.remove('drop-up');
      micVoiceDropdown.classList.add('drop-down');
    } else {
      micVoiceDropdown.classList.remove('drop-down');
      micVoiceDropdown.classList.add('drop-up');
    }

    micVoiceDropdown.style.display = 'flex';
    btnVoiceToggle.classList.add('is-open');
    btnVoiceToggle.setAttribute('aria-expanded', 'true');
  }

  /**
   * Closes the tactical voice selection dropdown menu.
   */
  function closeVoiceDropdown(): void {
    if (!micVoiceDropdown || !btnVoiceToggle) return;
    micVoiceDropdown.style.display = 'none';
    btnVoiceToggle.classList.remove('is-open');
    btnVoiceToggle.setAttribute('aria-expanded', 'false');
  }

  /**
   * Toggles the dropdown menu open or closed.
   */
  function toggleVoiceDropdown(): void {
    if (micVoiceDropdown && micVoiceDropdown.style.display === 'flex') {
      closeVoiceDropdown();
    } else {
      openVoiceDropdown();
    }
  }

  // Populate initial dropdown items
  populateVoiceDropdown();

  // Dropdown toggle button click
  btnVoiceToggle?.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleVoiceDropdown();
  });

  // Close dropdown on outside click
  const onDocumentClick = (e: MouseEvent) => {
    if (micSpeakGroup && !micSpeakGroup.contains(e.target as Node)) {
      closeVoiceDropdown();
    }
  };
  document.addEventListener('click', onDocumentClick);

  // Close dropdown on Escape key
  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      closeVoiceDropdown();
    }
  };
  document.addEventListener('keydown', onKeyDown);

  /**
   * Updates the Speak button visuals according to its state machine:
   * - IDLE: Standard mic icon and "Speak" label.
   * - STARTING: Rotating loading spinner waiting for Child confirmation.
   * - STREAMING: Active mic icon, animated wave icon, glowing aura, and canvas pulse.
   * - STOPPING: Rotating loading spinner waiting for Child mute confirmation.
   */
  function setSpeakButtonState(state: SpeakButtonState): void {
    speakState = state;
    if (!btnSpeak || !micSpeakIcon || !micSpeakWave || !micSpeakLabel) return;

    switch (state) {
      case 'IDLE':
        micSpeakIcon.src = 'assets/icons/main-content/mic.svg';
        micSpeakWave.style.display = 'none';
        micSpeakLabel.textContent = 'Speak';
        btnSpeak.classList.remove('is-speaking', 'is-loading');
        micSpeakGroup?.classList.remove('is-speaking', 'is-loading');
        btnSpeak.removeAttribute('disabled');
        btnSpeak.setAttribute('title', 'Click to speak to child (Mic off)');
        isSpeaking = false;
        break;

      case 'STARTING':
        micSpeakIcon.src = 'assets/icons/animations/loading.svg';
        micSpeakWave.style.display = 'none';
        micSpeakLabel.textContent = 'Connecting...';
        btnSpeak.classList.remove('is-speaking');
        btnSpeak.classList.add('is-loading');
        micSpeakGroup?.classList.remove('is-speaking');
        micSpeakGroup?.classList.add('is-loading');
        btnSpeak.setAttribute('disabled', 'true');
        btnSpeak.setAttribute('title', 'Connecting to child loudspeaker...');
        isSpeaking = false;
        break;

      case 'STREAMING':
        micSpeakIcon.src = 'assets/icons/main-content/mic.svg';
        micSpeakWave.style.display = 'inline-block';
        micSpeakLabel.textContent = 'Speaking';
        btnSpeak.classList.remove('is-loading');
        btnSpeak.classList.add('is-speaking');
        micSpeakGroup?.classList.remove('is-loading');
        micSpeakGroup?.classList.add('is-speaking');
        btnSpeak.removeAttribute('disabled');
        btnSpeak.setAttribute('title', 'Speaking to child at MAX volume (Click to stop)');
        isSpeaking = true;
        break;

      case 'STOPPING':
        micSpeakIcon.src = 'assets/icons/animations/loading.svg';
        micSpeakWave.style.display = 'none';
        micSpeakLabel.textContent = 'Stopping...';
        btnSpeak.classList.remove('is-speaking');
        btnSpeak.classList.add('is-loading');
        micSpeakGroup?.classList.remove('is-speaking');
        micSpeakGroup?.classList.add('is-loading');
        btnSpeak.setAttribute('disabled', 'true');
        btnSpeak.setAttribute('title', 'Restoring normal phone volume...');
        isSpeaking = false;
        break;
    }
  }

  /**
   * Updates the Play/Pause/Loading button visuals strictly according to current state.
   */
  function setButtonState(state: MicButtonState): void {
    micState = state;
    globalMicState = state;
    if (!btnPlay || !playIcon) return;

    switch (state) {
      case 'IDLE':
        playIcon.src = 'assets/icons/main-content/play.svg';
        btnPlay.classList.remove('active', 'is-loading');
        btnPlay.removeAttribute('disabled');
        btnPlay.setAttribute('title', 'Play live audio stream');
        sidebarState.setFeatureLive('mic', false);
        break;

      case 'STARTING':
        playIcon.src = 'assets/icons/animations/loading.svg';
        btnPlay.classList.remove('active');
        btnPlay.classList.add('is-loading');
        btnPlay.setAttribute('disabled', 'true');
        btnPlay.setAttribute('title', 'Connecting to child microphone...');
        break;

      case 'STREAMING':
        playIcon.src = 'assets/icons/main-content/pause.svg';
        btnPlay.classList.remove('is-loading');
        btnPlay.classList.add('active');
        btnPlay.removeAttribute('disabled');
        btnPlay.setAttribute('title', 'Pause live audio stream');
        sidebarState.setFeatureLive('mic', true);
        break;

      case 'STOPPING':
        playIcon.src = 'assets/icons/animations/loading.svg';
        btnPlay.classList.add('is-loading');
        btnPlay.setAttribute('disabled', 'true');
        btnPlay.setAttribute('title', 'Stopping child microphone...');
        break;
    }
  }

  /**
   * Attaches the incoming WebRTC MediaStream to the hidden HTML5 <audio> element
   * and configures Web Audio API AnalyserNode for the canvas visualizer.
   */
  function attachIncomingAudioStream(): void {
    const stream = webrtcManager.getMicStream();
    if (!stream) {
      console.log('[Mic] MediaStream not yet available. Awaiting nexus-mic-stream event...');
      return;
    }

    console.log('[Mic] Attaching audio stream to HTML5 playback element...');

    // Step 1: Ensure hidden <audio> element exists and plays stream
    if (!audioElement) {
      audioElement = document.createElement('audio');
      audioElement.id = 'nexus-mic-audio-stream';
      audioElement.autoplay = true;
      audioElement.style.display = 'none';
      document.body.appendChild(audioElement);
    }

    audioElement.srcObject = stream;
    void applyAudioOutputDevice(
      audioElement as unknown as { setSinkId?: (sinkId: string) => Promise<void> },
      settingsState.getValue('outputDeviceId')
    ).then((res) => {
      if (res.fellBackToDefault) {
        showToast('Selected speaker unavailable — using System Default Output');
      }
    });
    audioElement.play().catch((err) => {
      console.warn('[Mic] Audio autoplay prevented by browser policy:', err);
    });

    // Step 2: Wire Web Audio API AnalyserNode
    try {
      if (!audioContext || audioContext.state === 'closed') {
        const AudioCtx =
          window.AudioContext ||
          (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        audioContext = new AudioCtx();
      }
      if (audioContext.state === 'suspended') {
        audioContext.resume();
      }

      if (audioSourceNode) {
        audioSourceNode.disconnect();
      }

      audioSourceNode = audioContext.createMediaStreamSource(stream);
      analyserNode = audioContext.createAnalyser();
      analyserNode.fftSize = 64; // Yields 32 frequency bins, ideal for our 36 visualizer bars
      analyserNode.smoothingTimeConstant = 0.8;
      audioSourceNode.connect(analyserNode);
      console.log('[Mic] Web Audio AnalyserNode successfully connected to microphone stream.');
    } catch (err) {
      console.error('[Mic] Failed to configure Web Audio AnalyserNode:', err);
    }
  }

  /**
   * Stops audio element playback and releases Web Audio nodes.
   */
  function stopAudioPlayback(): void {
    if (audioElement) {
      audioElement.pause();
      audioElement.srcObject = null;
    }
    if (audioSourceNode) {
      try {
        audioSourceNode.disconnect();
      } catch (e) {
        console.warn('[Mic] Warning disconnecting audio source node:', e);
      }
      audioSourceNode = null;
    }
    analyserNode = null;
  }

  // Close button minimizes overlay without stopping active background audio stream
  btnClose?.addEventListener('click', () => {
    // Hey friend! Closing the floating mic panel should ONLY minimize the overlay!
    // The active audio stream continues playing seamlessly in the background,
    // and the sidebar LIVE badge remains active until the user explicitly pauses.
    closeVoiceDropdown();
    sidebarState.setMicOpen(false);
  });

  // Dragging implementation
  let isDragging = false;
  let dragStartX = 0;
  let dragStartY = 0;
  let initialPanelX = 0;
  let initialPanelY = 0;

  header.addEventListener('mousedown', (e: MouseEvent) => {
    if (e.button !== 0 || (e.target as HTMLElement).closest('.mic-header-btn')) return;
    e.preventDefault();

    // Close voice dropdown on drag start
    closeVoiceDropdown();

    isDragging = true;
    panel.classList.add('is-dragging');

    dragStartX = e.clientX;
    dragStartY = e.clientY;
    initialPanelX = panel.offsetLeft;
    initialPanelY = panel.offsetTop;

    panel.style.right = 'auto';
    panel.style.bottom = 'auto';
  });

  window.addEventListener('mousemove', (e: MouseEvent) => {
    if (!isDragging) return;
    e.preventDefault();

    const deltaX = e.clientX - dragStartX;
    const deltaY = e.clientY - dragStartY;

    const parent = panel.parentElement;
    const parentWidth = parent ? parent.clientWidth : 960;
    const parentHeight = parent ? parent.clientHeight : 600;
    const panelWidth = panel.offsetWidth || 280;
    const panelHeight = panel.offsetHeight || 135;

    const newLeft = Math.max(0, Math.min(initialPanelX + deltaX, parentWidth - panelWidth));
    const newTop = Math.max(0, Math.min(initialPanelY + deltaY, parentHeight - panelHeight));

    panel.style.left = `${newLeft}px`;
    panel.style.top = `${newTop}px`;
    lastPanelLeft = newLeft;
    lastPanelTop = newTop;
  });

  window.addEventListener('mouseup', () => {
    if (isDragging) {
      isDragging = false;
      panel.classList.remove('is-dragging');
    }
  });

  // 1. Play / Pause / Loading Control with Confirmation-based flow
  btnPlay?.addEventListener('click', () => {
    closeVoiceDropdown();

    if (micState === 'IDLE') {
      console.log('[Mic] Play clicked: Sending MIC_ON command...');
      setButtonState('STARTING');

      const sent = webrtcManager.sendCommand('MIC_ON');
      if (!sent) {
        console.warn('[Mic] Cannot send MIC_ON: DataChannel is not open.');
        setButtonState('IDLE');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (micState === 'STARTING') {
          console.warn('[Mic] Timeout waiting for MIC_STARTED from Child. Reverting to IDLE.');
          setButtonState('IDLE');
        }
      }, 20000);
    } else if (micState === 'STREAMING') {
      console.log('[Mic] Pause clicked: Sending MIC_OFF command...');
      setButtonState('STOPPING');

      const sent = webrtcManager.sendCommand('MIC_OFF');
      if (!sent) {
        console.warn('[Mic] Cannot send MIC_OFF: DataChannel is not open.');
        setButtonState('STREAMING');
        return;
      }

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      confirmationTimeout = setTimeout(() => {
        if (micState === 'STOPPING') {
          console.warn('[Mic] Timeout waiting for MIC_STOPPED from Child. Reverting to STREAMING.');
          setButtonState('STREAMING');
        }
      }, 20000);
    }
  });

  // WebRTC Connection State Listener: Auto-reset on peer disconnection
  const unbindConnectionState = webrtcManager.onConnectionStateChange((state) => {
    console.log('[Mic] WebRTC connection state changed:', state);
    if (state === 'disconnected' || state === 'failed' || state === 'closed') {
      console.log('[Mic] Connection lost. Executing automatic microphone and intercom reset...');
      closeVoiceDropdown();
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      if (speakTimeout) {
        clearTimeout(speakTimeout);
        speakTimeout = null;
      }
      setButtonState('IDLE');
      setSpeakButtonState('IDLE');
      stopAudioPlayback();
      parentAudioController.mute();
    }
  });

  // DataChannel Message Handler for Child confirmations
  const unbindDataChannel = webrtcManager.onMessage((rawMessage: string) => {
    const message = rawMessage.trim();
    console.log('[Mic] DataChannel message received:', message);

    if (message === 'MIC_STARTED') {
      console.log('[Mic] Confirmation received: MIC_STARTED. Activating stream and pause button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      setButtonState('STREAMING');
      attachIncomingAudioStream();
    } else if (message === 'MIC_STOPPED') {
      console.log('[Mic] Confirmation received: MIC_STOPPED. Deactivating stream and play button...');
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      stopAudioPlayback();
      setButtonState('IDLE');
    } else if (message.startsWith('MIC_ERROR:')) {
      console.error('[Mic] Child reported error:', message);
      if (confirmationTimeout) {
        clearTimeout(confirmationTimeout);
        confirmationTimeout = null;
      }
      if (micState === 'STARTING') {
        setButtonState('IDLE');
      } else if (micState === 'STOPPING') {
        setButtonState('STREAMING');
      }
    } else if (message === 'PARENT_AUDIO_UNMUTED') {
      console.log('[Mic] Confirmation received: PARENT_AUDIO_UNMUTED. Speaking mode active!');
      if (speakTimeout) {
        clearTimeout(speakTimeout);
        speakTimeout = null;
      }
      setSpeakButtonState('STREAMING');
    } else if (message === 'PARENT_AUDIO_MUTED') {
      console.log('[Mic] Confirmation received: PARENT_AUDIO_MUTED. Speaking mode stopped.');
      if (speakTimeout) {
        clearTimeout(speakTimeout);
        speakTimeout = null;
      }
      setSpeakButtonState('IDLE');
    } else if (message.startsWith('PARENT_AUDIO_ERROR:')) {
      console.error('[Mic] Child reported parent audio error:', message);
      if (speakTimeout) {
        clearTimeout(speakTimeout);
        speakTimeout = null;
      }
      parentAudioController.mute();
      setSpeakButtonState('IDLE');
    }
  });

  // Global window listener for incoming microphone stream events
  const onMicStreamReceived = (event: Event) => {
    const customEvent = event as CustomEvent<{ stream: MediaStream; track: MediaStreamTrack }>;
    console.log('[Mic] Global nexus-mic-stream event received:', customEvent.detail);
    if (micState === 'STREAMING' || micState === 'STARTING') {
      attachIncomingAudioStream();
    }
  };
  window.addEventListener('nexus-mic-stream', onMicStreamReceived);

  // Sync voice preset changes made inside Settings (including Reset Defaults)
  const onIntercomVoiceSync = (event: Event) => {
    const customEvent = event as CustomEvent<{ voiceId: VoicePresetId }>;
    if (customEvent.detail?.voiceId) {
      syncVoiceUi(customEvent.detail.voiceId);
    }
  };
  window.addEventListener('nexus-intercom-voice-sync', onIntercomVoiceSync);

  // Route incoming child audio to the newly chosen Monitoring Output Speaker live
  const onAudioOutputChanged = (event: Event) => {
    const customEvent = event as CustomEvent<{ deviceId: string }>;
    if (!audioElement) return;
    const targetDeviceId = customEvent.detail?.deviceId ?? settingsState.getValue('outputDeviceId');
    void applyAudioOutputDevice(
      audioElement as unknown as { setSinkId?: (sinkId: string) => Promise<void> },
      targetDeviceId
    ).then((res) => {
      if (res.fellBackToDefault) {
        showToast('Selected speaker unavailable — using System Default Output');
      }
    });
  };
  window.addEventListener(EVENT_AUDIO_OUTPUT_CHANGED, onAudioOutputChanged);

  // 2. Click to Speak Control (Microphone Intercom)
  btnSpeak?.addEventListener('click', async () => {
    closeVoiceDropdown();

    if (speakState === 'IDLE') {
      console.log(
        `[Mic] Speak clicked: Activating parent microphone with voice [${activeVoiceId}] and requesting loudspeaker boost...`
      );
      setSpeakButtonState('STARTING');

      try {
        await parentAudioController.unmute();

        // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
        if (speakTimeout) clearTimeout(speakTimeout);
        speakTimeout = setTimeout(() => {
          if (speakState === 'STARTING') {
            console.warn('[Mic] Timeout waiting for PARENT_AUDIO_UNMUTED from Child. Reverting to IDLE.');
            parentAudioController.mute();
            setSpeakButtonState('IDLE');
          }
        }, 20000);
      } catch (err) {
        console.error('[Mic] Failed to activate parent microphone:', err);
        setSpeakButtonState('IDLE');
      }
    } else if (speakState === 'STREAMING') {
      console.log('[Mic] Speak clicked to stop: Muting parent microphone...');
      setSpeakButtonState('STOPPING');

      parentAudioController.mute();

      // Safety fallback timer: if Child app doesn't confirm within 20s, revert state
      if (speakTimeout) clearTimeout(speakTimeout);
      speakTimeout = setTimeout(() => {
        if (speakState === 'STOPPING') {
          console.warn('[Mic] Timeout waiting for PARENT_AUDIO_MUTED from Child. Reverting to IDLE.');
          setSpeakButtonState('IDLE');
        }
      }, 20000);
    }
  });

  // 3. Wave Animation Canvas Rendering with Real Frequency Analysis
  if (canvas) {
    const ctx = canvas.getContext('2d');
    if (ctx) {
      let step = 0;

      const drawWave = () => {
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        const centerY = h / 2;
        const barCount = 36;
        const barWidth = 3;
        const gap = (w - barCount * barWidth) / (barCount - 1);

        step += 0.08;

        // Retrieve real frequency buckets if currently streaming
        let freqData: Uint8Array<ArrayBuffer> | null = null;
        if (micState === 'STREAMING' && analyserNode) {
          freqData = new Uint8Array(new ArrayBuffer(analyserNode.frequencyBinCount));
          analyserNode.getByteFrequencyData(freqData);
        }

        for (let i = 0; i < barCount; i++) {
          let amplitude = 2; // Flat idle baseline

          if (isSpeaking) {
            // High energy green pulse when parent holds to speak
            amplitude = 4 + Math.sin(step * 2 + i * 0.4) * 8 + Math.cos(step * 3 - i * 0.2) * 5;
          } else if (micState === 'STREAMING') {
            if (freqData && freqData.length > 0) {
              // Real hardware microphone spectrum data
              const binIdx = Math.min(freqData.length - 1, Math.floor(i * (freqData.length / barCount)));
              const norm = freqData[binIdx] / 255;
              amplitude = Math.max(2, norm * (h - 6));
            } else {
              // Graceful animated wave while waiting for buffer fill
              const wave1 = Math.sin(step + i * 0.3) * 5;
              const wave2 = Math.cos(step * 1.5 + i * 0.5) * 3;
              amplitude = Math.max(2, Math.abs(wave1 + wave2) + 2);
            }
          }

          const barHeight = Math.min(h - 4, Math.max(3, amplitude * 1.8));
          const x = i * (barWidth + gap);
          const y = centerY - barHeight / 2;

          ctx.fillStyle = isSpeaking
            ? '#22c55e'
            : micState === 'STREAMING'
            ? 'rgba(255, 255, 255, 0.9)'
            : 'rgba(255, 255, 255, 0.2)';

          // Draw rounded pill bar
          ctx.beginPath();
          ctx.roundRect(x, y, barWidth, barHeight, 1.5);
          ctx.fill();
        }

        animFrameId = requestAnimationFrame(drawWave);
      };

      drawWave();
    }
  }

  // Cleanup on element disconnect
  const observer = new MutationObserver(() => {
    if (!document.body.contains(panel)) {
      if (animFrameId) cancelAnimationFrame(animFrameId);
      if (confirmationTimeout) clearTimeout(confirmationTimeout);
      if (speakTimeout) clearTimeout(speakTimeout);
      document.removeEventListener('click', onDocumentClick);
      document.removeEventListener('keydown', onKeyDown);
      parentAudioController.cleanup();
      unbindDataChannel();
      unbindConnectionState();
      window.removeEventListener('nexus-mic-stream', onMicStreamReceived);
      window.removeEventListener('nexus-intercom-voice-sync', onIntercomVoiceSync);
      window.removeEventListener(EVENT_AUDIO_OUTPUT_CHANGED, onAudioOutputChanged);
      stopAudioPlayback();
      globalMicState = 'IDLE';
      sidebarState.setFeatureLive('mic', false);
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
