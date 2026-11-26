/**
 * @file desktop-alerts.test.js
 * @description End-to-end tests for General & System Desktop Alert Notifications:
 *              1. Intruder Selfie / Unauthorized PIN (`APPLOCK_NEW` with `pinCorrect: false`)
 *              2. Child Device Low Battery (`batteryPercent < 15`, non-charging, 20% hysteresis)
 *              3. Child Device Unexpected Disconnection (distinguishing dropout from manual disconnect)
 *              4. Native Tauri IPC `show_desktop_notification` + browser Notification fallback
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createAlertDomMock(tag) {
  let _className = '';
  const _classes = new Set();
  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    innerHTML: '',
    textContent: '',
    children: [],
    style: {},
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
    appendChild: (child) => {
      el.children.push(child);
      return child;
    },
    querySelector: (selector) => {
      if (selector === '#app-global-toast-text') {
        if (!el._toastText) {
          el._toastText = createAlertDomMock('span');
        }
        return el._toastText;
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
  let webNotifications;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    webNotifications = [];
    toastElement = null;

    const mockBody = createAlertDomMock('body');

    global.document = {
      body: mockBody,
      createElement: (tag) => createAlertDomMock(tag),
      getElementById: (id) => (id === 'app-global-toast' ? toastElement : null),
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    const originalAppend = mockBody.appendChild;
    mockBody.appendChild = (child) => {
      if (child.id === 'app-global-toast') {
        toastElement = child;
      }
      return originalAppend(child);
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

    // Correct PIN unlock -> no intruder alert
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

    // Wrong PIN with selfie & lockout -> fires toast + native desktop notification
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

    // Disabled toggle -> suppressed
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

    // 15% boundary -> does NOT alert (strictly < 15%)
    deviceState.updateTelemetry({ batteryPercent: 15, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    // 14% while not charging -> alerts once
    deviceState.updateTelemetry({ batteryPercent: 14, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 1);
    assert.strictEqual(invokeCalls[0].cmd, 'show_desktop_notification');
    assert.strictEqual(invokeCalls[0].args.title, 'Child Device Low Battery');
    assert.ok(invokeCalls[0].args.body.includes('14%'));

    // 12% repeat while still low -> deduplicated (no spam)
    invokeCalls = [];
    deviceState.updateTelemetry({ batteryPercent: 12, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    // Plugging in charger resets latch, so unplugging at 11% alerts again
    deviceState.updateTelemetry({ batteryPercent: 12, isCharging: true });
    deviceState.updateTelemetry({ batteryPercent: 11, isCharging: false });
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 1);
    assert.ok(invokeCalls[0].args.body.includes('11%'));

    // Disabled toggle suppresses alert
    invokeCalls = [];
    deviceState.updateTelemetry({ batteryPercent: 25, isCharging: false }); // reset latch via >= 20%
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

    // Manual disconnect must NOT trigger unexpected disconnect alert
    invokeCalls = [];
    deviceState.disconnect();
    await new Promise((r) => setTimeout(r, 10));
    assert.strictEqual(invokeCalls.length, 0);

    // Unexpected disconnect alert fires with device details
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

    // Disabled toggle suppresses alert
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
