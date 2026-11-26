/**
 * @file settings.ts
 * @description Master Settings Dialog & Layout Orchestrator for Nexus Parent.
 *
 * Hey friend! This orchestrator manages the master-detail Settings modal:
 * 1. Hosts a two-pane layout: a dedicated left navigation sidebar (`sidebar/`) and
 *    modular category content wired via `main-content/main-content.ts`.
 * 2. Wires activation triggers: Left Sidebar Settings button, Titlebar File -> Settings,
 *    System Tray -> Settings, and the global `Ctrl+,` keyboard shortcut.
 * 3. Dynamically hot-swaps category content views when clicking sidebar tabs without modal flicker.
 * 4. Implements the Instant Auto-Save model with a Reset Defaults recovery button.
 * 5. Handles smooth keyboard dismissal with Escape and click-outside overlay detection.
 */

import { settingsState } from './sidebar/state.js';
import { SettingsTabId } from './sidebar/types.js';
import { SettingsSidebar } from './sidebar/sidebar.js';
import {
  initSettingsMainContentRuntime,
  mountSettingsMainContent,
} from './main-content/main-content.js';
import { showToast } from '../shared/toast.js';

let activeSidebarInstance: SettingsSidebar | null = null;
let unbindTabListener: (() => void) | null = null;
let disposeRuntime: (() => void) | null = null;

/**
 * Initializes global settings triggers, keyboard shortcuts, and native runtime sync.
 */
export function initSettings(): void {
  // 1. Initialize native runtime integrations (window close-to-tray, autostart, tray menu)
  if (disposeRuntime) {
    disposeRuntime();
  }
  disposeRuntime = initSettingsMainContentRuntime(() => {
    openSettingsDialog('general');
  });

  // 2. Left sidebar Settings button
  const sidebarBtn = document.getElementById('btn-sidebar-settings');
  sidebarBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    openSettingsDialog();
  });

  // 3. Global keyboard shortcut (Ctrl+, or Cmd+,)
  document.addEventListener('keydown', (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === ',') {
      e.preventDefault();
      openSettingsDialog();
    }
  });
}

/**
 * Opens the interactive master-detail settings modal dialog, optionally focusing a tab.
 */
export function openSettingsDialog(initialTab?: SettingsTabId): void {
  if (initialTab) {
    settingsState.setActiveTab(initialTab);
  }

  const existing = document.getElementById('settings-dialog-overlay');
  if (existing) {
    existing.focus();
    return;
  }

  const overlay = document.createElement('div');
  overlay.id = 'settings-dialog-overlay';
  overlay.className = 'settings-overlay';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-labelledby', 'settings-modal-title');

  overlay.innerHTML = `
    <div class="settings-modal settings-modal-two-pane">
      <!-- Modal Top Header Bar -->
      <div class="settings-header">
        <div class="settings-header-left">
          <img src="assets/icons/settings/settings.svg" alt="" style="width: 14px; height: 14px; filter: invert(1); opacity: 0.8;" />
          <span id="settings-modal-title" class="settings-title">Settings</span>
        </div>
        <button id="btn-settings-close" class="settings-close-btn" title="Close Settings (Escape)">✕</button>
      </div>

      <!-- Two-Pane Master-Detail Body -->
      <div class="settings-two-pane-body">
        <!-- Left Pane: Navigation Sidebar -->
        <div id="settings-sidebar-mount" class="settings-sidebar-container"></div>

        <!-- Right Pane: Category Content Area -->
        <div id="settings-content-mount" class="settings-content-area"></div>
      </div>

      <!-- Modal Bottom Action Footer -->
      <div class="settings-footer">
        <button id="btn-settings-reset" class="settings-btn settings-btn-secondary" title="Reset all settings to factory defaults">
          <img src="assets/icons/settings/refresh-cw.svg" alt="" style="width: 11px; height: 11px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
          Reset Defaults
        </button>
        <button id="btn-settings-done" class="settings-btn settings-btn-primary">Done</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  // Mount Left Navigation Sidebar
  const sidebarMount = overlay.querySelector<HTMLElement>('#settings-sidebar-mount');
  if (sidebarMount) {
    activeSidebarInstance = new SettingsSidebar();
    sidebarMount.appendChild(activeSidebarInstance.render());
  }

  // Mount Initial Category Content View
  const contentMount = overlay.querySelector<HTMLElement>('#settings-content-mount');
  if (contentMount) {
    mountSettingsMainContent(contentMount, settingsState.getActiveTab());

    // Listen to tab changes and re-render content area smoothly
    unbindTabListener = settingsState.onTabChange((newTabId: SettingsTabId) => {
      mountSettingsMainContent(contentMount, newTabId);
    });
  }

  // Wire Reset Defaults Button
  const resetBtn = overlay.querySelector<HTMLButtonElement>('#btn-settings-reset');
  resetBtn?.addEventListener('click', () => {
    settingsState.resetToDefaults();
    if (contentMount) {
      mountSettingsMainContent(contentMount, settingsState.getActiveTab());
    }
    showToast('All settings restored to factory defaults');
  });

  // Wire Close Actions
  const closeBtn = overlay.querySelector<HTMLButtonElement>('#btn-settings-close');
  const doneBtn = overlay.querySelector<HTMLButtonElement>('#btn-settings-done');

  closeBtn?.addEventListener('click', closeSettingsDialog);
  doneBtn?.addEventListener('click', closeSettingsDialog);

  // Close on outside overlay click
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      closeSettingsDialog();
    }
  });

  // Close on Escape key
  const onEscape = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      closeSettingsDialog();
    }
  };
  document.addEventListener('keydown', onEscape);

  // Attach teardown hook to clean up Escape listener when modal unmounts
  (overlay as HTMLElement & { _cleanupEscape?: () => void })._cleanupEscape = () => {
    document.removeEventListener('keydown', onEscape);
  };
}

/**
 * Closes and unmounts the active settings modal dialog.
 */
export function closeSettingsDialog(): void {
  const overlay = document.getElementById('settings-dialog-overlay') as
    | (HTMLElement & { _cleanupEscape?: () => void })
    | null;

  if (!overlay) return;

  if (overlay._cleanupEscape) {
    overlay._cleanupEscape();
  }

  if (unbindTabListener) {
    unbindTabListener();
    unbindTabListener = null;
  }

  if (activeSidebarInstance) {
    activeSidebarInstance.destroy();
    activeSidebarInstance = null;
  }

  if (overlay.parentElement) {
    overlay.parentElement.removeChild(overlay);
  }
}
