/**
 * @file surveillance-dvr.ts
 * @description Surveillance & Rolling DVR Settings category compositor.
 *
 * Hey friend! Assembles all camera, screen mirror, and rolling DVR settings.
 */

import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderScreenQualitySetting } from './screen-quality.js';
import { renderCameraLensSetting } from './camera-lens.js';
import { renderHardwareAccelerationSetting } from './hardware-acceleration.js';
import { renderDvrDurationSetting } from './dvr-duration.js';
import { renderClipFormatSetting } from './clip-format.js';

/**
 * Renders the Surveillance & Rolling DVR Settings main content section.
 */
export function renderSurveillanceDvrContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'Surveillance & Rolling DVR',
      'Manage camera sensor preferences, screen mirroring streaming quality, and DVR rolling buffer retention.'
    )
  );

  container.appendChild(
    createSettingsGroup('LIVE STREAMING FIDELITY', [
      renderScreenQualitySetting(),
      renderCameraLensSetting(),
      renderHardwareAccelerationSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('ROLLING DVR TIMELINE BUFFER', [
      renderDvrDurationSetting(),
      renderClipFormatSetting(),
    ])
  );

  return container;
}
