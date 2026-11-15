/**
 * @file mic.ts
 * @description Compact Floating Draggable Microphone Monitor component for Nexus Parent.
 *
 * Hey friend! This module implements a small, sleek floating audio monitor overlay.
 * It contains strictly 4 core items:
 *  1. Play / Pause button (toggles live audio listening)
 *  2. Hold to Speak button (two-way intercom push-to-talk)
 *  3. Record button (toggles audio recording clip)
 *  4. Audio waveform animation (real-time dynamic visualizer)
 */

import { sidebarState } from '../../left-sidebar/state.js';

// Cache the active floating panel DOM instance and its coordinates
let activePanelInstance: HTMLElement | null = null;
let lastPanelLeft: number | null = null;
let lastPanelTop: number | null = null;

/**
 * Synchronizes the floating Mic overlay with the current content container.
 * If sidebarState.getIsMicOpen() is true, the small floating panel is attached
 * on top of whichever view is currently active.
 * If false, the panel is removed from the DOM.
 */
export function syncMicOverlay(container: HTMLElement): void {
  const isOpen = sidebarState.getIsMicOpen();

  if (!isOpen) {
    sidebarState.setFeatureLive('mic', false);
    if (activePanelInstance && activePanelInstance.parentElement) {
      activePanelInstance.parentElement.removeChild(activePanelInstance);
    }
    return;
  }

  // Get or create the floating panel element
  const panel = getOrCreateMicPanel();

  // If already attached to this container, nothing to do
  if (panel.parentElement === container) {
    return;
  }

  // Attach to container
  container.appendChild(panel);

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
      const containerRect = typeof container.getBoundingClientRect === 'function'
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

    <!-- 4. Simple Wave Animation Container -->
    <div class="mic-wave-container">
      <canvas id="mic-wave-canvas" class="mic-wave-canvas" width="256" height="34"></canvas>
    </div>

    <!-- 3 Core Action Controls -->
    <div class="mic-controls-row">
      <!-- 1. Play / Pause Button (starts paused by default) -->
      <button id="btn-mic-play" class="mic-btn-icon" title="Play live audio stream">
        <img id="mic-play-icon" src="assets/icons/main-content/play.svg" alt="" />
      </button>

      <!-- 2. Hold to Speak Button -->
      <button id="btn-mic-speak" class="mic-btn-speak" title="Hold down to talk to child phone">
        <img src="assets/icons/main-content/mic-audio-lines.svg" alt="" />
        <span>Hold to Speak</span>
      </button>

      <!-- 3. Record Button -->
      <button id="btn-mic-record" class="mic-btn-record" title="Record audio clip">
        <span class="record-dot"></span>
        <span id="mic-record-label">Record</span>
        <span id="mic-record-timer" class="record-timer" style="display: none;">00:00</span>
      </button>
    </div>
  `;

  // Bind dragging and control events to this panel
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
 * Attaches dragging, audio waveform rendering, and controls event listeners.
 */
function bindMicPanelEvents(panel: HTMLElement): void {
  const header = panel.querySelector<HTMLElement>('#mic-panel-header');
  const canvas = panel.querySelector<HTMLCanvasElement>('#mic-wave-canvas');
  const btnPlay = panel.querySelector<HTMLButtonElement>('#btn-mic-play');
  const playIcon = panel.querySelector<HTMLImageElement>('#mic-play-icon');
  const btnSpeak = panel.querySelector<HTMLButtonElement>('#btn-mic-speak');
  const btnRecord = panel.querySelector<HTMLButtonElement>('#btn-mic-record');
  const recordLabel = panel.querySelector<HTMLElement>('#mic-record-label');
  const recordTimer = panel.querySelector<HTMLElement>('#mic-record-timer');
  const btnClose = panel.querySelector<HTMLButtonElement>('#btn-panel-close');

  if (!header) return;

  // State flags (starts in paused state by default)
  let isPlaying = false;
  let isSpeaking = false;
  let isRecording = false;
  let recordSeconds = 0;
  let recordInterval: ReturnType<typeof setInterval> | null = null;
  let animFrameId: number | null = null;

  // Close button hides overlay and turns off live state
  btnClose?.addEventListener('click', () => {
    sidebarState.setFeatureLive('mic', false);
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

  // 1. Play / Pause Control
  btnPlay?.addEventListener('click', () => {
    isPlaying = !isPlaying;
    if (isPlaying) {
      btnPlay.classList.add('active');
      btnPlay.setAttribute('title', 'Pause live audio stream');
      if (playIcon) playIcon.src = 'assets/icons/main-content/pause.svg';
      sidebarState.setFeatureLive('mic', true);
    } else {
      btnPlay.classList.remove('active');
      btnPlay.setAttribute('title', 'Play live audio stream');
      if (playIcon) playIcon.src = 'assets/icons/main-content/play.svg';
      sidebarState.setFeatureLive('mic', false);
    }
  });

  // 2. Hold to Speak Control
  const startSpeaking = (e: Event) => {
    e.preventDefault();
    isSpeaking = true;
    btnSpeak?.classList.add('is-speaking');
  };

  const stopSpeaking = (e: Event) => {
    e.preventDefault();
    isSpeaking = false;
    btnSpeak?.classList.remove('is-speaking');
  };

  btnSpeak?.addEventListener('mousedown', startSpeaking);
  btnSpeak?.addEventListener('mouseup', stopSpeaking);
  btnSpeak?.addEventListener('mouseleave', stopSpeaking);
  btnSpeak?.addEventListener('touchstart', startSpeaking, { passive: false });
  btnSpeak?.addEventListener('touchend', stopSpeaking);

  // 3. Record Control
  btnRecord?.addEventListener('click', () => {
    isRecording = !isRecording;

    if (isRecording) {
      btnRecord.classList.add('is-recording');
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
      btnRecord.classList.remove('is-recording');
      if (recordInterval) {
        clearInterval(recordInterval);
        recordInterval = null;
      }
      if (recordLabel) recordLabel.style.display = 'inline';
      if (recordTimer) recordTimer.style.display = 'none';
    }
  });

  // 4. Wave Animation Canvas Rendering
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

        for (let i = 0; i < barCount; i++) {
          let amplitude = 2; // Flat idle line

          if (isSpeaking) {
            // High energy green/white pulse when parent speaks
            amplitude = 4 + Math.sin(step * 2 + i * 0.4) * 8 + Math.cos(step * 3 - i * 0.2) * 5;
          } else if (isPlaying) {
            // Natural sound activity wave when listening
            const wave1 = Math.sin(step + i * 0.3) * 6;
            const wave2 = Math.cos(step * 1.5 + i * 0.5) * 4;
            amplitude = Math.max(2, Math.abs(wave1 + wave2) + (isRecording ? 3 : 1));
          }

          const barHeight = Math.min(h - 4, Math.max(3, amplitude * 1.8));
          const x = i * (barWidth + gap);
          const y = centerY - barHeight / 2;

          ctx.fillStyle = isSpeaking
            ? '#22c55e'
            : isRecording
            ? '#ef4444'
            : isPlaying
            ? 'rgba(255, 255, 255, 0.85)'
            : 'rgba(255, 255, 255, 0.25)';

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
      if (recordInterval) clearInterval(recordInterval);
      sidebarState.setFeatureLive('mic', false);
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
