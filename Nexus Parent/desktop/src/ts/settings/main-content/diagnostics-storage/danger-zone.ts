/**
 * @file danger-zone.ts
 * @description Danger Zone workspace reset setting for Diagnostics & Storage.
 *
 * Hey friend! Provides the full workspace reset action that restores factory defaults.
 */

import { settingsState } from '../../sidebar/state.js';
import { showToast } from '../../../shared/toast.js';
import { createSettingsGroup } from '../helpers.js';

/**
 * Renders the Danger Zone settings group.
 */
export function renderDangerZoneGroup(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';
  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Reset Parent Workspace</span>
      <span class="settings-label-desc">Clears all paired device records, cached settings, and restores factory defaults</span>
    </div>
    <button type="button" id="btn-factory-reset" class="settings-btn settings-btn-danger">Reset Workspace</button>
  `;

  row.querySelector<HTMLButtonElement>('#btn-factory-reset')?.addEventListener('click', () => {
    settingsState.resetToDefaults();
    showToast('Workspace reset to factory defaults');
  });

  return createSettingsGroup('DANGER ZONE', [row], {
    extraClass: 'settings-group-danger',
    titleColor: '#ef4444',
  });
}
