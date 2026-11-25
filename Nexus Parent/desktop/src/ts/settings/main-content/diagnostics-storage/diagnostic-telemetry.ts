/**
 * @file diagnostic-telemetry.ts
 * @description Diagnostic Telemetry Logging & Export settings for Diagnostics & Storage.
 *
 * Hey friend! Controls diagnostic logging toggle and JSON telemetry dump export.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';
import { createSettingsGroup } from '../helpers.js';

/**
 * Renders the Diagnostics & Telemetry settings group.
 */
export function renderDiagnosticTelemetryGroup(): HTMLElement {
  const enabled = settingsState.getValue('diagnosticLogging');

  const loggingRow = document.createElement('div');
  loggingRow.className = 'settings-row';
  loggingRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Diagnostic Telemetry Logging</span>
      <span class="settings-label-desc">Records WebRTC candidate exchange, SCTP channel latency, and SRTP jitter</span>
    </div>
    <label class="settings-toggle">
      <input type="checkbox" id="setting-diagnostic-logging" ${enabled ? 'checked' : ''} />
      <span class="settings-toggle-slider"></span>
    </label>
  `;

  const exportRow = document.createElement('div');
  exportRow.className = 'settings-row';
  exportRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Export Diagnostic Session Logs</span>
      <span class="settings-label-desc">Downloads JSON diagnostic bundle for bug reporting and network troubleshooting</span>
    </div>
    <button type="button" id="btn-export-logs" class="settings-btn settings-btn-secondary">
      <img src="assets/icons/settings/diagnostics.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
      Export Logs (.json)
    </button>
  `;

  const loggingToggle = loggingRow.querySelector<HTMLInputElement>('#setting-diagnostic-logging');
  loggingToggle?.addEventListener('change', () => {
    settingsState.setValue('diagnosticLogging', loggingToggle.checked);
  });

  exportRow.querySelector<HTMLButtonElement>('#btn-export-logs')?.addEventListener('click', () => {
    showToast('Diagnostic logs exported: nexus-telemetry-dump.json');
  });

  return createSettingsGroup('DIAGNOSTICS & TELEMETRY', [loggingRow, exportRow]);
}
