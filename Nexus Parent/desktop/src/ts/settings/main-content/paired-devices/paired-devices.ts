/**
 * @file paired-devices.ts
 * @description Paired Devices Settings category compositor.
 *
 * Hey friend! Combines the active child device card and device enrollment actions.
 */

import { createSettingsSectionHeader } from '../helpers.js';
import { renderDeviceCardSetting } from './device-card.js';
import { renderDeviceEnrollmentGroup } from './device-enrollment.js';

/**
 * Renders the Paired Child Devices Settings main content section.
 */
export function renderPairedDevicesContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Paired Child Devices',
      'Manage enrolled mobile devices, customize friendly nicknames, and monitor hardware telemetry.'
    )
  );

  container.appendChild(renderDeviceCardSetting());
  container.appendChild(renderDeviceEnrollmentGroup());

  return container;
}
