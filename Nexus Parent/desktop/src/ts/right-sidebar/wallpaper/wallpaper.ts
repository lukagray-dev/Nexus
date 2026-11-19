/**
 * @file wallpaper.ts
 * @description Remote Wallpaper Control Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! Here is how this panel coordinates child wallpaper surveillance and remote customization:
 * 1. Mobile HUD Preview: Clicking on the interactive phone mockup dispatches "WALLPAPER_GET"
 *    to fetch the child's active wallpapers on-demand.
 * 2. Multi-Screen Selection: Evaluates whether Home and Lock screen share the same image (isSame: true).
 *    If different, toggling between "Home Screen" and "Lock Screen" shows the respective wallpaper.
 * 3. Live Monitoring: Handled via the bottom dock footer Play/Pause button ("WALLPAPER_ON"/"WALLPAPER_OFF").
 *    When live, any wallpaper changes made on the child phone stream automatically to this preview!
 * 4. Push Wallpaper: Clicking "Push Wallpaper to Device" opens the native file picker, validates the image,
 *    slices the Base64 payload into 16KB DataChannel chunks, and tracks percentage upload progress.
 */

import { rightSidebarState } from '../state.js';
import {
  type WallpaperTarget,
  CMD_WALLPAPER_GET,
  CMD_PUSH_WALLPAPER_START,
  CMD_PUSH_WALLPAPER_CHUNK
} from '../types.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import { triggerSidebarToast } from '../shared.js';

const CHUNK_SIZE = 16384; // 16KB chunk size for reliable SCTP transport

/**
 * Creates and returns the Wallpaper Remote Control panel element.
 */
export function renderWallpaperPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-wallpaper-panel';

  const updateUi = () => {
    const data = rightSidebarState.getWallpaperData();
    const target = data.currentTarget;

    // Determine which image URL to render based on active target and isSame
    let activeUrl: string | null = null;
    if (target === 'home') {
      activeUrl = data.homeUrl;
    } else if (target === 'lock') {
      activeUrl = data.lockUrl || (data.isSame ? data.homeUrl : null);
    } else {
      activeUrl = data.homeUrl || data.lockUrl;
    }

    container.innerHTML = `
      <div class="wp-container">
        <!-- Device Mockup Preview (Clickable to fetch active wallpaper) -->
        <div class="wp-preview-frame" id="wp-preview-frame" title="Click to load current wallpaper from child">
          <div class="wp-preview-notch"></div>
          <div class="wp-preview-inner" id="wp-preview-display">
            ${
              data.isFetching
                ? `
                  <div style="display:flex; flex-direction:column; align-items:center; gap:8px;">
                    <img src="assets/icons/animations/loading.svg" alt="" style="width:20px;height:20px;" />
                    <span>Loading Wallpaper...</span>
                  </div>
                `
                : activeUrl
                ? `<div class="wp-preview-image" style="background-image: url('${activeUrl}');"></div>`
                : `
                  <div style="display:flex; flex-direction:column; align-items:center; gap:6px; text-align:center; padding:12px;">
                    <img src="assets/icons/main-content/image.svg" alt="" style="width:20px;height:20px;opacity:0.4;" />
                    <span style="font-weight:600; color:var(--text-primary);">Active Wallpaper</span>
                    <span style="font-size:10px; color:var(--text-muted);">Click to load from child</span>
                  </div>
                `
            }
          </div>
        </div>

        <!-- Target Selection Buttons -->
        <div class="wp-target-selector">
          <button class="wp-target-btn ${target === 'home' ? 'active' : ''}" data-target="home">Home Screen</button>
          <button class="wp-target-btn ${target === 'lock' ? 'active' : ''}" data-target="lock">Lock Screen</button>
          <button class="wp-target-btn ${target === 'both' ? 'active' : ''}" data-target="both">Both</button>
        </div>

        <!-- Hidden Native File Input for Wallpaper Upload -->
        <input type="file" id="wp-file-input" accept="image/png,image/jpeg,image/webp" style="display:none;" />

        <!-- Push Wallpaper Button -->
        <button id="btn-apply-wallpaper" class="wp-apply-btn" ${data.isPushing ? 'disabled' : ''}>
          ${
            data.isPushing
              ? `
                <img src="assets/icons/animations/loading.svg" alt="" style="width:14px;height:14px;" />
                <span>Pushing... ${data.pushProgress}%</span>
              `
              : `
                <img src="assets/icons/main-content/image.svg" alt="" style="width:14px;height:14px;" />
                <span>Push Wallpaper to Device</span>
              `
          }
        </button>
      </div>
    `;

    attachEventHandlers(container);
  };

  // Initial render
  updateUi();

  return container;
}

/**
 * Attaches DOM click listeners for fetching, target selection, and wallpaper pushing.
 */
function attachEventHandlers(container: HTMLElement): void {
  // 1. Mobile HUD Click: Requests on-demand wallpaper from child device
  const frame = container.querySelector('#wp-preview-frame');
  frame?.addEventListener('click', () => {
    fetchActiveWallpaper();
  });

  // 2. Target Selector Buttons (Home, Lock, Both)
  const targetBtns = container.querySelectorAll<HTMLButtonElement>('.wp-target-btn');
  targetBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const selected = btn.dataset.target as WallpaperTarget;
      if (selected) {
        rightSidebarState.setWallpaperTarget(selected);
      }
    });
  });

  // 3. Push Wallpaper Button
  const pushBtn = container.querySelector('#btn-apply-wallpaper');
  const fileInput = container.querySelector<HTMLInputElement>('#wp-file-input');

  pushBtn?.addEventListener('click', () => {
    if (fileInput) {
      fileInput.click();
    }
  });

  // 4. File Input Change: Read file, slice chunks, and dispatch across DataChannel
  fileInput?.addEventListener('change', (e) => {
    const target = e.target as HTMLInputElement;
    const file = target.files?.[0];
    if (file) {
      pushWallpaperToChild(file);
    }
    // Clear input so selecting the same file again triggers change
    target.value = '';
  });
}

/**
 * Sends a one-shot WALLPAPER_GET request across the WebRTC DataChannel.
 */
function fetchActiveWallpaper(): void {
  const currentState = rightSidebarState.getWallpaperData();
  if (currentState.isFetching) return;

  console.log('[WallpaperPanel] Requesting active wallpaper from child device...');
  rightSidebarState.setWallpaperFetching(true);

  const sent = webrtcManager.sendCommand(CMD_WALLPAPER_GET);
  if (!sent) {
    console.warn('[WallpaperPanel] Cannot send WALLPAPER_GET: DataChannel closed');
    rightSidebarState.setWallpaperFetching(false);
    triggerSidebarToast('Cannot fetch: DataChannel not connected');
    return;
  }

  // Safety fallback: if child doesn't respond within 15 seconds, revert fetching flag
  setTimeout(() => {
    if (rightSidebarState.getWallpaperData().isFetching) {
      rightSidebarState.setWallpaperFetching(false);
      triggerSidebarToast('Wallpaper fetch request timed out');
    }
  }, 15000);
}

/**
 * Reads user image file, validates size, slices into 16KB Base64 chunks,
 * and streams across the WebRTC DataChannel to the Android child.
 */
function pushWallpaperToChild(file: File): void {
  // Validate file size (max 10MB)
  if (file.size > 10 * 1024 * 1024) {
    triggerSidebarToast('Image too large: maximum file size is 10MB');
    return;
  }

  console.log(`[WallpaperPanel] Preparing to push wallpaper: ${file.name} (${file.size} bytes)...`);
  rightSidebarState.setWallpaperPushing(true, 0);

  const reader = new FileReader();

  reader.onload = () => {
    try {
      const dataUrl = reader.result as string;
      const base64Data = dataUrl.split(',')[1] || '';

      if (!base64Data) {
        throw new Error('Failed to read image Base64 data');
      }

      const currentTarget = rightSidebarState.getWallpaperData().currentTarget;
      const totalChunks = Math.ceil(base64Data.length / CHUNK_SIZE);

      // Step 1: Send Header Packet
      const startPayload = JSON.stringify({
        type: CMD_PUSH_WALLPAPER_START,
        target: currentTarget,
        totalChunks: totalChunks,
        fileSize: file.size
      });

      const startSent = webrtcManager.sendCommand(startPayload);
      if (!startSent) {
        throw new Error('DataChannel not connected');
      }

      // Step 2: Slice and dispatch 16KB chunks
      for (let i = 0; i < totalChunks; i++) {
        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, base64Data.length);
        const chunkSlice = base64Data.substring(start, end);

        const chunkPayload = JSON.stringify({
          type: CMD_PUSH_WALLPAPER_CHUNK,
          chunkIndex: i,
          totalChunks: totalChunks,
          data: chunkSlice
        });

        webrtcManager.sendCommand(chunkPayload);
        const progress = Math.round(((i + 1) / totalChunks) * 100);
        rightSidebarState.setWallpaperPushing(true, progress);
      }

      console.log(`[WallpaperPanel] Dispatched all ${totalChunks} chunks to child.`);

      // Optimistically update preview with local dataUrl
      rightSidebarState.setPushedWallpaperSuccess(currentTarget, dataUrl);

      // Safety timeout fallback (30s)
      setTimeout(() => {
        if (rightSidebarState.getWallpaperData().isPushing) {
          rightSidebarState.setWallpaperPushing(false, 0);
          triggerSidebarToast('Push wallpaper confirmation timed out');
        }
      }, 30000);

    } catch (err: any) {
      console.error('[WallpaperPanel] Failed pushing wallpaper:', err);
      rightSidebarState.setWallpaperPushing(false, 0);
      triggerSidebarToast(`Push failed: ${err.message || 'Unknown error'}`);
    }
  };

  reader.onerror = () => {
    rightSidebarState.setWallpaperPushing(false, 0);
    triggerSidebarToast('Failed reading selected image file');
  };

  reader.readAsDataURL(file);
}
