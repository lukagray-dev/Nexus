/**
 * @file surveillance-dvr.ts
 * @description Surveillance & Rolling DVR Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Screen mirroring resolution and framerate stream parameters.
 * 2. Preferred default camera sensor on surveillance start.
 * 3. Rolling DVR buffer memory retention depth (15m / 30m / 60m).
 * 4. Video clip export encoding formats (MP4 vs WebM).
 * 5. Chromium GPU hardware video acceleration.
 */

import { settingsState } from '../sidebar/state.js';

export function renderSurveillanceDvrContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Surveillance & Rolling DVR</h2>
      <p class="settings-content-desc">Manage camera sensor preferences, screen mirroring streaming quality, and DVR rolling buffer retention.</p>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">LIVE STREAMING FIDELITY</div>

      <!-- 1. Screen Mirror Quality -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Screen Mirror Quality</span>
          <span class="settings-label-desc">Higher quality increases detail; lower saves child phone battery</span>
        </div>
        <div class="settings-segmented-group" id="setting-screen-quality">
          <button type="button" class="settings-segmented-btn ${values.screenQuality === '1080p' ? 'is-active' : ''}" data-value="1080p">1080p @ 30fps</button>
          <button type="button" class="settings-segmented-btn ${values.screenQuality === '720p' ? 'is-active' : ''}" data-value="720p">720p @ 30fps</button>
          <button type="button" class="settings-segmented-btn ${values.screenQuality === '540p' ? 'is-active' : ''}" data-value="540p">540p @ 24fps</button>
        </div>
      </div>

      <!-- 2. Preferred Camera Lens -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Preferred Default Camera Lens</span>
          <span class="settings-label-desc">Initial optical sensor engaged when starting camera surveillance</span>
        </div>
        <div class="settings-segmented-group" id="setting-camera-lens">
          <button type="button" class="settings-segmented-btn ${values.preferredCameraLens === 'rear' ? 'is-active' : ''}" data-value="rear">Rear Lens</button>
          <button type="button" class="settings-segmented-btn ${values.preferredCameraLens === 'front' ? 'is-active' : ''}" data-value="front">Front Lens</button>
        </div>
      </div>

      <!-- 3. Hardware Video Acceleration -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">GPU Video Hardware Acceleration</span>
          <span class="settings-label-desc">Uses native GPU decoding inside WebView2 for smooth zero-lag playback</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-hardware-acceleration" ${values.hardwareAcceleration ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">ROLLING DVR TIMELINE BUFFER</div>

      <!-- 4. DVR Buffer Duration -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Rolling DVR Memory Retention</span>
          <span class="settings-label-desc">Timeline depth retained in parent RAM for instant rewinds and scrubber snaps</span>
        </div>
        <select id="setting-dvr-duration" class="settings-select">
          <option value="15m" ${values.dvrBufferDuration === '15m' ? 'selected' : ''}>15 Minutes (~140 MB RAM)</option>
          <option value="30m" ${values.dvrBufferDuration === '30m' ? 'selected' : ''}>30 Minutes (~280 MB RAM, Default)</option>
          <option value="60m" ${values.dvrBufferDuration === '60m' ? 'selected' : ''}>60 Minutes (~560 MB RAM)</option>
        </select>
      </div>

      <!-- 5. DVR Clip Export Format -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">DVR Clip Export Container</span>
          <span class="settings-label-desc">Format used when saving downloaded video clips to your PC</span>
        </div>
        <div class="settings-segmented-group" id="setting-clip-format">
          <button type="button" class="settings-segmented-btn ${values.dvrClipFormat === 'mp4' ? 'is-active' : ''}" data-value="mp4">MP4 (H.264 / AAC)</button>
          <button type="button" class="settings-segmented-btn ${values.dvrClipFormat === 'webm' ? 'is-active' : ''}" data-value="webm">WebM (Fast Save)</button>
        </div>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const qualityButtons = container.querySelectorAll<HTMLButtonElement>('#setting-screen-quality .settings-segmented-btn');
  qualityButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      qualityButtons.forEach((b) => b.classList.remove('is-active'));
      btn.classList.add('is-active');
      settingsState.setValue('screenQuality', btn.dataset.value as '1080p' | '720p' | '540p');
    });
  });

  const lensButtons = container.querySelectorAll<HTMLButtonElement>('#setting-camera-lens .settings-segmented-btn');
  lensButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      lensButtons.forEach((b) => b.classList.remove('is-active'));
      btn.classList.add('is-active');
      settingsState.setValue('preferredCameraLens', btn.dataset.value as 'rear' | 'front');
    });
  });

  const hwToggle = container.querySelector<HTMLInputElement>('#setting-hardware-acceleration');
  hwToggle?.addEventListener('change', () => {
    settingsState.setValue('hardwareAcceleration', hwToggle.checked);
  });

  const dvrSelect = container.querySelector<HTMLSelectElement>('#setting-dvr-duration');
  dvrSelect?.addEventListener('change', () => {
    settingsState.setValue('dvrBufferDuration', dvrSelect.value as '15m' | '30m' | '60m');
  });

  const formatButtons = container.querySelectorAll<HTMLButtonElement>('#setting-clip-format .settings-segmented-btn');
  formatButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      formatButtons.forEach((b) => b.classList.remove('is-active'));
      btn.classList.add('is-active');
      settingsState.setValue('dvrClipFormat', btn.dataset.value as 'mp4' | 'webm');
    });
  });

  return container;
}
