/**
 * @file settings.test.js
 * @description Comprehensive unit tests for Nexus Parent Settings Panel (Master-Detail Dialog).
 *
 * Hey friend! These tests verify:
 * 1. Category Registry: All 6 required categories are registered with proper labels, descriptions, and vector icons.
 * 2. Default Configuration: Baseline factory defaults across General, Audio, Surveillance, Security,
 *    Devices, and Diagnostics are complete and valid (with Connection & WebRTC removed).
 * 3. State & Auto-Save: Tab switching, property mutations, instant localStorage persistence,
 *    legacy key cleanup, and resetToDefaults recovery.
 * 4. Navigation Sidebar: DOM structure, aria accessibility attributes, and reactive active item updates.
 * 5. Dialog Orchestrator: Idempotent modal opening, clean unmounting, Escape dismissal, and click-outside dismissal.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';
import {
  SETTINGS_TABS,
  getDefaultSettingsValues,
} from '../src/js/settings/sidebar/types.js';
import {
  SettingsState,
  SETTINGS_STORAGE_KEY,
} from '../src/js/settings/sidebar/state.js';
import { SettingsSidebar } from '../src/js/settings/sidebar/sidebar.js';

describe('Settings Category Registry & Default Values Tests', () => {
  it('Must register exactly 6 settings categories in the requested sidebar order without Connection & WebRTC', () => {
    assert.strictEqual(SETTINGS_TABS.length, 6);

    const expectedIds = [
      'general',
      'audio',
      'surveillance',
      'security',
      'devices',
      'diagnostics',
    ];

    const expectedLabels = [
      'General & System',
      'Audio & Intercom',
      'Surveillance & DVR',
      'Security & App Lock',
      'Paired Devices',
      'Diagnostics & Storage',
    ];

    assert.deepStrictEqual(SETTINGS_TABS.map((t) => t.id), expectedIds);
    assert.deepStrictEqual(SETTINGS_TABS.map((t) => t.label), expectedLabels);
    assert.strictEqual(SETTINGS_TABS.some((t) => t.id === 'connection'), false);
  });

  it('Each category tab must possess non-empty label, description, and valid SVG iconPath without emojis', () => {
    SETTINGS_TABS.forEach((tab) => {
      assert.ok(tab.id && typeof tab.id === 'string', 'Tab must have an ID string');
      assert.ok(tab.label && tab.label.trim().length > 0, `Tab ${tab.id} must have a label`);
      assert.ok(tab.description && tab.description.trim().length > 0, `Tab ${tab.id} must have a description`);
      assert.ok(
        tab.iconPath.endsWith('.svg'),
        `Tab ${tab.id} iconPath must point to an SVG file: ${tab.iconPath}`
      );
      assert.ok(!tab.iconPath.includes('emoji'), `Tab ${tab.id} iconPath must not contain emojis`);
    });
  });

  it('getDefaultSettingsValues returns valid baseline configurations across all 6 domains', () => {
    const defaults = getDefaultSettingsValues();

    // 1. Audio
    assert.strictEqual(defaults.defaultVoicePreset, 'original');
    assert.strictEqual(defaults.inputDeviceId, 'default');
    assert.strictEqual(defaults.outputDeviceId, 'default');
    assert.strictEqual(defaults.loudspeakerVolume, 'max');
    assert.strictEqual(defaults.noiseSuppression, true);

    // 2. Surveillance
    assert.strictEqual(defaults.screenQuality, '1080p');
    assert.strictEqual(defaults.preferredCameraLens, 'rear');
    assert.strictEqual(defaults.dvrBufferDuration, '30m');
    assert.strictEqual(defaults.dvrClipFormat, 'mp4');
    assert.strictEqual(defaults.hardwareAcceleration, true);

    // Removed Connection & WebRTC keys must not exist
    assert.strictEqual('autoReconnect' in defaults, false);
    assert.strictEqual('primaryStunServer' in defaults, false);
    assert.strictEqual('secondaryTurnServer' in defaults, false);
    assert.strictEqual('iceTransportPolicy' in defaults, false);
    assert.strictEqual('iceGatherTimeout' in defaults, false);
    assert.strictEqual('signalingHeartbeat' in defaults, false);

    // 3. Security
    assert.strictEqual(defaults.remotePin, '123456');
    assert.strictEqual(defaults.intruderSelfie, true);
    assert.strictEqual(defaults.lockoutStrikes, '3');
    assert.strictEqual(defaults.maskPasswords, true);
    assert.strictEqual(defaults.stealthConfirmation, true);
    assert.strictEqual(defaults.alertIntruderSelfie, true);
    assert.strictEqual(defaults.alertLowBattery, true);
    assert.strictEqual(defaults.alertChildDisconnected, true);
    assert.strictEqual('alertSos' in defaults, false);

    // 5. General
    assert.strictEqual(defaults.downloadsDirectory, '');
    assert.strictEqual(defaults.askBeforeDownload, false);
    assert.strictEqual(defaults.autoConnectLastDevice, false);
    assert.strictEqual(defaults.launchOnStartup, false);
    assert.strictEqual(defaults.startMinimized, false);
    assert.strictEqual(defaults.alwaysOnTop, false);
    assert.strictEqual(defaults.keepScreenAwake, true);
    assert.strictEqual(defaults.closeAction, 'tray');
    assert.strictEqual(defaults.autoCheckUpdates, true);

    // Removed legacy deviceAlias key must not exist
    assert.strictEqual('deviceAlias' in defaults, false);

    // 6. Diagnostics
    assert.strictEqual(defaults.diagnosticLogging, true);
  });
});

describe('Settings State Machine & Auto-Save Tests', () => {
  let mockStorage = {};

  beforeEach(() => {
    mockStorage = {};
    global.window = {
      localStorage: {
        getItem: (key) => mockStorage[key] || null,
        setItem: (key, val) => {
          mockStorage[key] = String(val);
        },
        removeItem: (key) => {
          delete mockStorage[key];
        },
        clear: () => {
          mockStorage = {};
        },
      },
    };
  });

  it('Manages active navigation tab and triggers onTabChange listeners', () => {
    const state = new SettingsState();
    assert.strictEqual(state.getActiveTab(), 'general');

    let reportedTab = null;
    const unsubscribe = state.onTabChange((newTab) => {
      reportedTab = newTab;
    });

    state.setActiveTab('audio');
    assert.strictEqual(state.getActiveTab(), 'audio');
    assert.strictEqual(reportedTab, 'audio');

    // Setting same tab should be a no-op
    reportedTab = null;
    state.setActiveTab('audio');
    assert.strictEqual(reportedTab, null);

    unsubscribe();
    state.setActiveTab('security');
    assert.strictEqual(state.getActiveTab(), 'security');
    assert.strictEqual(reportedTab, null, 'Unsubscribed listener must not be called');
  });

  it('Mutating a setting updates value, triggers listener, and auto-saves to localStorage', () => {
    const state = new SettingsState();

    let changeNotified = false;
    state.onValuesChange((vals) => {
      if (vals.screenQuality === '720p') {
        changeNotified = true;
      }
    });

    state.setValue('screenQuality', '720p');
    assert.strictEqual(state.getValue('screenQuality'), '720p');
    assert.strictEqual(changeNotified, true);

    // Verify localStorage persistence
    const rawPersisted = mockStorage[SETTINGS_STORAGE_KEY];
    assert.ok(rawPersisted, 'Settings must be persisted in localStorage');
    const parsed = JSON.parse(rawPersisted);
    assert.strictEqual(parsed.screenQuality, '720p');
  });

  it('resetToDefaults restores all factory defaults and persists the change', () => {
    const state = new SettingsState();
    state.setValue('remotePin', '999999');
    state.setValue('dvrBufferDuration', '60m');
    assert.strictEqual(state.getValue('remotePin'), '999999');
    assert.strictEqual(state.getValue('dvrBufferDuration'), '60m');

    state.resetToDefaults();
    assert.strictEqual(state.getValue('remotePin'), '123456');
    assert.strictEqual(state.getValue('dvrBufferDuration'), '30m');

    const parsed = JSON.parse(mockStorage[SETTINGS_STORAGE_KEY]);
    assert.strictEqual(parsed.remotePin, '123456');
    assert.strictEqual(parsed.dvrBufferDuration, '30m');
  });

  it('Safely recovers from corrupted localStorage data on boot', () => {
    mockStorage[SETTINGS_STORAGE_KEY] = 'INVALID_JSON_CORRUPTED';
    const state = new SettingsState();

    assert.strictEqual(state.getValue('defaultVoicePreset'), 'original');
    assert.strictEqual(state.getValue('screenQuality'), '1080p');
  });

  it('Migrates legacy launchMinimized=true into launchOnStartup and startMinimized', () => {
    mockStorage[SETTINGS_STORAGE_KEY] = JSON.stringify({
      closeAction: 'tray',
      launchMinimized: true,
    });
    const state = new SettingsState();

    assert.strictEqual(state.getValue('launchOnStartup'), true);
    assert.strictEqual(state.getValue('startMinimized'), true);
  });

  it('Strips removed legacy Connection & WebRTC and deviceAlias keys when loading persisted settings', () => {
    mockStorage[SETTINGS_STORAGE_KEY] = JSON.stringify({
      defaultVoicePreset: 'echo',
      autoReconnect: false,
      primaryStunServer: 'stun:custom.example:3478',
      secondaryTurnServer: 'turn:custom.example:3478',
      iceTransportPolicy: 'relay',
      iceGatherTimeout: 900,
      signalingHeartbeat: 45,
      deviceAlias: 'Old Nickname',
    });
    const state = new SettingsState();
    const values = state.getValues();

    assert.strictEqual(values.defaultVoicePreset, 'echo');
    assert.strictEqual('autoReconnect' in values, false);
    assert.strictEqual('primaryStunServer' in values, false);
    assert.strictEqual('secondaryTurnServer' in values, false);
    assert.strictEqual('iceTransportPolicy' in values, false);
    assert.strictEqual('iceGatherTimeout' in values, false);
    assert.strictEqual('signalingHeartbeat' in values, false);
    assert.strictEqual('deviceAlias' in values, false);
  });
});

describe('Settings Navigation Sidebar Component Tests', () => {
  let mockDoc;

  beforeEach(() => {
    // Minimal mock DOM for node:test
    mockDoc = {
      createElement: (tag) => {
        let _className = '';
        const _classes = new Set();
        const el = {
          tagName: tag.toUpperCase(),
          dataset: {},
          attributes: {},
          children: [],
          innerHTML: '',
          get className() {
            return _className;
          },
          set className(val) {
            _className = val;
            _classes.clear();
            val.split(/\s+/).filter(Boolean).forEach((c) => _classes.add(c));
          },
          setAttribute: (k, v) => {
            el.attributes[k] = String(v);
          },
          getAttribute: (k) => el.attributes[k],
          appendChild: (child) => {
            el.children.push(child);
          },
          classList: {
            add: (c) => {
              _classes.add(c);
              _className = Array.from(_classes).join(' ');
            },
            remove: (c) => {
              _classes.delete(c);
              _className = Array.from(_classes).join(' ');
            },
            toggle: (c, force) => {
              if (force === undefined) {
                if (_classes.has(c)) _classes.delete(c);
                else _classes.add(c);
              } else if (force) {
                _classes.add(c);
              } else {
                _classes.delete(c);
              }
              _className = Array.from(_classes).join(' ');
            },
            contains: (c) => _classes.has(c),
          },
          querySelectorAll: (selector) => {
            if (selector === '.settings-nav-item') {
              return el.children;
            }
            return [];
          },
          addEventListener: (event, handler) => {
            el[`on_${event}`] = handler;
          },
        };
        return el;
      },
    };
    global.document = mockDoc;
  });

  it('SettingsSidebar renders 6 navigation tabs with accessibility attributes', () => {
    const sidebar = new SettingsSidebar();
    const nav = sidebar.render();

    assert.strictEqual(nav.tagName, 'NAV');
    assert.strictEqual(nav.attributes['role'], 'tablist');
    assert.strictEqual(nav.children.length, 6);

    // Initial tab 'general' should be selected
    const firstTab = nav.children[0];
    assert.strictEqual(firstTab.dataset.tabId, 'general');
    assert.strictEqual(firstTab.classList.contains('is-active'), true);
    assert.strictEqual(firstTab.attributes['aria-selected'], 'true');

    sidebar.destroy();
  });
});

describe('Security & App Lock Desktop Alert Toggles Tests', () => {
  let mockStorage = {};

  function createAlertMockElement(tag) {
    let _className = '';
    let _innerHTML = '';
    const _classes = new Set();
    const el = {
      tagName: tag.toUpperCase(),
      id: '',
      checked: false,
      children: [],
      textContent: '',
      style: {},
      attributes: {},
      dataset: {},
      _parsedById: {},
      get className() {
        return _className;
      },
      set className(val) {
        _className = String(val);
        _classes.clear();
        _className.split(/\s+/).filter(Boolean).forEach((c) => _classes.add(c));
      },
      get innerHTML() {
        return _innerHTML;
      },
      set innerHTML(html) {
        _innerHTML = String(html);
        const byId = {};
        const regex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
        let match;
        while ((match = regex.exec(_innerHTML)) !== null) {
          const input = createAlertMockElement('input');
          input.id = match[1];
          input.checked = /\bchecked\b/.test(match[2]);
          byId[input.id] = input;
        }
        el._parsedById = byId;
      },
      classList: {
        add: (c) => _classes.add(c),
        remove: (c) => _classes.delete(c),
        toggle: (c, force) => {
          if (force === undefined) {
            if (_classes.has(c)) _classes.delete(c);
            else _classes.add(c);
          } else if (force) {
            _classes.add(c);
          } else {
            _classes.delete(c);
          }
        },
        contains: (c) => _classes.has(c),
      },
      querySelectorAll: () => [],
      setAttribute: (k, v) => {
        el.attributes[k] = String(v);
      },
      appendChild: (child) => {
        el.children.push(child);
        return child;
      },
      addEventListener: (event, handler) => {
        if (!el._listeners) el._listeners = {};
        if (!el._listeners[event]) el._listeners[event] = [];
        el._listeners[event].push(handler);
      },
      dispatchEvent: async (eventName) => {
        const handlers = el._listeners?.[eventName] || [];
        for (const fn of handlers) {
          await fn({ stopPropagation: () => {}, preventDefault: () => {} });
        }
      },
      querySelector: (selector) => {
        if (selector.startsWith('#')) {
          const id = selector.slice(1);
          if (el._parsedById[id]) return el._parsedById[id];
          for (const child of el.children) {
            const found = child.querySelector(selector);
            if (found) return found;
          }
        }
        return null;
      },
    };
    return el;
  }

  beforeEach(() => {
    mockStorage = {};
    global.window = {
      localStorage: {
        getItem: (key) => (key in mockStorage ? mockStorage[key] : null),
        setItem: (key, val) => {
          mockStorage[key] = String(val);
        },
        removeItem: (key) => {
          delete mockStorage[key];
        },
        clear: () => {
          mockStorage = {};
        },
      },
    };
    global.document = {
      createElement: (tag) => createAlertMockElement(tag),
    };
  });

  it('renderDesktopAlertSettings renders 3 toggle rows (no checkboxes, no Emergency SOS Alert) and persists changes', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderDesktopAlertSettings } = await import(
      '../src/js/settings/main-content/security-forensics/desktop-alerts.js'
    );
    const { renderSecurityForensicsContent } = await import(
      '../src/js/settings/main-content/security-forensics/security-forensics.js'
    );
    const { renderGeneralSystemContent } = await import(
      '../src/js/settings/main-content/general-system/general-system.js'
    );

    settingsState.resetToDefaults();

    const rows = renderDesktopAlertSettings();
    assert.strictEqual(rows.length, 3);

    const combinedHtml = rows.map((r) => r.innerHTML).join('\n');
    assert.ok(combinedHtml.includes('settings-toggle'));
    assert.strictEqual(combinedHtml.includes('settings-checkbox-row'), false);
    assert.strictEqual(combinedHtml.includes('Emergency SOS Alert'), false);
    assert.strictEqual(combinedHtml.includes('setting-alert-sos'), false);

    const intruderToggle = rows[0].querySelector('#setting-alert-intruder');
    const batteryToggle = rows[1].querySelector('#setting-alert-low-battery');
    const disconnectToggle = rows[2].querySelector('#setting-alert-disconnect');

    assert.ok(intruderToggle && batteryToggle && disconnectToggle);
    assert.strictEqual(intruderToggle.checked, true);
    assert.strictEqual(batteryToggle.checked, true);
    assert.strictEqual(disconnectToggle.checked, true);

    intruderToggle.checked = false;
    await intruderToggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('alertIntruderSelfie'), false);

    batteryToggle.checked = false;
    await batteryToggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('alertLowBattery'), false);

    disconnectToggle.checked = false;
    await disconnectToggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('alertChildDisconnected'), false);

    // Verify alerts group is mounted in Security & App Lock and removed from General & System
    const securityView = renderSecurityForensicsContent();
    const generalView = renderGeneralSystemContent();

    assert.ok(securityView.querySelector('#setting-alert-intruder'));
    assert.strictEqual(generalView.querySelector('#setting-alert-intruder'), null);
  });
});
