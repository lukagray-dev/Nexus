/**
 * @file general-system.ts
 * @description General & System Settings category compositor and runtime synchronizer.
 *
 * Hey friend! This file:
 * 1. Composes the General & System view from individual per-setting modules.
 * 2. Synchronizes `closeAction` and `launchMinimized` between `settingsState` and Rust on boot
 *    and whenever settings change (including Reset Defaults).
 * 3. Listens for the system tray's `tray://open-settings` menu action to open Settings.
 */

import { settingsState } from '../../sidebar/state.js';
import {
  getWindowTraySettings,
  listenTauriEvent,
} from '../../../shared/ipc.js';
import { createSettingsGroup, createSettingsSectionHeader } from '../helpers.js';
import { renderDownloadsDirectorySetting } from './downloads-directory.js';
import {
  applyLaunchMinimizedSetting,
  markLaunchMinimizedSynced,
  renderLaunchMinimizedSetting,
  syncLaunchMinimizedIfChanged,
} from './launch-minimized.js';
import {
  applyCloseActionSetting,
  markCloseActionSynced,
  renderSystemTraySetting,
  syncCloseActionIfChanged,
} from './system-tray.js';
import { renderDesktopAlertsGroup } from './desktop-alerts.js';

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
      'Configure local export folders, native window close actions, system tray behavior, and desktop notifications.'
    )
  );

  container.appendChild(
    createSettingsGroup('FILE STORAGE & EXPORT', [renderDownloadsDirectorySetting()])
  );

  container.appendChild(
    createSettingsGroup('DESKTOP WINDOW & TRAY BEHAVIOR', [
      renderLaunchMinimizedSetting(),
      renderSystemTraySetting(),
    ])
  );

  container.appendChild(
    createSettingsGroup('DESKTOP ALERT NOTIFICATIONS', [renderDesktopAlertsGroup()])
  );

  return container;
}

/**
 * Initializes bidirectional runtime synchronization between frontend `settingsState`
 * and the Rust native window/tray backend, returning a cleanup function.
 */
export function initGeneralSystemRuntime(onOpenSettings?: () => void): () => void {
  const initialCloseAction = settingsState.getValue('closeAction');
  const initialLaunchMinimized = settingsState.getValue('launchMinimized');

  // Reconcile Rust backend state on startup so CloseRequested and Autostart match saved preferences
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

    if (snapshot.autostartEnabled === initialLaunchMinimized) {
      markLaunchMinimizedSynced(initialLaunchMinimized);
    } else {
      await applyLaunchMinimizedSetting(initialLaunchMinimized);
    }
  })();

  // Keep Rust synchronized whenever settings mutate (including Reset Defaults)
  const unsubscribeValues = settingsState.onValuesChange((values) => {
    void syncCloseActionIfChanged(values.closeAction);
    void syncLaunchMinimizedIfChanged(values.launchMinimized);
  });

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
    if (unlistenTraySettings) {
      unlistenTraySettings();
      unlistenTraySettings = null;
    }
  };
}
