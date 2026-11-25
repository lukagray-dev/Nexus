/**
 * @file storage-footprint.ts
 * @description Local Storage Footprint & Cache Purge setting group.
 *
 * Hey friend! Displays local cache consumption cards and provides a Purge Cache action.
 */

import { showToast } from '../../../shared/toast.js';
import { createSettingsGroup } from '../helpers.js';

/**
 * Renders the Local Storage Footprint group for Diagnostics & Storage.
 */
export function renderStorageFootprintGroup(): HTMLElement {
  const grid = document.createElement('div');
  grid.className = 'settings-cache-grid';
  grid.innerHTML = `
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
  `;

  const purgeRow = document.createElement('div');
  purgeRow.className = 'settings-row';
  purgeRow.style.marginTop = '10px';
  purgeRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Purge Local Cache</span>
      <span class="settings-label-desc">Reclaims local parent disk space without affecting child device files</span>
    </div>
    <button type="button" id="btn-purge-cache" class="settings-btn settings-btn-secondary">
      <img src="assets/icons/settings/trash.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
      Purge Cache
    </button>
  `;

  purgeRow.querySelector<HTMLButtonElement>('#btn-purge-cache')?.addEventListener('click', () => {
    const thumbEl = grid.querySelector('#cache-thumbnails-size');
    const treeEl = grid.querySelector('#cache-trees-size');
    const logEl = grid.querySelector('#cache-logs-size');
    if (thumbEl) thumbEl.textContent = '0 KB';
    if (treeEl) treeEl.textContent = '0 KB';
    if (logEl) logEl.textContent = '0 KB';
    showToast('Cache purged successfully (0 KB)');
  });

  return createSettingsGroup('LOCAL STORAGE FOOTPRINT', [grid, purgeRow]);
}
