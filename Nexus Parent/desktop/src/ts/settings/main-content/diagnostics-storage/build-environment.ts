/**
 * @file build-environment.ts
 * @description Build & Runtime Environment read-only telemetry grid.
 *
 * Hey friend! Displays runtime, WebView engine, desktop version, and encryption suite info.
 */

import { createSettingsGroup } from '../helpers.js';

/**
 * Renders the Build & Runtime Environment info group.
 */
export function renderBuildEnvironmentGroup(): HTMLElement {
  const grid = document.createElement('div');
  grid.className = 'settings-build-grid';
  grid.innerHTML = `
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
  `;

  return createSettingsGroup('BUILD & RUNTIME ENVIRONMENT', [grid]);
}
