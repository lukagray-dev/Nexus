/**
 * @file device-enrollment.ts
 * @description Device Enrollment & Local Cache Action rows for Paired Devices.
 *
 * Hey friend! Hosts actions to enroll a new child phone or purge cached device snapshots.
 */

import { showToast } from '../../../shared/toast.js';
import { createSettingsGroup } from '../helpers.js';

/**
 * Renders the Device Enrollment & Actions settings group.
 */
export function renderDeviceEnrollmentGroup(): HTMLElement {
  const pairRow = document.createElement('div');
  pairRow.className = 'settings-row';
  pairRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Enroll New Child Phone</span>
      <span class="settings-label-desc">Generate secure pairing QR code and 12-digit one-time token</span>
    </div>
    <button type="button" id="btn-pair-new" class="settings-btn settings-btn-primary">
      <img src="assets/icons/settings/devices.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
      Pair New Device
    </button>
  `;

  const clearRow = document.createElement('div');
  clearRow.className = 'settings-row';
  clearRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Clear Device Local Cache</span>
      <span class="settings-label-desc">Purge cached storage directory trees and contact snapshots for this child</span>
    </div>
    <button type="button" id="btn-clear-device-cache" class="settings-btn settings-btn-secondary">
      <img src="assets/icons/settings/trash.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
      Clear Cache
    </button>
  `;

  pairRow.querySelector<HTMLButtonElement>('#btn-pair-new')?.addEventListener('click', () => {
    showToast('Pairing dialog: Scan QR on child device');
  });

  clearRow.querySelector<HTMLButtonElement>('#btn-clear-device-cache')?.addEventListener('click', () => {
    showToast('Local device cache cleared');
  });

  return createSettingsGroup('DEVICE ENROLLMENT & ACTIONS', [pairRow, clearRow], {
    marginTop: '18px',
  });
}
