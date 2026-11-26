/**
 * @file paired-devices.test.js
 * @description Comprehensive tests for the Paired Devices Settings list & detail views,
 *              multi-device history persistence, Connect button orchestration,
 *              cached offline grayed-out fallback, and reactive SettingsSidebar status badge.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createDeviceMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    textContent: '',
    disabled: false,
    dataset: {},
    attributes: {},
    style: {},
    children: [],
    _parsedById: {},
    _parsedListItems: [],
    _parsedConnectBtns: [],
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
      const listItems = [];
      const connectBtns = [];

      const idRegex = /<(?:div|span|button|input|h2|p)[^>]*id="([^"]+)"[^>]*>/g;
      let match;
      while ((match = idRegex.exec(_innerHTML)) !== null) {
        const tagMatch = /^<(\w+)/.exec(match[0]);
        const child = createDeviceMockElement(tagMatch ? tagMatch[1] : 'div');
        child.id = match[1];
        const classMatch = /class="([^"]*)"/.exec(match[0]);
        if (classMatch) child.className = classMatch[1];
        child.disabled = /\bdisabled\b/.test(match[0]);
        byId[child.id] = child;
      }

      const itemRegex = /<div[^>]*class="([^"]*paired-device-list-item[^"]*)"[^>]*data-raw-id="([^"]+)"[^>]*>/g;
      while ((match = itemRegex.exec(_innerHTML)) !== null) {
        const item = createDeviceMockElement('div');
        item.className = match[1];
        item.dataset.rawId = match[2];
        listItems.push(item);
      }

      const connectRegex = /<button[^>]*class="([^"]*paired-device-connect-btn[^"]*)"[^>]*data-connect-raw-id="([^"]+)"(?:[^>]*data-device-action="([^"]+)")?([^>]*)>/g;
      while ((match = connectRegex.exec(_innerHTML)) !== null) {
        const btn = createDeviceMockElement('button');
        btn.className = match[1];
        btn.dataset.connectRawId = match[2];
        if (match[3]) btn.dataset.deviceAction = match[3];
        btn.disabled = /\bdisabled\b/.test(match[4]);
        connectBtns.push(btn);
      }

      el._parsedById = byId;
      el._parsedListItems = listItems;
      el._parsedConnectBtns = connectBtns;
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
      if (selector === '.settings-content-title') {
        const header = el.children.find((c) => c.className === 'settings-content-header');
        return header ? { textContent: '' } : null;
      }
      if (selector === '.settings-content-desc') {
        const header = el.children.find((c) => c.className === 'settings-content-header');
        return header ? { textContent: '' } : null;
      }
      const all = el.querySelectorAll(selector);
      return all[0] || null;
    },
    querySelectorAll: (selector) => {
      if (selector === '.settings-nav-item') {
        return el.children;
      }
      if (selector === '.paired-device-list-item') {
        return el._parsedListItems;
      }
      if (selector === '.paired-device-connect-btn') {
        return el._parsedConnectBtns;
      }
      if (selector.startsWith('#')) {
        const id = selector.slice(1);
        if (el._parsedById[id]) return [el._parsedById[id]];
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

describe('Paired Devices History List, Connect & Detail Telemetry Tests', () => {
  let mockStorage;

  beforeEach(async () => {
    mockStorage = {};
    const mockBody = createDeviceMockElement('body');

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
      body: mockBody,
      createElement: (tag) => createDeviceMockElement(tag),
      getElementById: (id) => (id === 'app-global-toast' ? mockBody.querySelector('#app-global-toast') : null),
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    const { deviceState } = await import('../src/js/shared/device-state.js');
    deviceState.disconnect();
    deviceState.clearPairedDevicesHistory();
  });

  it('Renders grayed-out empty state when no device has ever been paired and removes all modification controls', async () => {
    const { renderPairedDevicesContent } = await import(
      '../src/js/settings/main-content/paired-devices/paired-devices.js'
    );

    const view = renderPairedDevicesContent();
    const body = view.querySelector('#paired-devices-view-body');
    const html = body.innerHTML;

    assert.strictEqual(html.includes('setting-device-alias'), false);
    assert.strictEqual(html.includes('btn-unpair-device'), false);
    assert.strictEqual(html.includes('btn-pair-new'), false);
    assert.strictEqual(html.includes('btn-clear-device-cache'), false);
    assert.strictEqual(html.includes('<input'), false);

    assert.ok(html.includes('No Child Device Paired Yet'));
    assert.ok(html.includes('NOT PAIRED'));
    assert.ok(html.includes('settings-device-card is-offline'));
  });

  it('Persists multiple previously connected devices in history and shows all of them with Connect buttons', async () => {
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { renderPairedDevicesContent } = await import(
      '../src/js/settings/main-content/paired-devices/paired-devices.js'
    );

    // Connect device 1 and receive telemetry
    deviceState.connect('111122223333');
    deviceState.updateTelemetry({
      deviceName: 'Lucas Galaxy S24',
      model: 'SM-S928B',
      manufacturer: 'Samsung',
      androidVersion: '14',
      sdkInt: 34,
      batteryPercent: 86,
      isCharging: true,
      chargingSource: 'AC',
      networkState: 'WiFi',
    });
    deviceState.disconnect();

    // Connect device 2 and keep it online
    deviceState.connect('444455556666');
    deviceState.updateTelemetry({
      deviceName: 'Maya Pixel 9',
      model: 'Pixel 9',
      manufacturer: 'Google',
      androidVersion: '15',
      sdkInt: 35,
      batteryPercent: 52,
      isCharging: false,
      networkState: 'Mobile Data',
    });

    const history = deviceState.getPairedDevicesHistory();
    assert.strictEqual(history.length, 2);
    assert.strictEqual(history[0].rawId, '444455556666');
    assert.strictEqual(history[1].rawId, '111122223333');

    const view = renderPairedDevicesContent();
    const body = view.querySelector('#paired-devices-view-body');
    const listItems = body.querySelectorAll('.paired-device-list-item');
    const connectBtns = body.querySelectorAll('.paired-device-connect-btn');

    assert.strictEqual(listItems.length, 2);
    assert.strictEqual(connectBtns.length, 2);
    assert.strictEqual(connectBtns[0].disabled, false, 'Currently online device button should be enabled Disconnect');
    assert.strictEqual(connectBtns[0].dataset.deviceAction, 'disconnect');
    assert.ok(connectBtns[0].className.includes('settings-btn-danger'), 'Online device button must use red danger style');
    assert.ok(body.innerHTML.includes('Disconnect'));
    assert.strictEqual(connectBtns[1].disabled, false, 'Offline previous device must have an enabled Connect button');
    assert.strictEqual(connectBtns[1].dataset.deviceAction, 'connect');
    assert.ok(body.innerHTML.includes('Maya Pixel 9'));
    assert.ok(body.innerHTML.includes('Lucas Galaxy S24'));

    // Clicking offline device row opens its dedicated detail telemetry page (grayed-out with cached data)
    await listItems[1].dispatchEvent('click');
    assert.ok(body.querySelector('#btn-paired-devices-back'));
    assert.ok(body.querySelector('#btn-detail-connect-device'));
    assert.ok(body.innerHTML.includes('Lucas Galaxy S24'));
    assert.ok(body.innerHTML.includes('OFFLINE • CACHED'));
    assert.ok(body.innerHTML.includes('86% • Charging (AC)'));
    assert.ok(body.innerHTML.includes('settings-device-card is-offline'));

    // Clicking Back returns to the multi-device list
    await body.querySelector('#btn-paired-devices-back').dispatchEvent('click');
    assert.strictEqual(body.querySelectorAll('.paired-device-list-item').length, 2);

    // Clicking the online device row opens full-contrast live telemetry without a Connect button
    await body.querySelectorAll('.paired-device-list-item')[0].dispatchEvent('click');
    assert.strictEqual(body.querySelector('#btn-detail-connect-device'), null);
    assert.strictEqual(body.innerHTML.includes('settings-device-card is-offline'), false);
    assert.ok(body.innerHTML.includes('Maya Pixel 9'));
    assert.ok(body.innerHTML.includes('52% • On Battery'));

    // Disconnecting while viewing the detail page transitions it live to grayed-out cached state
    deviceState.disconnect();
    assert.ok(body.innerHTML.includes('OFFLINE • CACHED'));
    assert.ok(body.innerHTML.includes('settings-device-card is-offline'));
    assert.ok(body.querySelector('#btn-detail-connect-device'));
  });

  it('Clicking Connect on a previously connected device disconnects any other active session and calls connectLive', async () => {
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { renderPairedDevicesContent } = await import(
      '../src/js/settings/main-content/paired-devices/paired-devices.js'
    );

    deviceState.connect('111122223333');
    deviceState.updateTelemetry({ deviceName: 'Device One', model: 'Pixel 7', manufacturer: 'Google' });
    deviceState.disconnect();

    deviceState.connect('444455556666');
    deviceState.updateTelemetry({ deviceName: 'Device Two', model: 'Pixel 8', manufacturer: 'Google' });

    const originalConnectLive = deviceState.connectLive.bind(deviceState);
    const connectLiveCalls = [];
    deviceState.connectLive = async (rawId) => {
      connectLiveCalls.push(rawId);
      return deviceState.connect(rawId);
    };

    try {
      const view = renderPairedDevicesContent();
      const body = view.querySelector('#paired-devices-view-body');
      const connectBtns = body.querySelectorAll('.paired-device-connect-btn');

      // Click Connect on Device One (currently offline, index 1) while Device Two is online
      await connectBtns[1].dispatchEvent('click');
      await new Promise((r) => setTimeout(r, 15));

      assert.deepStrictEqual(connectLiveCalls, ['111122223333']);
      assert.strictEqual(deviceState.isConnected(), true);
      assert.strictEqual(deviceState.getConnectedDevice().rawId, '111122223333');

      // Now click the red Disconnect button on Device One in the list to disconnect cleanly
      const updatedBtns = body.querySelectorAll('.paired-device-connect-btn');
      assert.strictEqual(updatedBtns[0].dataset.deviceAction, 'disconnect');
      assert.ok(updatedBtns[0].className.includes('settings-btn-danger'));
      await updatedBtns[0].dispatchEvent('click');

      assert.strictEqual(deviceState.isConnected(), false);
      assert.strictEqual(deviceState.getConnectedDevice(), null);
      assert.strictEqual(body.querySelectorAll('.paired-device-connect-btn')[0].dataset.deviceAction, 'connect');
    } finally {
      deviceState.connectLive = originalConnectLive;
    }
  });

  it('Displays full-contrast expanded telemetry fields cleanly in buildPairedDeviceViewModel', async () => {
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { buildPairedDeviceViewModel } = await import(
      '../src/js/settings/main-content/paired-devices/device-card.js'
    );

    deviceState.connect('482194021104');
    deviceState.updateTelemetry({
      deviceName: 'Lucas Galaxy S24',
      model: 'SM-S928B',
      manufacturer: 'Samsung',
      brand: 'samsung',
      androidVersion: '14',
      sdkInt: 34,
      appVersion: '0.1.0-alpha',
      batteryPercent: 86,
      isCharging: true,
      chargingSource: 'AC',
      batteryTemperatureC: 32.4,
      isPowerSaveMode: false,
      batteryOptimizationIgnored: true,
      networkState: 'WiFi',
      simCarrier: 'SIM 1 • Jio',
      freeStorageBytes: 64 * 1024 * 1024 * 1024,
      totalStorageBytes: 256 * 1024 * 1024 * 1024,
      availableRamBytes: 4.5 * 1024 * 1024 * 1024,
      totalRamBytes: 8 * 1024 * 1024 * 1024,
      isScreenOn: true,
      screenResolution: '1080x2340',
      ringerMode: 'Vibrate',
      deviceUptimeMs: (2 * 24 * 60 + 4 * 60 + 15) * 60 * 1000,
      isStealthMode: false,
      isAppLockEnabled: true,
      grantedPermissionsCount: 13,
      totalPermissionsCount: 14,
    });

    const vm = buildPairedDeviceViewModel('482194021104');
    assert.strictEqual(vm.statusMode, 'online');
    assert.strictEqual(vm.osText, 'Android 14 (API 34)');
    assert.strictEqual(vm.batteryText, '86% • Charging (AC)');
    assert.strictEqual(vm.thermalPowerText, '32.4 °C • Normal Mode');
    assert.strictEqual(vm.ramText, '4.5 GB free / 8.0 GB');
    assert.strictEqual(vm.storageText, '64.0 GB free of 256.0 GB');
    assert.strictEqual(vm.networkCarrierText, 'WiFi • SIM 1 • Jio');
    assert.strictEqual(vm.ringerUptimeText, 'Vibrate • Up 2d 4h 15m');
    assert.strictEqual(vm.securityGuardsText, 'Launcher Visible • App Lock On');
    assert.strictEqual(vm.survivalPermissionsText, '13/14 Permissions • Unrestricted Battery');
    assert.strictEqual(vm.displayScreenText, 'Awake (1080x2340)');
    assert.strictEqual(vm.appVersionText, 'v0.1.0-alpha');
  });

  it('SettingsSidebar reflects live Online / Offline / empty status badge on the Paired Devices tab', async () => {
    const { deviceState } = await import('../src/js/shared/device-state.js');
    const { SettingsSidebar } = await import('../src/js/settings/sidebar/sidebar.js');

    const sidebar = new SettingsSidebar();
    const nav = sidebar.render();
    const devicesBtn = nav.children.find((c) => c.dataset.tabId === 'devices');
    assert.ok(devicesBtn);
    assert.strictEqual(devicesBtn.innerHTML.includes('settings-nav-badge'), false);

    deviceState.connect('482194021104');
    assert.ok(devicesBtn.innerHTML.includes('>Online<'));
    assert.strictEqual(devicesBtn.innerHTML.includes('is-offline'), false);

    deviceState.disconnect();
    assert.ok(devicesBtn.innerHTML.includes('>Offline<'));
    assert.ok(devicesBtn.innerHTML.includes('is-offline'));

    sidebar.destroy();
  });
});
