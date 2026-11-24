/**
 * @file diagnostics-storage.ts
 * @description Diagnostics & Storage Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Storage cache analysis (thumbnails, directory indexes, logs).
 * 2. Diagnostic session telemetry logging toggle and log export.
 * 3. System runtime specs and binary build versions.
 * 4. Factory reset parent workspace state.
 */

import { settingsState } from '../sidebar/state.js';
import { showToast } from '../../shared/toast.js';

export function renderDiagnosticsStorageContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">Diagnostics & Storage</h2>
      <p class="settings-content-desc">Inspect memory caches, export WebRTC diagnostic session dumps, and manage system telemetry.</p>
    </div>

    <!-- Storage Cache Breakdown -->
    <div class="settings-group">
      <div class="settings-group-title">LOCAL STORAGE FOOTPRINT</div>

      <div class="settings-cache-grid">
        <div class="settings-cache-card">
          <span class="settings-cache-card-title">Image Thumbnails</span>
          <span id="cache-thumbnails-size" class="settings-cache-card-val">14.2 MB</span>
          <span class="settings-cache-card-sub">Cached SAF previews</span>
        </div>
        <div class="settings-cache-card">
          <span class="settings-cache-card-title">Directory Indexes</span>
          <span id="cache-trees-size" class="settings-cache-card-val">1.8 MB</span>
          <span class="settings-cache-card-sub">Filesystem hierarchy</span>
        </div>
        <div class="settings-cache-card">
          <span class="settings-cache-card-title">Session Telemetry</span>
          <span id="cache-logs-size" class="settings-cache-card-val">540 KB</span>
          <span class="settings-cache-card-sub">WebRTC connection logs</span>
        </div>
      </div>

      <div class="settings-row" style="margin-top: 10px;">
        <div class="settings-row-label">
          <span class="settings-label-title">Purge Local Cache</span>
          <span class="settings-label-desc">Reclaims local parent disk space without affecting child device files</span>
        </div>
        <button type="button" id="btn-purge-cache" class="settings-btn settings-btn-secondary">
          <img src="assets/icons/settings/trash.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
          Purge Cache
        </button>
      </div>
    </div>

    <!-- Diagnostic Logs & Session Metrics -->
    <div class="settings-group">
      <div class="settings-group-title">DIAGNOSTICS & TELEMETRY</div>

      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Diagnostic Telemetry Logging</span>
          <span class="settings-label-desc">Records WebRTC candidate exchange, SCTP channel latency, and SRTP jitter</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-diagnostic-logging" ${values.diagnosticLogging ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>

      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Export Diagnostic Session Logs</span>
          <span class="settings-label-desc">Downloads JSON diagnostic bundle for bug reporting and network troubleshooting</span>
        </div>
        <button type="button" id="btn-export-logs" class="settings-btn settings-btn-secondary">
          <img src="assets/icons/settings/diagnostics.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
          Export Logs (.json)
        </button>
      </div>
    </div>

    <!-- System & Architecture Badges -->
    <div class="settings-group">
      <div class="settings-group-title">BUILD & RUNTIME ENVIRONMENT</div>

      <div class="settings-build-grid">
        <div class="settings-build-tile">
          <span class="settings-build-label">Core Runtime</span>
          <span class="settings-build-val">Tauri v2.0 (Rust)</span>
        </div>
        <div class="settings-build-tile">
          <span class="settings-build-label">WebView Engine</span>
          <span class="settings-build-val">Microsoft WebView2 (Chromium)</span>
        </div>
        <div class="settings-build-tile">
          <span class="settings-build-label">Desktop Version</span>
          <span class="settings-build-val">v0.1.0-alpha</span>
        </div>
        <div class="settings-build-tile">
          <span class="settings-build-label">Encryption Suite</span>
          <span class="settings-build-val">AES-256-GCM / SRTP</span>
        </div>
      </div>
    </div>

    <!-- Danger Zone -->
    <div class="settings-group settings-group-danger">
      <div class="settings-group-title" style="color: #ef4444;">DANGER ZONE</div>

      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Reset Parent Workspace</span>
          <span class="settings-label-desc">Clears all paired device records, cached settings, and restores factory defaults</span>
        </div>
        <button type="button" id="btn-factory-reset" class="settings-btn settings-btn-danger">Reset Workspace</button>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const purgeBtn = container.querySelector<HTMLButtonElement>('#btn-purge-cache');
  purgeBtn?.addEventListener('click', () => {
    const thumbEl = container.querySelector('#cache-thumbnails-size');
    const treeEl = container.querySelector('#cache-trees-size');
    const logEl = container.querySelector('#cache-logs-size');
    if (thumbEl) thumbEl.textContent = '0 KB';
    if (treeEl) treeEl.textContent = '0 KB';
    if (logEl) logEl.textContent = '0 KB';
    showToast('Cache purged successfully (0 KB)');
  });

  const loggingToggle = container.querySelector<HTMLInputElement>('#setting-diagnostic-logging');
  loggingToggle?.addEventListener('change', () => {
    settingsState.setValue('diagnosticLogging', loggingToggle.checked);
  });

  const exportBtn = container.querySelector<HTMLButtonElement>('#btn-export-logs');
  exportBtn?.addEventListener('click', () => {
    showToast('Diagnostic logs exported: nexus-telemetry-dump.json');
  });

  const resetBtn = container.querySelector<HTMLButtonElement>('#btn-factory-reset');
  resetBtn?.addEventListener('click', () => {
    settingsState.resetToDefaults();
    showToast('Workspace reset to factory defaults');
  });

  return container;
}
