/**
 * @file general-system-features.test.js
 * @description Unit and integration tests for Auto-Connect Last Paired Device,
 *              Always on Top, Keep Screen On When Connected, and GitHub Stable Release
 *              Auto-Updater + Titlebar "Restart to update" button visibility.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createFeatureMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    checked: false,
    disabled: false,
    textContent: '',
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
      const inputRegex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
      let match;
      while ((match = inputRegex.exec(_innerHTML)) !== null) {
        const input = createFeatureMockElement('input');
        input.id = match[1];
        input.checked = /\bchecked\b/.test(match[2]);
        byId[input.id] = input;
      }
      const btnRegex = /<button[^>]*id="([^"]+)"[^>]*>/g;
      while ((match = btnRegex.exec(_innerHTML)) !== null) {
        const btn = createFeatureMockElement('button');
        btn.id = match[1];
        byId[btn.id] = btn;
      }
      const spanRegex = /<span[^>]*id="([^"]+)"[^>]*>/g;
      while ((match = spanRegex.exec(_innerHTML)) !== null) {
        const span = createFeatureMockElement('span');
        span.id = match[1];
        byId[span.id] = span;
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
      el.children.push(child);
      return child;
    },
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
      if (selector.startsWith('#')) {
        const id = selector.slice(1);
        if (el._parsedById[id]) return el._parsedById[id];
      }
      return null;
    },
  };
  return el;
}

describe('General & System Startup, Window, Power & Auto-Update Tests', () => {
  let mockStorage;
  let invokeCalls;
  let mockImpl;
  let registeredEvents;
  let elementsById;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    registeredEvents = {};
    elementsById = {};
    mockImpl = async () => null;

    const mockBody = createFeatureMockElement('body');
    global.document = {
      body: mockBody,
      createElement: (tag) => createFeatureMockElement(tag),
      getElementById: (id) => elementsById[id] || null,
      addEventListener: () => {},
      removeEventListener: () => {},
    };

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
      __TAURI__: {
        core: {
          invoke: async (cmd, args) => {
            invokeCalls.push({ cmd, args });
            return await mockImpl(cmd, args);
          },
        },
        event: {
          listen: async (eventName, handler) => {
            registeredEvents[eventName] = handler;
            return () => {
              delete registeredEvents[eventName];
            };
          },
        },
      },
    };
  });

  it('auto-connect-last-device.ts toggles setting and executes single clean startup connect only when enabled', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const {
      renderAutoConnectLastDeviceSetting,
      runStartupAutoConnect,
    } = await import(
      '../src/js/settings/main-content/general-system/auto-connect-last-device.js'
    );

    settingsState.resetToDefaults();
    deviceState.disconnect();
    deviceState.saveLastConnectedDevice({
      rawId: '482194021104',
      formattedId: '4821 9402 1104',
      name: 'Lucas Pixel 8',
    });

    const originalConnectLive = deviceState.connectLive.bind(deviceState);
    let connectAttempts = [];
    deviceState.connectLive = async (rawId) => {
      connectAttempts.push(rawId);
      return true;
    };

    try {
      // Disabled by default -> does not connect on startup
      const skipped = await runStartupAutoConnect();
      assert.strictEqual(skipped, false);
      assert.strictEqual(connectAttempts.length, 0);

      // Enable toggle
      const row = renderAutoConnectLastDeviceSetting();
      const toggle = row.querySelector('#setting-auto-connect-last-device');
      toggle.checked = true;
      await toggle.dispatchEvent('change');
      assert.strictEqual(settingsState.getValue('autoConnectLastDevice'), true);

      // Now startup auto-connect attempts exactly once with saved rawId
      const ok = await runStartupAutoConnect();
      assert.strictEqual(ok, true);
      assert.deepStrictEqual(connectAttempts, ['482194021104']);

      // Offline failure is handled gracefully without throwing
      deviceState.connectLive = async () => {
        throw new Error('Child offline');
      };
      const offlineResult = await runStartupAutoConnect();
      assert.strictEqual(offlineResult, false);
    } finally {
      deviceState.connectLive = originalConnectLive;
      deviceState.clearLastConnectedDevice();
    }
  });

  it('always-on-top.ts invokes Rust set_always_on_top and reverts checkbox on failure', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderAlwaysOnTopSetting } = await import(
      '../src/js/settings/main-content/general-system/always-on-top.js'
    );

    settingsState.resetToDefaults();
    const row = renderAlwaysOnTopSetting();
    const toggle = row.querySelector('#setting-always-on-top');

    toggle.checked = true;
    await toggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('alwaysOnTop'), true);
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_always_on_top', args: { enabled: true } },
    ]);

    mockImpl = async (cmd) => {
      if (cmd === 'set_always_on_top') throw new Error('Window unavailable');
      return null;
    };
    toggle.checked = false;
    await toggle.dispatchEvent('change');
    assert.strictEqual(toggle.checked, true);
    assert.strictEqual(settingsState.getValue('alwaysOnTop'), true);
  });

  it('keep-screen-awake.ts activates wake lock whenever connected (even without media streams) and releases on disconnect', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const {
      computeShouldKeepScreenAwake,
      initKeepScreenAwakeRuntime,
      renderKeepScreenAwakeSetting,
      resetKeepScreenAwakeSyncCache,
    } = await import(
      '../src/js/settings/main-content/general-system/keep-screen-awake.js'
    );

    assert.strictEqual(computeShouldKeepScreenAwake(true, false, false), false);
    assert.strictEqual(computeShouldKeepScreenAwake(true, true, false), true);
    assert.strictEqual(computeShouldKeepScreenAwake(true, false, true), true);
    assert.strictEqual(computeShouldKeepScreenAwake(false, true, true), false);

    settingsState.resetToDefaults();
    deviceState.disconnect();
    resetKeepScreenAwakeSyncCache();
    invokeCalls = [];

    const dispose = initKeepScreenAwakeRuntime();
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_keep_screen_awake', args: { enabled: false } },
    ]);

    // Connecting child device (without any camera/mic/screen stream) activates wake lock
    invokeCalls = [];
    deviceState.connect('482194021104');
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_keep_screen_awake', args: { enabled: true } },
    ]);

    // Toggling off in UI releases wake lock immediately while still connected
    invokeCalls = [];
    const row = renderKeepScreenAwakeSetting();
    const toggle = row.querySelector('#setting-keep-screen-awake');
    toggle.checked = false;
    await toggle.dispatchEvent('change');
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(settingsState.getValue('keepScreenAwake'), false);
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_keep_screen_awake', args: { enabled: false } },
    ]);

    // Toggling back on while connected re-activates wake lock; disconnecting releases it
    invokeCalls = [];
    toggle.checked = true;
    await toggle.dispatchEvent('change');
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_keep_screen_awake', args: { enabled: true } },
    ]);

    invokeCalls = [];
    deviceState.disconnect();
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_keep_screen_awake', args: { enabled: false } },
    ]);

    dispose();
  });

  it('Titlebar "Restart to update" button stays hidden when up to date and appears only when ready', async () => {
    const { setupTitlebarUpdateButton } = await import('../src/js/titlebar/updater-ui.js');
    const { EVENT_UPDATE_STATUS } = await import(
      '../src/js/settings/main-content/general-system/auto-update.js'
    );
    const { deviceState } = await import('../src/js/shared/device-state.js');

    const btn = createFeatureMockElement('button');
    btn.id = 'btn-restart-to-update';
    btn.className = 'titlebar-update-btn hidden';
    elementsById['btn-restart-to-update'] = btn;

    mockImpl = async (cmd) => {
      if (cmd === 'get_update_status') {
        return {
          status: 'up-to-date',
          currentVersion: '0.1.0',
          availableVersion: null,
          releaseUrl: null,
          sha256Verified: false,
          errorMessage: null,
        };
      }
      return null;
    };

    const dispose = setupTitlebarUpdateButton();
    await new Promise((r) => setTimeout(r, 15));

    // Up to date -> button remains hidden
    assert.strictEqual(btn.classList.contains('hidden'), true);
    assert.ok(registeredEvents[EVENT_UPDATE_STATUS]);

    // Emit downloading -> still hidden
    registeredEvents[EVENT_UPDATE_STATUS]({
      event: EVENT_UPDATE_STATUS,
      payload: {
        status: 'downloading',
        currentVersion: '0.1.0',
        availableVersion: '0.2.0',
        releaseUrl: null,
        sha256Verified: false,
        errorMessage: null,
      },
    });
    assert.strictEqual(btn.classList.contains('hidden'), true);

    // Emit ready -> button becomes visible with version tooltip
    registeredEvents[EVENT_UPDATE_STATUS]({
      event: EVENT_UPDATE_STATUS,
      payload: {
        status: 'ready',
        currentVersion: '0.1.0',
        availableVersion: '0.2.0',
        releaseUrl: 'https://github.com/lukagray-dev/Nexus/releases/tag/v0.2.0',
        sha256Verified: true,
        errorMessage: null,
      },
    });
    assert.strictEqual(btn.classList.contains('hidden'), false);
    assert.strictEqual(btn.getAttribute('title'), 'Restart to update to v0.2.0');

    // Clicking button disconnects active device and triggers restart_to_update
    deviceState.connect('482194021104');
    invokeCalls = [];
    await btn.dispatchEvent('click');
    assert.strictEqual(deviceState.isConnected(), false);
    assert.deepStrictEqual(invokeCalls, [{ cmd: 'restart_to_update', args: undefined }]);

    dispose();
  });
});
