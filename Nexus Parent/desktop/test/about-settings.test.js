/**
 * @file about-settings.test.js
 * @description Comprehensive unit and integration tests for the About Nexus Parent settings page:
 *              - Parent Identity card, live Tauri version/updater status, and Copy System Info
 *              - Live Runtime Environment (Rust get_parent_system_info + browser fallback + folder open)
 *              - Transport, Security, Auth, Session & real measured localStorage footprint
 *              - Credits, AGPLv3 license & external support links
 *              - Two-step Danger Zone workspace reset (disconnects, clears device memory, resets settings)
 *              - Titlebar Help -> About delegation to Settings 'about' tab
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createAboutMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    isConnected: false,
    value: '',
    disabled: false,
    textContent: '',
    dataset: {},
    attributes: {},
    style: {},
    children: [],
    _parsedById: {},
    get className() {
      return _className;
    },
    set className(val) {
      _className = String(val);
      _classes.clear();
      _className
        .split(/\s+/)
        .filter(Boolean)
        .forEach((c) => _classes.add(c));
    },
    get innerHTML() {
      return _innerHTML;
    },
    set innerHTML(html) {
      _innerHTML = String(html);
      const byId = {};

      // Parse nested tags with id attributes cleanly even when wrapped inside parent tags (e.g. <span> inside <button>)
      const openTagRegex = /<(button|span|div|img)([^>]*\bid="([^"]+)"[^>]*)>/g;
      let match;
      while ((match = openTagRegex.exec(_innerHTML)) !== null) {
        const tagName = match[1];
        const id = match[3];
        if (byId[id]) continue;
        const child = createAboutMockElement(tagName);
        child.id = id;

        if (tagName !== 'img' && !match[2].trim().endsWith('/')) {
          const closeTag = `</${tagName}>`;
          const closeIdx = _innerHTML.indexOf(closeTag, match.index + match[0].length);
          if (closeIdx !== -1) {
            const inner = _innerHTML.slice(match.index + match[0].length, closeIdx);
            child.textContent = inner.replace(/<[^>]+>/g, '').trim();
          }
        }
        byId[id] = child;
      }

      el._parsedById = byId;
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
    setAttribute: (k, v) => {
      el.attributes[k] = String(v);
    },
    getAttribute: (k) => el.attributes[k],
    appendChild: (child) => {
      child.isConnected = true;
      el.children.push(child);
      return child;
    },
    removeChild: (child) => {
      el.children = el.children.filter((c) => c !== child);
    },
    focus: () => {},
    addEventListener: (event, handler) => {
      if (!el._listeners) el._listeners = {};
      if (!el._listeners[event]) el._listeners[event] = [];
      el._listeners[event].push(handler);
    },
    dispatchEvent: async (eventName, payload = {}) => {
      const handlers = el._listeners?.[eventName] || [];
      for (const fn of handlers) {
        await fn({ stopPropagation: () => {}, preventDefault: () => {}, ...payload });
      }
    },
    querySelector: (selector) => {
      const all = el.querySelectorAll(selector);
      return all[0] || null;
    },
    querySelectorAll: (selector) => {
      if (selector.startsWith('#') && el._parsedById[selector.slice(1)]) {
        return [el._parsedById[selector.slice(1)]];
      }
      const results = [];
      for (const child of el.children) {
        if (selector.startsWith('#') && child.id === selector.slice(1)) {
          results.push(child);
        }
        results.push(...child.querySelectorAll(selector));
      }
      return results;
    },
  };

  return el;
}

describe('About Nexus Parent Settings Page Tests', () => {
  let mockStorage;
  let invokeCalls;
  let clipboardCopied;
  let windowListeners;
  let mockImpl;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    clipboardCopied = [];
    windowListeners = new Map();

    mockImpl = async (cmd) => {
      if (cmd === 'get_app_version') return '0.2.0';
      if (cmd === 'get_update_status') {
        return {
          status: 'up-to-date',
          currentVersion: '0.2.0',
          availableVersion: null,
          releaseUrl: null,
          sha256Verified: false,
          errorMessage: null,
        };
      }
      if (cmd === 'get_parent_system_info') {
        return {
          appName: 'Nexus Parent',
          appVersion: '0.2.0',
          identifier: 'com.nexus.parent',
          tauriVersion: '2.5.1',
          webviewVersion: '132.0.2957.127',
          os: 'windows',
          arch: 'x86_64',
          cpuCores: 16,
          exePath: 'C:\\Program Files\\Nexus Parent\\nexus-parent.exe',
          configDir: 'C:\\Users\\Parent\\AppData\\Roaming\\com.nexus.parent',
          localDataDir: 'C:\\Users\\Parent\\AppData\\Local\\com.nexus.parent',
        };
      }
      return null;
    };

    const mockBody = createAboutMockElement('body');

    global.CustomEvent = class MockCustomEvent {
      constructor(type, init = {}) {
        this.type = type;
        this.detail = init.detail;
      }
    };

    global.document = {
      body: mockBody,
      createElement: (tag) => createAboutMockElement(tag),
      getElementById: (id) => mockBody.querySelector(`#${id}`),
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    global.window = {
      localStorage: {
        get length() {
          return Object.keys(mockStorage).length;
        },
        key: (i) => Object.keys(mockStorage)[i] ?? null,
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
      addEventListener: (name, fn) => {
        if (!windowListeners.has(name)) windowListeners.set(name, new Set());
        windowListeners.get(name).add(fn);
      },
      removeEventListener: (name, fn) => {
        windowListeners.get(name)?.delete(fn);
      },
      dispatchEvent: (evt) => {
        const set = windowListeners.get(evt.type);
        if (set) {
          for (const fn of set) {
            fn(evt);
          }
        }
        return true;
      },
      open: () => {},
      __TAURI__: {
        core: {
          invoke: async (cmd, args) => {
            invokeCalls.push({ cmd, args });
            return await mockImpl(cmd, args);
          },
        },
        event: {
          listen: async () => () => {},
        },
      },
    };

    Object.defineProperty(global, 'navigator', {
      configurable: true,
      value: {
        platform: 'Win32',
        hardwareConcurrency: 8,
        userAgent: 'Mozilla/5.0 Edg/132.0.0.0',
        clipboard: {
          writeText: async (txt) => {
            clipboardCopied.push(txt);
          },
        },
      },
    });
  });

  it('renderAboutContent populates live Rust system info, WebView2 version, paths, and copies diagnostics summary', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderAboutContent } = await import('../src/js/settings/main-content/about/about.js');

    settingsState.resetToDefaults();
    settingsState.setValue('downloadsDirectory', 'D:\\NexusDownloads');

    const view = renderAboutContent();
    await new Promise((r) => setTimeout(r, 20));

    assert.strictEqual(view.querySelector('#about-tab-version-badge').textContent, 'v0.2.0');
    assert.strictEqual(view.querySelector('#about-tab-update-badge').textContent, 'v0.2.0 (Latest)');
    assert.strictEqual(view.querySelector('#about-os-val').textContent, 'Windows (x64)');
    assert.strictEqual(view.querySelector('#about-runtime-val').textContent, 'Tauri v2.5.1 (Rust)');
    assert.strictEqual(view.querySelector('#about-webview-val').textContent, 'WebView2 132.0.2957.127');
    assert.strictEqual(view.querySelector('#about-cpu-gpu-val').textContent, '16 Cores • GPU On');
    assert.strictEqual(
      view.querySelector('#about-path-config-val').textContent,
      'C:\\Users\\Parent\\AppData\\Roaming\\com.nexus.parent'
    );
    assert.strictEqual(view.querySelector('#about-path-downloads-val').textContent, 'D:\\NexusDownloads');

    // Open folder invokes Rust open_local_folder
    await view.querySelector('#about-path-config-open').dispatchEvent('click');
    assert.ok(
      invokeCalls.some(
        (c) =>
          c.cmd === 'open_local_folder' &&
          c.args.path === 'C:\\Users\\Parent\\AppData\\Roaming\\com.nexus.parent'
      )
    );

    // Copy System Info writes full summary to clipboard
    await view.querySelector('#btn-about-copy-summary').dispatchEvent('click');
    assert.strictEqual(clipboardCopied.length, 1);
    assert.ok(clipboardCopied[0].includes('Nexus Parent (com.nexus.parent)'));
    assert.ok(clipboardCopied[0].includes('v0.2.0'));
    assert.ok(clipboardCopied[0].includes('132.0.2957.127'));
  });

  it('measureLocalStorageFootprint measures real nexus_* byte size and updates on session & reset', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { measureLocalStorageFootprint } = await import(
      '../src/js/settings/main-content/about/transport-storage.js'
    );
    const { renderAboutContent } = await import('../src/js/settings/main-content/about/about.js');

    settingsState.resetToDefaults();
    deviceState.connect('482194021104');

    const footprint = measureLocalStorageFootprint();
    assert.ok(footprint.bytes > 0);
    assert.ok(footprint.keyCount >= 2);

    const view = renderAboutContent();
    await new Promise((r) => setTimeout(r, 15));

    assert.strictEqual(view.querySelector('#about-session-badge').textContent, 'Connected');
    assert.ok(view.querySelector('#about-session-desc').textContent.includes('4821 9402 1104'));

    deviceState.disconnect();
    deviceState.clearLastConnectedDevice();
  });

  it('Credits & external links buttons invoke openExternalUrl targets', async () => {
    const { renderCreditsLinksGroup } = await import(
      '../src/js/settings/main-content/about/credits-links.js'
    );

    const group = renderCreditsLinksGroup();
    await group.querySelector('#btn-about-link-repo').dispatchEvent('click');
    await group.querySelector('#btn-about-link-docs').dispatchEvent('click');
    await group.querySelector('#btn-about-link-issue').dispatchEvent('click');

    assert.ok(
      invokeCalls.some(
        (c) => c.cmd === 'plugin:opener|open_url' && c.args.url === 'https://github.com/lukagray-dev/Nexus'
      )
    );
    assert.ok(
      invokeCalls.some(
        (c) =>
          c.cmd === 'plugin:opener|open_url' &&
          c.args.url === 'https://github.com/lukagray-dev/Nexus/tree/main/docs'
      )
    );
    assert.ok(
      invokeCalls.some(
        (c) =>
          c.cmd === 'plugin:opener|open_url' &&
          c.args.url === 'https://github.com/lukagray-dev/Nexus/issues'
      )
    );
  });

  it('Two-step Reset Workspace disconnects active peer, clears last device, resets voice & settingsState', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { VOICE_STORAGE_KEY, saveVoicePresetId } = await import(
      '../src/js/main-content/mic/voice-presets.js'
    );
    const { renderWorkspaceResetGroup } = await import(
      '../src/js/settings/main-content/about/workspace-reset.js'
    );

    deviceState.connect('482194021104');
    settingsState.setValue('remotePin', '987654');
    saveVoicePresetId('alien');

    const group = renderWorkspaceResetGroup();
    const resetBtn = group.querySelector('#btn-factory-reset');
    const resetLabel = group.querySelector('#btn-factory-reset-label');

    // First click only arms confirmation (does not wipe yet)
    await resetBtn.dispatchEvent('click');
    assert.strictEqual(resetLabel.textContent, 'Confirm Full Reset?');
    assert.strictEqual(deviceState.isConnected(), true);
    assert.strictEqual(settingsState.getValue('remotePin'), '987654');

    // Second click executes real full workspace reset
    await resetBtn.dispatchEvent('click');
    assert.strictEqual(resetLabel.textContent, 'Reset Workspace');
    assert.strictEqual(deviceState.isConnected(), false);
    assert.strictEqual(deviceState.getLastConnectedDevice(), null);
    assert.strictEqual(settingsState.getValue('remotePin'), '123456');
    assert.strictEqual(mockStorage[VOICE_STORAGE_KEY], 'original');
  });

  it('openAboutDialog delegates directly to Settings on the about tab', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { closeSettingsDialog } = await import('../src/js/settings/settings.js');
    const { openAboutDialog } = await import('../src/js/titlebar/about.js');

    settingsState.setActiveTab('general');
    openAboutDialog();

    assert.strictEqual(settingsState.getActiveTab(), 'about');
    assert.ok(document.getElementById('settings-dialog-overlay'));
    closeSettingsDialog();
  });
});
