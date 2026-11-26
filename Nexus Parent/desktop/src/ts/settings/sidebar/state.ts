/**
 * @file state.ts
 * @description State machine and LocalStorage persistence for the Settings Panel.
 *
 * Hey friend! This singleton:
 * 1. Tracks the currently active navigation tab (General, Audio, Surveillance, Devices, About).
 * 2. Implements the Instant Auto-Save model: whenever the user toggles a switch,
 *    selects a dropdown, or types in an input, values are instantly saved to localStorage.
 * 3. Provides clean subscription handles so the view re-renders smoothly.
 * 4. Provides a resetToDefaults() method for one-click factory restore.
 */

import {
  SettingsTabId,
  SettingsValues,
  getDefaultSettingsValues,
} from './types.js';

export const SETTINGS_STORAGE_KEY = 'nexus_parent_settings_v1';

/**
 * Legacy settings keys removed from the schema (Connection & WebRTC, Security & App Lock, Device Alias, Diagnostics).
 * Stripped on load so stale dummy entries do not remain in localStorage.
 */
const REMOVED_LEGACY_SETTING_KEYS = [
  'autoReconnect',
  'primaryStunServer',
  'secondaryTurnServer',
  'iceTransportPolicy',
  'iceGatherTimeout',
  'signalingHeartbeat',
  'remotePin',
  'intruderSelfie',
  'lockoutStrikes',
  'maskPasswords',
  'stealthConfirmation',
  'deviceAlias',
  'diagnosticLogging',
] as const;

export class SettingsState {
  private activeTab: SettingsTabId = 'general';
  private values: SettingsValues;
  private tabChangeListeners: Array<(tabId: SettingsTabId) => void> = [];
  private valueChangeListeners: Array<(values: SettingsValues) => void> = [];

  constructor() {
    this.values = this.loadPersistedValues();
  }

  /**
   * Retrieves the currently active settings navigation tab ID.
   */
  public getActiveTab(): SettingsTabId {
    return this.activeTab;
  }

  /**
   * Switches the active settings tab and notifies all registered listeners.
   */
  public setActiveTab(tabId: SettingsTabId): void {
    if (this.activeTab === tabId) return;
    this.activeTab = tabId;
    this.tabChangeListeners.forEach((listener) => {
      try {
        listener(tabId);
      } catch (err) {
        console.error('[SettingsState] Listener error on tab change:', err);
      }
    });
  }

  /**
   * Subscribes to active tab change events.
   *
   * @returns Unsubscribe function to release the listener.
   */
  public onTabChange(callback: (tabId: SettingsTabId) => void): () => void {
    this.tabChangeListeners.push(callback);
    return () => {
      this.tabChangeListeners = this.tabChangeListeners.filter((cb) => cb !== callback);
    };
  }

  /**
   * Retrieves the full immutable settings values snapshot.
   */
  public getValues(): Readonly<SettingsValues> {
    return { ...this.values };
  }

  /**
   * Retrieves a specific settings property value.
   */
  public getValue<K extends keyof SettingsValues>(key: K): SettingsValues[K] {
    return this.values[key];
  }

  /**
   * Updates a specific settings property, triggers instant auto-save to localStorage,
   * and notifies all value listeners.
   */
  public setValue<K extends keyof SettingsValues>(key: K, value: SettingsValues[K]): void {
    if (this.values[key] === value) return;
    this.values[key] = value;
    this.savePersistedValues();

    this.valueChangeListeners.forEach((listener) => {
      try {
        listener(this.getValues());
      } catch (err) {
        console.error('[SettingsState] Listener error on value change:', err);
      }
    });
  }

  /**
   * Subscribes to settings values mutation events.
   *
   * @returns Unsubscribe function.
   */
  public onValuesChange(callback: (values: SettingsValues) => void): () => void {
    this.valueChangeListeners.push(callback);
    return () => {
      this.valueChangeListeners = this.valueChangeListeners.filter((cb) => cb !== callback);
    };
  }

  /**
   * Resets all configuration values to their factory defaults and persists the change.
   */
  public resetToDefaults(): void {
    console.log('[SettingsState] Resetting all settings to factory defaults...');
    this.values = getDefaultSettingsValues();
    this.savePersistedValues();

    this.valueChangeListeners.forEach((listener) => {
      try {
        listener(this.getValues());
      } catch (err) {
        console.error('[SettingsState] Listener error on reset:', err);
      }
    });
  }

  /**
   * Loads persisted settings from localStorage with safe fallback for corrupted values.
   */
  private loadPersistedValues(): SettingsValues {
    const defaults = getDefaultSettingsValues();
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const raw = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as Record<string, unknown>;
          const merged = { ...defaults, ...parsed } as SettingsValues & {
            launchMinimized?: boolean;
          };

          // Migrate legacy combined `launchMinimized` preference cleanly into the separated toggles
          if (parsed.launchMinimized === true) {
            if (parsed.launchOnStartup === undefined) {
              merged.launchOnStartup = true;
            }
            if (parsed.startMinimized === undefined) {
              merged.startMinimized = true;
            }
          }
          delete merged.launchMinimized;

          // Strip legacy Connection & WebRTC keys removed from the Settings schema
          const mutableRecord = merged as unknown as Record<string, unknown>;
          for (const legacyKey of REMOVED_LEGACY_SETTING_KEYS) {
            delete mutableRecord[legacyKey];
          }

          return merged;
        }
      }
    } catch (err) {
      console.warn('[SettingsState] Failed to read settings from localStorage, using defaults:', err);
    }
    return defaults;
  }

  /**
   * Serializes current values to localStorage.
   */
  private savePersistedValues(): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(this.values));
      }
    } catch (err) {
      console.warn('[SettingsState] Failed to write settings to localStorage:', err);
    }
  }
}

/**
 * Singleton state instance for Settings.
 */
export const settingsState = new SettingsState();
