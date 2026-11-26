/**
 * @file surveillance-dvr.ts
 * @description Surveillance & Rolling DVR Settings category compositor and runtime synchronizer.
 *
 * Hey friend! Assembles all camera, screen mirror, and rolling DVR settings and
 * keeps them synchronized with Rust (`window-settings.json` for WebView2 GPU flags)
 * and live WebRTC / Camera / Screen controllers.
 */

import { settingsState } from '../../sidebar/state.js';
import { getWindowTraySettings } from '../../../shared/ipc.js';
import { webrtcManager } from '../../../webrtc/webrtc-manager.js';
import { EVENT_CAMERA_PREFERRED_LENS_CHANGED } from '../../../main-content/camera/types.js';
import { buildScreenQualityCommand } from '../../../main-content/screen/types.js';
import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderScreenQualitySetting } from './screen-quality.js';
import { renderCameraLensSetting } from './camera-lens.js';
import {
  applyHardwareAccelerationSetting,
  markHardwareAccelerationSynced,
  renderHardwareAccelerationSetting,
  setLaunchHardwareAcceleration,
  syncHardwareAccelerationIfChanged,
} from './hardware-acceleration.js';
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

/**
 * Initializes live runtime synchronization for Surveillance & DVR settings:
 * - Reconciles `hardwareAcceleration` with Rust's `window-settings.json` on startup.
 * - Pushes live `SCREEN_QUALITY` updates over DataChannel when `screenQuality` changes.
 * - Broadcasts `preferredCameraLens` changes to any idle Camera view.
 */
export function initSurveillanceDvrRuntime(): () => void {
  const initialValues = settingsState.getValues();
  let lastScreenQuality = initialValues.screenQuality;
  let lastPreferredLens = initialValues.preferredCameraLens;

  void (async () => {
    const snapshot = await getWindowTraySettings();
    if (!snapshot) {
      return;
    }

    if (typeof snapshot.launchHardwareAcceleration === 'boolean') {
      setLaunchHardwareAcceleration(snapshot.launchHardwareAcceleration);
    }

    if (typeof snapshot.hardwareAcceleration === 'boolean') {
      if (snapshot.hardwareAcceleration === initialValues.hardwareAcceleration) {
        markHardwareAccelerationSynced(initialValues.hardwareAcceleration);
      } else {
        await applyHardwareAccelerationSetting(initialValues.hardwareAcceleration);
      }
    }
  })();

  const unsubscribeValues = settingsState.onValuesChange((values) => {
    void syncHardwareAccelerationIfChanged(values.hardwareAcceleration);

    if (values.screenQuality !== lastScreenQuality) {
      lastScreenQuality = values.screenQuality;
      const dc = webrtcManager.getDataChannel();
      if (dc && dc.readyState === 'open') {
        webrtcManager.sendCommand(buildScreenQualityCommand(values.screenQuality));
      }
    }

    if (values.preferredCameraLens !== lastPreferredLens) {
      lastPreferredLens = values.preferredCameraLens;
      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(
          new CustomEvent(EVENT_CAMERA_PREFERRED_LENS_CHANGED, {
            detail: { lens: values.preferredCameraLens },
          })
        );
      }
    }
  });

  return () => {
    unsubscribeValues();
  };
}
