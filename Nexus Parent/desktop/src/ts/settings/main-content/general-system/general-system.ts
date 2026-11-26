/**
 * @file general-system.ts
 * @description General & System Settings category compositor and runtime synchronizer.
 *
 * Hey friend! This file:
 * 1. Composes the General & System view from individual per-setting modules across
 *    File Storage & Export, Startup & Connection, Window & Display Behavior, and Application Updates.
 * 2. Synchronizes `closeAction`, `launchOnStartup`, `startMinimized`, and `alwaysOnTop`
 *    between `settingsState` and Rust on boot and whenever settings change (including Reset Defaults).
 * 3. Initializes the display wake-lock synchronizer (`keepScreenAwake`) and background GitHub
 *    stable release auto-updater (`autoCheckUpdates`).
 * 4. Listens for the system tray's `tray://open-settings` menu action to open Settings.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  getWindowTraySettings,
  listenTauriEvent,
} from '../../../shared/ipc.js';
import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderAskBeforeDownloadSetting } from './ask-before-download.js';
import { renderDownloadsDirectorySetting } from './downloads-directory.js';
import { renderAutoConnectLastDeviceSetting } from './auto-connect-last-device.js';
import {
  applyLaunchOnStartupSetting,
  markLaunchOnStartupSynced,
  renderLaunchOnStartupSetting,
  syncLaunchOnStartupIfChanged,
} from './launch-on-startup.js';
import {
  applyStartMinimizedSetting,
  markStartMinimizedSynced,
  renderStartMinimizedSetting,
  syncStartMinimizedIfChanged,
} from './start-minimized.js';
import {
  applyAlwaysOnTopSetting,
  markAlwaysOnTopSynced,
  renderAlwaysOnTopSetting,
  syncAlwaysOnTopIfChanged,
} from './always-on-top.js';
import {
  initKeepScreenAwakeRuntime,
  renderKeepScreenAwakeSetting,
} from './keep-screen-awake.js';
import {
  applyCloseActionSetting,
  markCloseActionSynced,
  renderSystemTraySetting,
  syncCloseActionIfChanged,
} from './system-tray.js';
import {
  initAutoUpdateRuntime,
  renderAutoUpdateSetting,
} from './auto-update.js';
import {
  initDesktopAlertsRuntime,
  renderDesktopAlertSettings,
} from './desktop-alerts.js';

export const EVENT_TRAY_OPEN_SETTINGS = 'tray://open-settings';

/**
 * Renders the General & System Settings main content section.
 */
export function renderGeneralSystemContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'General & System',
      'Configure local downloads, startup behavior, window pinning, power wake-locks, and GitHub auto-updates.'
    )
  );

  container.appendChild(
    createSettingsGroup('FILE STORAGE & EXPORT', [
      renderDownloadsDirectorySetting(),
      renderAskBeforeDownloadSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('STARTUP & CONNECTION', [
      renderAutoConnectLastDeviceSetting(),
      renderLaunchOnStartupSetting(),
      renderStartMinimizedSetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('WINDOW & DISPLAY BEHAVIOR', [
      renderAlwaysOnTopSetting(),
      renderKeepScreenAwakeSetting(),
      renderSystemTraySetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('DESKTOP ALERT NOTIFICATIONS', renderDesktopAlertSettings())
  );

  container.appendChild(
    createSettingsGroup('APPLICATION UPDATES', renderAutoUpdateSetting())
  );

  return container;
}

/**
 * Initializes bidirectional runtime synchronization between frontend `settingsState`
 * and the Rust native window/tray/power/updater backends, returning a cleanup function.
 */
export function initGeneralSystemRuntime(onOpenSettings?: () => void): () => void {
  const initialCloseAction = settingsState.getValue('closeAction');
  const initialLaunchOnStartup = settingsState.getValue('launchOnStartup');
  const initialStartMinimized = settingsState.getValue('startMinimized');
  const initialAlwaysOnTop = settingsState.getValue('alwaysOnTop');

  // Reconcile Rust backend state on startup so CloseRequested, Autostart, Start Minimized,
  // and Always on Top match saved preferences.
  void (async () => {
    const snapshot = await getWindowTraySettings();
    if (!snapshot) {
      return;
    }

    if (snapshot.closeAction === initialCloseAction) {
      markCloseActionSynced(initialCloseAction);
    } else {
      await applyCloseActionSetting(initialCloseAction);
    }

    if (snapshot.autostartEnabled === initialLaunchOnStartup) {
      markLaunchOnStartupSynced(initialLaunchOnStartup);
    } else {
      await applyLaunchOnStartupSetting(initialLaunchOnStartup);
    }

    if (snapshot.startMinimized === initialStartMinimized) {
      markStartMinimizedSynced(initialStartMinimized);
    } else {
      await applyStartMinimizedSetting(initialStartMinimized);
    }

    if (snapshot.alwaysOnTop === initialAlwaysOnTop) {
      markAlwaysOnTopSynced(initialAlwaysOnTop);
    } else {
      await applyAlwaysOnTopSetting(initialAlwaysOnTop);
    }
  })();

  // Keep Rust synchronized whenever settings mutate (including Reset Defaults)
  const unsubscribeValues = settingsState.onValuesChange((values) => {
    void syncCloseActionIfChanged(values.closeAction);
    void syncLaunchOnStartupIfChanged(values.launchOnStartup);
    void syncStartMinimizedIfChanged(values.startMinimized);
    void syncAlwaysOnTopIfChanged(values.alwaysOnTop);
  });

  const disposeKeepAwake = initKeepScreenAwakeRuntime();
  const disposeAutoUpdate = initAutoUpdateRuntime();
  const disposeDesktopAlerts = initDesktopAlertsRuntime();

  // Listen for "Settings" clicks from the OS system tray menu
  let unlistenTraySettings: (() => void) | null = null;
  let disposed = false;

  void listenTauriEvent(EVENT_TRAY_OPEN_SETTINGS, () => {
    settingsState.setActiveTab('general');
    onOpenSettings?.();
  }).then((unlisten) => {
    if (disposed) {
      unlisten();
    } else {
      unlistenTraySettings = unlisten;
    }
  });

  return () => {
    disposed = true;
    unsubscribeValues();
    disposeKeepAwake();
    disposeAutoUpdate();
    disposeDesktopAlerts();
    if (unlistenTraySettings) {
      unlistenTraySettings();
      unlistenTraySettings = null;
    }
  };
}
