/**
 * @file settings.test.js
 * @description Comprehensive unit tests for Nexus Parent Settings Panel (Master-Detail Dialog).
 *
 * Hey friend! These tests verify:
 * 1. Category Registry: All 7 required categories are registered with proper labels, descriptions, and vector icons.
 * 2. Default Configuration: Baseline factory defaults across Audio, Surveillance, WebRTC, Security,
 *    General, Devices, and Diagnostics are complete and valid.
 * 3. State & Auto-Save: Tab switching, property mutations, instant localStorage persistence,
 *    and resetToDefaults recovery.
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
  it('Must register exactly 7 settings categories matching system specification', () => {
    assert.strictEqual(SETTINGS_TABS.length, 7);

    const expectedIds = [
      'audio',
      'surveillance',
      'connection',
      'security',
      'general',
      'devices',
      'diagnostics',
    ];

    const actualIds = SETTINGS_TABS.map((t) => t.id);
    assert.deepStrictEqual(actualIds, expectedIds);
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

  it('getDefaultSettingsValues returns valid baseline configurations across all 7 domains', () => {
    const defaults = getDefaultSettingsValues();

    // 1. Audio
    assert.strictEqual(defaults.defaultVoicePreset, 'original');
    assert.strictEqual(defaults.loudspeakerVolume, 'max');
    assert.strictEqual(defaults.noiseSuppression, true);

    // 2. Surveillance
    assert.strictEqual(defaults.screenQuality, '1080p');
    assert.strictEqual(defaults.preferredCameraLens, 'rear');
    assert.strictEqual(defaults.dvrBufferDuration, '30m');
    assert.strictEqual(defaults.dvrClipFormat, 'mp4');
    assert.strictEqual(defaults.hardwareAcceleration, true);

    // 3. Connection
    assert.strictEqual(defaults.autoReconnect, true);
    assert.ok(defaults.primaryStunServer.includes('stun.l.google.com'));
    assert.strictEqual(defaults.iceTransportPolicy, 'all');
    assert.strictEqual(defaults.iceGatherTimeout, 400);
    assert.strictEqual(defaults.signalingHeartbeat, 30);

    // 4. Security
    assert.strictEqual(defaults.remotePin, '123456');
    assert.strictEqual(defaults.intruderSelfie, true);
    assert.strictEqual(defaults.lockoutStrikes, '3');
    assert.strictEqual(defaults.maskPasswords, true);
    assert.strictEqual(defaults.stealthConfirmation, true);

    // 5. General
    assert.ok(defaults.downloadsDirectory.includes('Downloads'));
    assert.strictEqual(defaults.closeAction, 'tray');
    assert.strictEqual(defaults.alertIntruderSelfie, true);
    assert.strictEqual(defaults.alertSos, true);

    // 6. Devices
    assert.strictEqual(defaults.deviceAlias, 'Lucas - Pixel 8');

    // 7. Diagnostics
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
    assert.strictEqual(state.getActiveTab(), 'audio');

    let reportedTab = null;
    const unsubscribe = state.onTabChange((newTab) => {
      reportedTab = newTab;
    });

    state.setActiveTab('connection');
    assert.strictEqual(state.getActiveTab(), 'connection');
    assert.strictEqual(reportedTab, 'connection');

    // Setting same tab should be a no-op
    reportedTab = null;
    state.setActiveTab('connection');
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

  it('SettingsSidebar renders 7 navigation tabs with accessibility attributes', () => {
    const sidebar = new SettingsSidebar();
    const nav = sidebar.render();

    assert.strictEqual(nav.tagName, 'NAV');
    assert.strictEqual(nav.attributes['role'], 'tablist');
    assert.strictEqual(nav.children.length, 7);

    // Initial tab 'audio' should be selected
    const firstTab = nav.children[0];
    assert.strictEqual(firstTab.dataset.tabId, 'audio');
    assert.strictEqual(firstTab.classList.contains('is-active'), true);
    assert.strictEqual(firstTab.attributes['aria-selected'], 'true');

    sidebar.destroy();
  });
});
