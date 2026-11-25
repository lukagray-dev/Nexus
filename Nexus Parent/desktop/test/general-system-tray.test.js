/**
 * @file general-system-tray.test.js
 * @description Comprehensive unit and integration tests for Nexus Parent's modular
 *              settings `main-content` wiring, General & System desktop window/tray
 *              settings, separated OS startup & minimized toggles, Always on Top sync,
 *              and exit vs minimize-to-tray IPC flows.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    type: '',
    value: '',
    checked: false,
    disabled: false,
    dataset: {},
    attributes: {},
    style: {},
    children: [],
    textContent: '',
    get className() {
      return _className;
    },
    set className(val) {
      _className = val;
      _classes.clear();
      String(val)
        .split(/\s+/)
        .filter(Boolean)
        .forEach((c) => _classes.add(c));
    },
    get innerHTML() {
      return _innerHTML;
    },
    set innerHTML(html) {
      _innerHTML = String(html);
      el._parsedControls = parseControlsFromHtml(_innerHTML);
    },
    _parsedControls: null,
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
      const all = el.querySelectorAll(selector);
      return all[0] || null;
    },
    querySelectorAll: (selector) => {
      if (el._parsedControls) {
        if (selector === '#setting-close-action .settings-segmented-btn') {
          return el._parsedControls.closeActionButtons;
        }
        if (selector.startsWith('#')) {
          const targetId = selector.slice(1);
          const direct = el._parsedControls.byId[targetId];
          if (direct) return [direct];
        }
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

function parseControlsFromHtml(html) {
  const byId = {};
  const closeActionButtons = [];

  const checkboxRegex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
  let match;
  while ((match = checkboxRegex.exec(html)) !== null) {
    const id = match[1];
    const rest = match[2];
    const input = createMockElement('input');
    input.id = id;
    input.type = 'checkbox';
    input.checked = /\bchecked\b/.test(rest);
    byId[id] = input;
  }

  if (html.includes('id="setting-close-action"')) {
    const btnRegex = /<button[^>]*class="([^"]*)"[^>]*data-value="([^"]+)"[^>]*>/g;
    while ((match = btnRegex.exec(html)) !== null) {
      const btn = createMockElement('button');
      btn.className = match[1];
      btn.dataset.value = match[2];
      closeActionButtons.push(btn);
    }
  }

  return { byId, closeActionButtons };
}

describe('General & System Window/Tray Settings & Modular Wiring Tests', () => {
  let mockStorage;
  let invokeCalls;
  let closeCalledCount;
  let registeredEvents;
  let mockImpl;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    closeCalledCount = 0;
    registeredEvents = {};
    mockImpl = async () => ({
      closeAction: 'tray',
      launchOnStartup: false,
      startMinimized: false,
      alwaysOnTop: false,
      autostartEnabled: false,
    });

    const mockBody = createMockElement('body');
    global.document = {
      body: mockBody,
      createElement: (tag) => createMockElement(tag),
      getElementById: (id) => (id === 'app-global-toast' ? mockBody.querySelector('#app-global-toast') : null),
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
        window: {
          getCurrentWindow: () => ({
            minimize: async () => {},
            toggleMaximize: async () => {},
            isMaximized: async () => false,
            close: async () => {
              closeCalledCount += 1;
            },
          }),
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

  it('Modular main-content router renders all 6 settings categories cleanly', async () => {
    const { SETTINGS_TABS } = await import('../src/js/settings/sidebar/types.js');
    const { renderSettingsMainContent } = await import('../src/js/settings/main-content/main-content.js');

    assert.strictEqual(SETTINGS_TABS.length, 6);
    for (const tab of SETTINGS_TABS) {
      const view = renderSettingsMainContent(tab.id);
      assert.ok(view, `Category ${tab.id} should return an element`);
      assert.strictEqual(view.className, 'settings-content-section');
      assert.ok(view.children.length >= 2, `Category ${tab.id} should render header + groups`);
    }
  });

  it('Defaults keep closeAction as "tray" and separated startup toggles as false', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    settingsState.resetToDefaults();

    assert.strictEqual(settingsState.getValue('closeAction'), 'tray');
    assert.strictEqual(settingsState.getValue('launchOnStartup'), false);
    assert.strictEqual(settingsState.getValue('startMinimized'), false);
    assert.strictEqual(settingsState.getValue('alwaysOnTop'), false);

    const { renderSystemTraySetting } = await import(
      '../src/js/settings/main-content/general-system/system-tray.js'
    );
    const { renderLaunchOnStartupSetting } = await import(
      '../src/js/settings/main-content/general-system/launch-on-startup.js'
    );
    const { renderStartMinimizedSetting } = await import(
      '../src/js/settings/main-content/general-system/start-minimized.js'
    );

    const trayRow = renderSystemTraySetting();
    const buttons = trayRow.querySelectorAll('#setting-close-action .settings-segmented-btn');
    assert.strictEqual(buttons.length, 2);
    assert.strictEqual(buttons[0].dataset.value, 'tray');
    assert.strictEqual(buttons[0].classList.contains('is-active'), true);
    assert.strictEqual(buttons[1].dataset.value, 'exit');
    assert.strictEqual(buttons[1].classList.contains('is-active'), false);

    const startupToggle = renderLaunchOnStartupSetting().querySelector('#setting-launch-on-startup');
    assert.ok(startupToggle);
    assert.strictEqual(startupToggle.checked, false);

    const minimizedToggle = renderStartMinimizedSetting().querySelector('#setting-start-minimized');
    assert.ok(minimizedToggle);
    assert.strictEqual(minimizedToggle.checked, false);
  });

  it('system-tray.ts updates settingsState, localStorage, and Rust backend when switching modes', async () => {
    const { settingsState, SETTINGS_STORAGE_KEY } = await import('../src/js/settings/sidebar/state.js');
    settingsState.resetToDefaults();
    invokeCalls = [];

    const { renderSystemTraySetting } = await import(
      '../src/js/settings/main-content/general-system/system-tray.js'
    );

    const row = renderSystemTraySetting();
    const buttons = row.querySelectorAll('#setting-close-action .settings-segmented-btn');

    await buttons[1].dispatchEvent('click');
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(settingsState.getValue('closeAction'), 'exit');
    assert.strictEqual(buttons[1].classList.contains('is-active'), true);
    assert.strictEqual(buttons[0].classList.contains('is-active'), false);
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_close_action', args: { action: 'exit' } },
    ]);

    const persisted = JSON.parse(mockStorage[SETTINGS_STORAGE_KEY]);
    assert.strictEqual(persisted.closeAction, 'exit');

    await buttons[0].dispatchEvent('click');
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(settingsState.getValue('closeAction'), 'tray');
    assert.deepStrictEqual(invokeCalls[1], {
      cmd: 'set_close_action',
      args: { action: 'tray' },
    });
  });

  it('launch-on-startup.ts enables/disables OS autostart independently and reverts checkbox if Rust rejects', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    settingsState.resetToDefaults();
    invokeCalls = [];

    const { renderLaunchOnStartupSetting } = await import(
      '../src/js/settings/main-content/general-system/launch-on-startup.js'
    );

    const row = renderLaunchOnStartupSetting();
    const toggle = row.querySelector('#setting-launch-on-startup');

    toggle.checked = true;
    await toggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('launchOnStartup'), true);
    assert.strictEqual(settingsState.getValue('startMinimized'), false);
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'set_launch_on_startup', args: { enabled: true } },
    ]);

    mockImpl = async (cmd) => {
      if (cmd === 'set_launch_on_startup') {
        throw new Error('Registry access denied');
      }
      return null;
    };
    toggle.checked = false;
    await toggle.dispatchEvent('change');
    assert.strictEqual(toggle.checked, true, 'Toggle must revert when Rust command fails');
    assert.strictEqual(settingsState.getValue('launchOnStartup'), true);
  });

  it('initGeneralSystemRuntime reconciles separated startup, alwaysOnTop, Reset Defaults, and tray open-settings', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    settingsState.resetToDefaults();
    settingsState.setValue('closeAction', 'exit');
    settingsState.setValue('launchOnStartup', true);
    settingsState.setValue('startMinimized', true);
    settingsState.setValue('alwaysOnTop', true);
    invokeCalls = [];

    mockImpl = async (cmd) => {
      if (cmd === 'get_window_tray_settings') {
        return {
          closeAction: 'tray',
          launchOnStartup: false,
          startMinimized: false,
          alwaysOnTop: false,
          autostartEnabled: false,
        };
      }
      return {
        closeAction: 'exit',
        launchOnStartup: true,
        startMinimized: true,
        alwaysOnTop: true,
      };
    };

    const { initGeneralSystemRuntime, EVENT_TRAY_OPEN_SETTINGS } = await import(
      '../src/js/settings/main-content/general-system/general-system.js'
    );

    let openSettingsInvoked = 0;
    const dispose = initGeneralSystemRuntime(() => {
      openSettingsInvoked += 1;
    });

    await new Promise((r) => setTimeout(r, 20));

    assert.ok(invokeCalls.some((c) => c.cmd === 'get_window_tray_settings'));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_close_action' && c.args.action === 'exit'));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_launch_on_startup' && c.args.enabled === true));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_start_minimized' && c.args.enabled === true));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_always_on_top' && c.args.enabled === true));

    assert.ok(registeredEvents[EVENT_TRAY_OPEN_SETTINGS]);
    registeredEvents[EVENT_TRAY_OPEN_SETTINGS]({ event: EVENT_TRAY_OPEN_SETTINGS, payload: null });
    assert.strictEqual(openSettingsInvoked, 1);
    assert.strictEqual(settingsState.getActiveTab(), 'general');

    invokeCalls = [];
    settingsState.resetToDefaults();
    await new Promise((r) => setTimeout(r, 20));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_close_action' && c.args.action === 'tray'));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_launch_on_startup' && c.args.enabled === false));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_start_minimized' && c.args.enabled === false));
    assert.ok(invokeCalls.some((c) => c.cmd === 'set_always_on_top' && c.args.enabled === false));

    dispose();
  });

  it('closeWindow() keeps WebRTC/background alive via window.close while exitApplication() calls exit_application', async () => {
    const { closeWindow, exitApplication } = await import('../src/js/shared/ipc.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');

    deviceState.connect('482194021104');
    assert.strictEqual(deviceState.isConnected(), true);

    await closeWindow();
    assert.strictEqual(closeCalledCount, 1);
    assert.strictEqual(invokeCalls.some((c) => c.cmd === 'exit_application'), false);
    assert.strictEqual(deviceState.isConnected(), true);

    await exitApplication();
    assert.strictEqual(invokeCalls.some((c) => c.cmd === 'exit_application'), true);

    deviceState.disconnect();
  });
});
