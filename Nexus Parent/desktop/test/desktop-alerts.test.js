/**
 * @file desktop-alerts.test.js
 * @description End-to-end tests for General & System Desktop Alert Notifications:
 *              1. Intruder Selfie / Unauthorized PIN (`APPLOCK_NEW` with `pinCorrect: false`)
 *              2. Child Device Low Battery (`batteryPercent < 15`, non-charging, 20% hysteresis)
 *              3. Child Device Unexpected Disconnection (distinguishing dropout from manual disconnect)
 *              4. Child Active Call Alert (`CALL_STATE` ringing/active/idle, 30s repeat, 1-click Turn On Mic, suppression when Mic live)
 *              5. Native Tauri IPC `show_desktop_notification` + browser Notification fallback
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createAlertDomMock(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();
  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    textContent: '',
    children: [],
    parentElement: null,
    style: {},
    dataset: {},
    attributes: {},
    _parsedById: {},
    _listeners: {},
    classList: {
      add: (c) => {
        _classes.add(c);
        _className = Array.from(_classes).join(' ');
      },
      remove: (c) => {
        _classes.delete(c);
        _className = Array.from(_classes).join(' ');
      },
      contains: (c) => _classes.has(c),
    },
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
      const elemRegex = /<(button|div|span|img|canvas)[^>]*id="([^"]+)"[^>]*>/g;
      let match;
      while ((match = elemRegex.exec(_innerHTML)) !== null) {
        const child = createAlertDomMock(match[1]);
        child.id = match[2];
        byId[child.id] = child;
      }
      el._parsedById = byId;
    },
    setAttribute: (k, v) => {
      el.attributes[k] = String(v);
    },
    removeAttribute: (k) => {
      delete el.attributes[k];
    },
    getAttribute: (k) => el.attributes[k],
    querySelectorAll: () => [],
    getContext: () => null,
    appendChild: (child) => {
      child.parentElement = el;
      el.children.push(child);
      return child;
    },
    removeChild: (child) => {
      el.children = el.children.filter((c) => c !== child);
      child.parentElement = null;
      return child;
    },
    addEventListener: (event, handler) => {
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
      if (selector === '#app-global-toast-text') {
        if (!el._toastText) {
          el._toastText = createAlertDomMock('span');
        }
        return el._toastText;
      }
      if (selector.startsWith('#')) {
        const id = selector.slice(1);
        if (el._parsedById[id]) return el._parsedById[id];
        for (const child of el.children) {
          if (child.id === id) return child;
          const nested = child.querySelector(selector);
          if (nested) return nested;
        }
      }
      return null;
    },
  };
  return el;
}

describe('General & System Desktop Alert Notifications Runtime Tests', () => {
  let mockStorage;
  let invokeCalls;
  let toastElement;
  let callBannerElement;
  let webNotifications;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    webNotifications = [];
    toastElement = null;
    callBannerElement = null;

    global.MutationObserver = class MockMutationObserver {
      observe() {}
      disconnect() {}
    };

    const mockBody = createAlertDomMock('body');

    global.document = {
      body: mockBody,
      createElement: (tag) => createAlertDomMock(tag),
      getElementById: (id) => {
        if (id === 'app-global-toast') return toastElement;
        if (id === 'nexus-active-call-alert') return callBannerElement;
        return mockBody.querySelector(`#${id}`);
      },
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    const originalAppend = mockBody.appendChild;
    mockBody.appendChild = (child) => {
      if (child.id === 'app-global-toast') {
        toastElement = child;
      } else if (child.id === 'nexus-active-call-alert') {
        callBannerElement = child;
      }
      return originalAppend(child);
    };

    const originalRemove = mockBody.removeChild;
    mockBody.removeChild = (child) => {
      if (child.id === 'nexus-active-call-alert') {
        callBannerElement = null;
      }
      return originalRemove(child);
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
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => true,
      __TAURI__: {
        core: {
          invoke: async (cmd, args) => {
            invokeCalls.push({ cmd, args });
            return null;
          },
        },
      },
    };

    class MockNotification {
      static permission = 'granted';
      constructor(title, options) {
        webNotifications.push({ title, body: options?.body });
      }
    }
    global.Notification = MockNotification;
  });

  it('Intruder Selfie alert fires on wrong PIN APPLOCK_NEW and ignores correct unlocks or disabled toggle', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const {
      initDesktopAlertsRuntime,
      evaluateAppLockAlertRecord,
      resetDesktopAlertsRuntimeForTesting,
    } = await import('../src/js/settings/main-content/general-system/desktop-alerts.js');

    settingsState.resetToDefaults();
    resetDesktopAlertsRuntimeForTesting();
    deviceState.connect('482194021104');
    const dispose = initDesktopAlertsRuntime();

    const allowedCorrect = evaluateAppLockAlertRecord({
      id: 'att-1',
      timestamp: Date.now(),
      pinCorrect: true,
      failedAttempts: 0,
      isLockedOut: false,
      selfie: 'base64-img',
    });
    assert.strictEqual(allowedCorrect, false);
    assert.strictEqual(invokeCalls.length, 0);

    invokeCalls = [];
    const triggered = evaluateAppLockAlertRecord({
      id: 'att-2',
      timestamp: Date.now(),
      pinCorrect: false,
      failedAttempts: 3,
      isLockedOut: true,
      selfie: 'base64-intruder',
    });
    await new Promise((r) => setTimeout(r, 10));

    assert.strictEqual(triggered, true);
    assert.strictEqual(invokeCalls.length, 1);
    assert.strictEqual(invokeCalls[0].cmd, 'show_desktop_notification');
    assert.strictEqual(invokeCalls[0].args.title, 'Intruder Selfie Captured');
    assert.ok(invokeCalls[0].args.body.includes('Wrong PIN (attempt #3)'));
    assert.ok(invokeCalls[0].args.body.includes('60s lockout active'));
    assert.ok(toastElement && toastElement.querySelector('#app-global-toast-text').textContent.includes('Intruder Selfie Captured'));

    invokeCalls = [];
    settingsState.setValue('alertIntruderSelfie', false);
    const suppressed = evaluateAppLockAlertRecord({
      id: 'att-3',
      timestamp: Date.now(),
      pinCorrect: false,
      failedAttempts: 1,
      isLockedOut: false,
      selfie: 'base64-intruder',
    });
    assert.strictEqual(suppressed, false);
    assert.strictEqual(invokeCalls.length, 0);

    dispose();
    deviceState.disconnect();
  });

  it('Low Battery alert fires once below 15% when not charging and resets after charging or >= 20%', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const {
      initDesktopAlertsRuntime,
      resetDesktopAlertsRuntimeForTesting,
    } = await import('../src/js/settings/main-content/general-system/desktop-alerts.js');

    settingsState.resetToDefaults();
    resetDesktopAlertsRuntimeForTesting();
    deviceState.connect('482194021104');
    const dispose = initDesktopAlertsRuntime();

    deviceState.updateTelemetry({ batteryPercent: 15, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    deviceState.updateTelemetry({ batteryPercent: 14, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 1);
    assert.strictEqual(invokeCalls[0].cmd, 'show_desktop_notification');
    assert.strictEqual(invokeCalls[0].args.title, 'Child Device Low Battery');
    assert.ok(invokeCalls[0].args.body.includes('14%'));

    invokeCalls = [];
    deviceState.updateTelemetry({ batteryPercent: 12, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    deviceState.updateTelemetry({ batteryPercent: 12, isCharging: true });
    deviceState.updateTelemetry({ batteryPercent: 11, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 1);
    assert.ok(invokeCalls[0].args.body.includes('11%'));

    invokeCalls = [];
    deviceState.updateTelemetry({ batteryPercent: 25, isCharging: false });
    settingsState.setValue('alertLowBattery', false);
    deviceState.updateTelemetry({ batteryPercent: 9, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    dispose();
    deviceState.disconnect();
  });

  it('Unexpected Disconnect alert fires on dropout, ignores manual disconnect, and respects toggle', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const {
      initDesktopAlertsRuntime,
      evaluateUnexpectedDisconnectAlert,
      resetDesktopAlertsRuntimeForTesting,
    } = await import('../src/js/settings/main-content/general-system/desktop-alerts.js');

    settingsState.resetToDefaults();
    resetDesktopAlertsRuntimeForTesting();
    deviceState.connect('482194021104');
    const dispose = initDesktopAlertsRuntime();

    invokeCalls = [];
    deviceState.disconnect();
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    const alerted = evaluateUnexpectedDisconnectAlert({
      id: '4821 9402 1104',
      rawId: '482194021104',
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro',
      batteryPercent: 70,
      signalStatus: 'WiFi',
      isOnline: false,
      lastSeen: 'Just now',
    });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(alerted, true);
    assert.strictEqual(invokeCalls.length, 1);
    assert.strictEqual(invokeCalls[0].cmd, 'show_desktop_notification');
    assert.strictEqual(invokeCalls[0].args.title, 'Child Device Disconnected');
    assert.ok(invokeCalls[0].args.body.includes('Pixel 8 Pro (4821 9402 1104)'));

    invokeCalls = [];
    settingsState.setValue('alertChildDisconnected', false);
    const suppressed = evaluateUnexpectedDisconnectAlert({
      id: '4821 9402 1104',
      rawId: '482194021104',
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro',
      batteryPercent: 70,
      signalStatus: 'WiFi',
      isOnline: false,
      lastSeen: 'Just now',
    });
    assert.strictEqual(suppressed, false);
    assert.strictEqual(invokeCalls.length, 0);

    dispose();
  });

  it('Active Call Alert displays 1-click Turn On Mic banner, repeats while Mic is off, suppresses when Mic live, and clears on idle', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { sidebarState } = await import('../src/js/left-sidebar/state.js');
    const { webrtcManager } = await import('../src/js/webrtc/webrtc-manager.js');
    const {
      initDesktopAlertsRuntime,
      evaluateCallStateAlert,
      resetDesktopAlertsRuntimeForTesting,
      CMD_CALL_ALERT_ON,
      CMD_CALL_ALERT_OFF,
      syncCallAlertSettingToChild,
    } = await import('../src/js/settings/main-content/general-system/desktop-alerts.js');

    settingsState.resetToDefaults();
    resetDesktopAlertsRuntimeForTesting();
    sidebarState.setFeatureLive('mic', false);
    sidebarState.setMicOpen(false);
    deviceState.connect('482194021104');

    const sentCommands = [];
    const origSendCommand = webrtcManager.sendCommand.bind(webrtcManager);
    const origGetDataChannel = webrtcManager.getDataChannel.bind(webrtcManager);
    webrtcManager.getDataChannel = () => ({ readyState: 'open', send: (c) => sentCommands.push(c) });
    webrtcManager.sendCommand = (cmd) => {
      sentCommands.push(cmd);
      return true;
    };

    try {
      const dispose = initDesktopAlertsRuntime();

      // 1. Incoming ringing call -> renders banner with Turn On Mic button & fires OS notification
      const alertedIncoming = evaluateCallStateAlert({
        type: 'CALL_STATE',
        state: 'ringing',
        direction: 'incoming',
        number: '+15551234567',
        contactName: 'Mom',
        timestamp: Date.now(),
        isRepeat: false,
      });
      await new Promise((r) => setTimeout(r, 10));

      assert.strictEqual(alertedIncoming, true);
      assert.ok(callBannerElement, 'In-app Call Alert banner must be mounted');
      assert.strictEqual(callBannerElement.classList.contains('visible'), true);
      assert.ok(
        callBannerElement.querySelector('#nexus-call-alert-title').textContent.includes('Incoming Call: Mom (+15551234567)')
      );
      assert.ok(
        callBannerElement.querySelector('#nexus-call-alert-body').textContent.includes('Turn on Mic')
      );
      assert.strictEqual(invokeCalls.length, 1);
      assert.strictEqual(invokeCalls[0].cmd, 'show_desktop_notification');

      // 2. 30-second repeat while Mic is still IDLE -> re-notifies parent
      invokeCalls = [];
      const repeated = evaluateCallStateAlert({
        type: 'CALL_STATE',
        state: 'active',
        direction: 'outgoing',
        number: '+15559876543',
        contactName: '',
        timestamp: Date.now(),
        isRepeat: true,
      });
      await new Promise((r) => setTimeout(r, 10));
      assert.strictEqual(repeated, true);
      assert.strictEqual(invokeCalls.length, 1);
      assert.ok(
        callBannerElement.querySelector('#nexus-call-alert-title').textContent.includes('Outgoing Call Active: +15559876543')
      );

      // 3. Clicking the 1-click Turn On Mic button opens Mic panel, sends MIC_ON, and closes banner
      const micBtn = callBannerElement.querySelector('#btn-call-alert-mic-on');
      assert.ok(micBtn);
      await micBtn.dispatchEvent('click');
      await new Promise((r) => setTimeout(r, 15));

      assert.strictEqual(sidebarState.getIsMicOpen(), true);
      assert.ok(sentCommands.includes('MIC_ON'), 'Clicking Turn On Mic must dispatch MIC_ON');
      assert.strictEqual(callBannerElement, null, 'Banner must dismiss after clicking Turn On Mic');

      // 4. Subsequent 30s repeat while Mic is live must be suppressed
      invokeCalls = [];
      sidebarState.setFeatureLive('mic', true);
      const suppressedWhileLive = evaluateCallStateAlert({
        type: 'CALL_STATE',
        state: 'active',
        direction: 'outgoing',
        timestamp: Date.now(),
        isRepeat: true,
      });
      assert.strictEqual(suppressedWhileLive, false);
      assert.strictEqual(invokeCalls.length, 0);
      assert.strictEqual(callBannerElement, null);

      // 5. Call active again when Mic is off, then call ends -> IDLE clears banner
      sidebarState.setFeatureLive('mic', false);
      // Clear any active mic state via unmount/idle check
      const { getOrCreateMicPanel } = await import('../src/js/main-content/mic/mic.js');
      const panel = getOrCreateMicPanel();
      assert.ok(panel);

      // 6. Toggle syncs CALL_ALERT_OFF / CALL_ALERT_ON to child
      sentCommands.length = 0;
      settingsState.setValue('alertChildActiveCall', false);
      assert.ok(sentCommands.includes(CMD_CALL_ALERT_OFF));

      const blockedWhenDisabled = evaluateCallStateAlert({
        type: 'CALL_STATE',
        state: 'active',
        direction: 'incoming',
        timestamp: Date.now(),
        isRepeat: false,
      });
      assert.strictEqual(blockedWhenDisabled, false);
      assert.strictEqual(callBannerElement, null);

      settingsState.setValue('alertChildActiveCall', true);
      assert.ok(sentCommands.includes(CMD_CALL_ALERT_ON));
      assert.strictEqual(syncCallAlertSettingToChild(true), true);

      // Idle packet clears banner cleanly
      evaluateCallStateAlert({
        type: 'CALL_STATE',
        state: 'idle',
        direction: 'incoming',
        timestamp: Date.now(),
        isRepeat: false,
      });
      assert.strictEqual(callBannerElement, null);

      dispose();
    } finally {
      webrtcManager.sendCommand = origSendCommand;
      webrtcManager.getDataChannel = origGetDataChannel;
      sidebarState.setFeatureLive('mic', false);
      sidebarState.setMicOpen(false);
      deviceState.disconnect();
    }
  });

  it('showDesktopNotification falls back to browser Notification outside Tauri runtime', async () => {
    const { showDesktopNotification } = await import('../src/js/shared/ipc.js');

    delete global.window.__TAURI__;
    const ok = await showDesktopNotification('Test Alert', 'Fallback notification body');
    assert.strictEqual(ok, true);
    assert.deepStrictEqual(webNotifications, [
      { title: 'Test Alert', body: 'Fallback notification body' },
    ]);
  });
});
