/**
 * @file diagnostics-storage.ts
 * @description Diagnostics & Storage Settings category compositor.
 *
 * Hey friend! Combines local storage footprint, diagnostic telemetry, build info, and danger zone.
 */

import { createSettingsSectionHeader } from '../helpers.js';
import { renderStorageFootprintGroup } from './storage-footprint.js';
import { renderDiagnosticTelemetryGroup } from './diagnostic-telemetry.js';
import { renderBuildEnvironmentGroup } from './build-environment.js';
import { renderDangerZoneGroup } from './danger-zone.js';

/**
 * Renders the Diagnostics & Storage Settings main content section.
 */
export function renderDiagnosticsStorageContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Diagnostics & Storage',
      'Inspect memory caches, export WebRTC diagnostic session dumps, and manage system telemetry.'
    )
  );

  container.appendChild(renderStorageFootprintGroup());
  container.appendChild(renderDiagnosticTelemetryGroup());
  container.appendChild(renderBuildEnvironmentGroup());
  container.appendChild(renderDangerZoneGroup());

  return container;
}
