/**
 * @file audio-intercom-settings.test.js
 * @description Comprehensive unit and integration tests for Audio & Intercom settings:
 *              - Hardware audio device enumeration, masked label detection, permission unlock, and setSinkId routing
 *              - Input/Output device dropdown UI, Detect Devices visibility, unavailable-device fallback, and devicechange sync
 *              - Bidirectional Default Intercom Voice sync and Noise Suppression & AEC constraint wiring
 *              - ParentAudioController live capture reconfiguration and OpusAudioPlayer sink routing
 *              - Loudspeaker Target Volume remaining local-only
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createAudioMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    value: '',
    checked: false,
    disabled: false,
    textContent: '',
    options: [],
    selectedIndex: 0,
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

      // Parse <select> and its <option> tags
      const selectRegex = /<select[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/select>/g;
      let selectMatch;
      while ((selectMatch = selectRegex.exec(_innerHTML)) !== null) {
        const selectEl = createAudioMockElement('select');
        selectEl.id = selectMatch[1];
        parseOptionsIntoSelect(selectEl, selectMatch[2]);
        byId[selectEl.id] = selectEl;
      }

      // If this element itself is a <select>, parse its <option> tags directly
      if (el.tagName === 'SELECT') {
        parseOptionsIntoSelect(el, _innerHTML);
      }

      const checkboxRegex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
      let match;
      while ((match = checkboxRegex.exec(_innerHTML)) !== null) {
        const input = createAudioMockElement('input');
        input.id = match[1];
        input.checked = /\bchecked\b/.test(match[2]);
        byId[input.id] = input;
      }

      const btnRegex = /<button[^>]*id="([^"]+)"([^>]*)>/g;
      while ((match = btnRegex.exec(_innerHTML)) !== null) {
        const btn = createAudioMockElement('button');
        btn.id = match[1];
        byId[btn.id] = btn;
      }

      const spanRegex = /<span[^>]*id="([^"]+)"([^>]*)>/g;
      while ((match = spanRegex.exec(_innerHTML)) !== null) {
        const span = createAudioMockElement('span');
        span.id = match[1];
        byId[span.id] = span;
      }

      const segRegex = /<div[^>]*class="settings-segmented-group"[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/div>/g;
      while ((match = segRegex.exec(_innerHTML)) !== null) {
        const segGroup = createAudioMockElement('div');
        segGroup.id = match[1];
        const segBtnRegex = /<button[^>]*class="([^"]*)"[^>]*data-value="([^"]+)"[^>]*>/g;
        let bMatch;
        while ((bMatch = segBtnRegex.exec(match[2])) !== null) {
          const sBtn = createAudioMockElement('button');
          sBtn.className = bMatch[1];
          sBtn.dataset.value = bMatch[2];
          segGroup.children.push(sBtn);
        }
        byId[segGroup.id] = segGroup;
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
      const all = el.querySelectorAll(selector);
      return all[0] || null;
    },
    querySelectorAll: (selector) => {
      if (selector.includes(' .settings-segmented-btn')) {
        const groupId = selector.split(' ')[0].slice(1);
        const group = el._parsedById[groupId];
        return group ? group.children : [];
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

function parseOptionsIntoSelect(selectEl, innerHtml) {
  const options = [];
  const optionRegex = /<option[^>]*value="([^"]*)"([^>]*)>([\s\S]*?)<\/option>/g;
  let optMatch;
  let selectedIdx = 0;
  while ((optMatch = optionRegex.exec(innerHtml)) !== null) {
    const opt = {
      value: optMatch[1],
      selected: /\bselected\b/.test(optMatch[2]),
      textContent: optMatch[3].trim(),
    };
    if (opt.selected) {
      selectedIdx = options.length;
    }
    options.push(opt);
  }
  selectEl.options = options;
  selectEl.selectedIndex = selectedIdx;
  selectEl.value = options[selectedIdx]?.value ?? '';
}

describe('Audio & Intercom Settings Real Implementation Tests', () => {
  let mockStorage;
  let mockDevices;
  let getUserMediaCalls;
  let getUserMediaImpl;
  let deviceChangeListeners;
  let windowListeners;
  let dispatchedWindowEvents;

  beforeEach(() => {
    mockStorage = {};
    mockDevices = [];
    getUserMediaCalls = [];
    deviceChangeListeners = new Set();
    windowListeners = new Map();
    dispatchedWindowEvents = [];

    getUserMediaImpl = async () => ({
      getTracks: () => [{ kind: 'audio', enabled: true, readyState: 'live', stop: () => {} }],
      getAudioTracks: () => [{ kind: 'audio', enabled: true, readyState: 'live', stop: () => {} }],
    });

    const mockBody = createAudioMockElement('body');

    global.CustomEvent = class MockCustomEvent {
      constructor(type, init = {}) {
        this.type = type;
        this.detail = init.detail;
      }
    };

    global.document = {
      body: mockBody,
      createElement: (tag) => createAudioMockElement(tag),
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
      addEventListener: (name, fn) => {
        if (!windowListeners.has(name)) windowListeners.set(name, new Set());
        windowListeners.get(name).add(fn);
      },
      removeEventListener: (name, fn) => {
        windowListeners.get(name)?.delete(fn);
      },
      dispatchEvent: (evt) => {
        dispatchedWindowEvents.push(evt);
        const set = windowListeners.get(evt.type);
        if (set) {
          for (const fn of set) {
            fn(evt);
          }
        }
        return true;
      },
    };

    Object.defineProperty(global, 'navigator', {
      configurable: true,
      value: {
        mediaDevices: {
          enumerateDevices: async () => mockDevices,
          getUserMedia: async (constraints) => {
            getUserMediaCalls.push(constraints);
            return await getUserMediaImpl(constraints);
          },
          addEventListener: (evt, fn) => {
            if (evt === 'devicechange') deviceChangeListeners.add(fn);
          },
          removeEventListener: (evt, fn) => {
            if (evt === 'devicechange') deviceChangeListeners.delete(fn);
          },
        },
      },
    });
  });

  it('enumerateAudioDevices filters raw default/empty IDs and detects masked labels', async () => {
    const { enumerateAudioDevices } = await import('../src/js/main-content/mic/audio-device-service.js');

    mockDevices = [
      { kind: 'audioinput', deviceId: 'default', label: '', groupId: 'g0' },
      { kind: 'audioinput', deviceId: '', label: '', groupId: 'g1' },
      { kind: 'audioinput', deviceId: 'mic-usb-1', label: '', groupId: 'g2' },
      { kind: 'audiooutput', deviceId: 'spk-1', label: 'Realtek Speakers', groupId: 'g3' },
      { kind: 'videoinput', deviceId: 'cam-1', label: 'HD Webcam', groupId: 'g4' },
    ];

    const maskedSnapshot = await enumerateAudioDevices();
    assert.strictEqual(maskedSnapshot.supported, true);
    assert.strictEqual(maskedSnapshot.hasMaskedLabels, true);
    assert.strictEqual(maskedSnapshot.inputs.length, 1);
    assert.strictEqual(maskedSnapshot.inputs[0].deviceId, 'mic-usb-1');
    assert.strictEqual(maskedSnapshot.inputs[0].label, 'Microphone 1');
    assert.strictEqual(maskedSnapshot.outputs.length, 1);
    assert.strictEqual(maskedSnapshot.outputs[0].label, 'Realtek Speakers');

    mockDevices = [
      { kind: 'audioinput', deviceId: 'default', label: 'Default - Mic', groupId: 'g0' },
      { kind: 'audioinput', deviceId: 'mic-usb-1', label: 'Blue Yeti USB', groupId: 'g2' },
      { kind: 'audiooutput', deviceId: 'spk-1', label: 'Headphones (Sony)', groupId: 'g3' },
    ];

    const unmaskedSnapshot = await enumerateAudioDevices();
    assert.strictEqual(unmaskedSnapshot.hasMaskedLabels, false);
    assert.deepStrictEqual(
      unmaskedSnapshot.inputs.map((d) => ({ id: d.deviceId, label: d.label })),
      [{ id: 'mic-usb-1', label: 'Blue Yeti USB' }]
    );
  });

  it('requestAudioDevicePermission stops tracks immediately and handles denial cleanly', async () => {
    const { requestAudioDevicePermission, EVENT_AUDIO_DEVICES_REFRESHED } = await import(
      '../src/js/main-content/mic/audio-device-service.js'
    );

    let stoppedCount = 0;
    getUserMediaImpl = async () => ({
      getTracks: () => [
        {
          stop: () => {
            stoppedCount += 1;
          },
        },
      ],
    });

    const ok = await requestAudioDevicePermission();
    assert.strictEqual(ok, true);
    assert.strictEqual(stoppedCount, 1);
    assert.ok(dispatchedWindowEvents.some((e) => e.type === EVENT_AUDIO_DEVICES_REFRESHED));

    getUserMediaImpl = async () => {
      throw new Error('Permission denied');
    };
    const denied = await requestAudioDevicePermission();
    assert.strictEqual(denied, false);
  });

  it('applyAudioOutputDevice maps default to empty sinkId and falls back on NotFoundError', async () => {
    const { applyAudioOutputDevice } = await import('../src/js/main-content/mic/audio-device-service.js');

    const calls = [];
    const target = {
      setSinkId: async (sinkId) => {
        calls.push(sinkId);
      },
    };

    const resDefault = await applyAudioOutputDevice(target, 'default');
    assert.strictEqual(resDefault.supported, true);
    assert.strictEqual(resDefault.fellBackToDefault, false);
    assert.strictEqual(calls[0], '');

    const resCustom = await applyAudioOutputDevice(target, 'usb-dac-9');
    assert.strictEqual(resCustom.appliedDeviceId, 'usb-dac-9');
    assert.strictEqual(calls[1], 'usb-dac-9');

    const fallbackCalls = [];
    const flakyTarget = {
      setSinkId: async (sinkId) => {
        fallbackCalls.push(sinkId);
        if (sinkId === 'missing-bt') {
          const err = new Error('Device not found');
          err.name = 'NotFoundError';
          throw err;
        }
      },
    };
    const resFallback = await applyAudioOutputDevice(flakyTarget, 'missing-bt');
    assert.strictEqual(resFallback.fellBackToDefault, true);
    assert.strictEqual(resFallback.appliedDeviceId, '');
    assert.deepStrictEqual(fallbackCalls, ['missing-bt', '']);
  });

  it('input-device.ts populates real mics, reveals Detect Devices when masked, and detects disconnected devices', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderInputDeviceSetting } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/input-device.js'
    );

    settingsState.resetToDefaults();
    mockDevices = [
      { kind: 'audioinput', deviceId: '', label: '', groupId: 'g1' },
    ];

    const row = renderInputDeviceSetting();
    await new Promise((r) => setTimeout(r, 15));

    const select = row.querySelector('#setting-input-device');
    const detectBtn = row.querySelector('#btn-detect-input-devices');
    assert.ok(select && detectBtn);
    assert.strictEqual(detectBtn.style.display, 'inline-flex');

    // Simulate user clicking Detect Devices to grant permission and unlock real hardware names
    mockDevices = [
      { kind: 'audioinput', deviceId: 'mic-realtek', label: 'Realtek Microphone Array', groupId: 'g1' },
      { kind: 'audioinput', deviceId: 'mic-yeti', label: 'Blue Yeti Stereo Microphone', groupId: 'g2' },
    ];
    await detectBtn.dispatchEvent('click');
    await new Promise((r) => setTimeout(r, 15));

    assert.strictEqual(detectBtn.style.display, 'none');
    assert.strictEqual(select.options.length, 3);
    assert.strictEqual(select.options[1].value, 'mic-realtek');
    assert.strictEqual(select.options[2].label ?? select.options[2].textContent, 'Blue Yeti Stereo Microphone');

    // Selecting a mic persists to settingsState
    select.value = 'mic-yeti';
    select.selectedIndex = 2;
    await select.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('inputDeviceId'), 'mic-yeti');

    // Unplugging the saved mic triggers devicechange and displays fallback hint
    mockDevices = [
      { kind: 'audioinput', deviceId: 'mic-realtek', label: 'Realtek Microphone Array', groupId: 'g1' },
    ];
    for (const listener of deviceChangeListeners) {
      listener();
    }
    await new Promise((r) => setTimeout(r, 15));

    const hint = row.querySelector('#setting-input-device-hint');
    assert.strictEqual(hint.style.display, 'block');
    assert.ok(select.options.some((o) => o.value === 'mic-yeti' && o.textContent.includes('Unavailable')));
  });

  it('output-device.ts populates real speakers and persists selection', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderOutputDeviceSetting } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/output-device.js'
    );

    settingsState.resetToDefaults();
    mockDevices = [
      { kind: 'audiooutput', deviceId: 'spk-realtek', label: 'Speakers (Realtek Audio)', groupId: 'g1' },
      { kind: 'audiooutput', deviceId: 'hp-wh1000', label: 'Headphones (WH-1000XM5)', groupId: 'g2' },
    ];

    const row = renderOutputDeviceSetting();
    await new Promise((r) => setTimeout(r, 15));

    const select = row.querySelector('#setting-output-device');
    assert.strictEqual(select.options.length, 3);
    assert.strictEqual(select.options[2].textContent, 'Headphones (WH-1000XM5)');

    select.value = 'hp-wh1000';
    select.selectedIndex = 2;
    await select.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('outputDeviceId'), 'hp-wh1000');
  });

  it('buildAudioCaptureConstraints & acquireMicStreamWithFallback respect noiseSuppression, deviceId, and fallback', async () => {
    const { buildAudioCaptureConstraints, acquireMicStreamWithFallback } = await import(
      '../src/js/main-content/mic/parent-audio-controller.js'
    );

    const enabledDefault = buildAudioCaptureConstraints('default', true);
    assert.strictEqual(enabledDefault.echoCancellation, true);
    assert.strictEqual(enabledDefault.noiseSuppression, true);
    assert.strictEqual(enabledDefault.autoGainControl, true);
    assert.strictEqual(enabledDefault.deviceId, undefined);

    const disabledCustom = buildAudioCaptureConstraints('mic-usb-42', false);
    assert.strictEqual(disabledCustom.echoCancellation, false);
    assert.strictEqual(disabledCustom.noiseSuppression, false);
    assert.strictEqual(disabledCustom.autoGainControl, false);
    assert.deepStrictEqual(disabledCustom.deviceId, { exact: 'mic-usb-42' });

    // Simulate unplugged saved microphone causing OverconstrainedError -> automatic fallback to default
    const fallbackWarnings = [];
    getUserMediaImpl = async (constraints) => {
      if (constraints.audio.deviceId) {
        const err = new Error('Requested device not found');
        err.name = 'OverconstrainedError';
        throw err;
      }
      return { getTracks: () => [], getAudioTracks: () => [] };
    };

    const acquired = await acquireMicStreamWithFallback('unplugged-mic', true, (msg) =>
      fallbackWarnings.push(msg)
    );
    assert.strictEqual(acquired.usedFallback, true);
    assert.strictEqual(getUserMediaCalls.length, 2);
    assert.strictEqual(getUserMediaCalls[1].audio.deviceId, undefined);
    assert.strictEqual(fallbackWarnings.length, 1);
  });

  it('ParentAudioController applies Settings constraints and hot-swaps input when settings change', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { parentAudioController } = await import(
      '../src/js/main-content/mic/parent-audio-controller.js'
    );

    parentAudioController.cleanup();
    settingsState.resetToDefaults();
    settingsState.setValue('inputDeviceId', 'mic-studio-1');
    settingsState.setValue('noiseSuppression', false);

    let stopCount = 0;
    getUserMediaImpl = async () => {
      const track = {
        kind: 'audio',
        enabled: true,
        readyState: 'live',
        stop: () => {
          stopCount += 1;
        },
      };
      return {
        getTracks: () => [track],
        getAudioTracks: () => [track],
      };
    };

    const ok = await parentAudioController.initialize();
    assert.strictEqual(ok, true);
    assert.strictEqual(getUserMediaCalls.length, 1);
    assert.deepStrictEqual(getUserMediaCalls[0].audio.deviceId, { exact: 'mic-studio-1' });
    assert.strictEqual(getUserMediaCalls[0].audio.noiseSuppression, false);
    assert.strictEqual(getUserMediaCalls[0].audio.echoCancellation, false);

    // While idle/muted, changing noiseSuppression cleans up capture so next Speak re-acquires
    settingsState.setValue('noiseSuppression', true);
    await parentAudioController.reconfigureAudioInput();
    assert.strictEqual(stopCount >= 1, true);

    // Now unmute (actively speaking) and change inputDeviceId -> hot-swaps live without muting
    await parentAudioController.unmute();
    assert.strictEqual(parentAudioController.isActive(), true);

    settingsState.setValue('inputDeviceId', 'mic-headset-2');
    const swapped = await parentAudioController.reconfigureAudioInput();
    assert.strictEqual(swapped, true);
    assert.strictEqual(parentAudioController.isActive(), true);
    assert.deepStrictEqual(getUserMediaCalls[getUserMediaCalls.length - 1].audio.deviceId, {
      exact: 'mic-headset-2',
    });

    parentAudioController.cleanup();
  });

  it('initAudioVoiceIntercomRuntime coordinates voice preset sync and output sink change events', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const {
      initAudioVoiceIntercomRuntime,
      EVENT_INTERCOM_VOICE_SYNC,
    } = await import('../src/js/settings/main-content/audio-voice-intercom/audio-voice-intercom.js');
    const { EVENT_AUDIO_OUTPUT_CHANGED } = await import(
      '../src/js/main-content/mic/audio-device-service.js'
    );
    const { renderVoicePresetSetting } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/voice-preset.js'
    );
    const { renderNoiseSuppressionSetting } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/noise-suppression.js'
    );
    const { renderLoudspeakerVolumeSetting } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/loudspeaker-volume.js'
    );
    const { VOICE_STORAGE_KEY } = await import('../src/js/main-content/mic/voice-presets.js');

    settingsState.resetToDefaults();
    const dispose = initAudioVoiceIntercomRuntime();

    const voiceRow = renderVoicePresetSetting();
    const voiceSelect = voiceRow.querySelector('#setting-voice-preset');
    voiceSelect.value = 'robot-male';
    await voiceSelect.dispatchEvent('change');

    assert.strictEqual(settingsState.getValue('defaultVoicePreset'), 'robot-male');
    assert.strictEqual(mockStorage[VOICE_STORAGE_KEY], 'robot-male');
    assert.ok(
      dispatchedWindowEvents.some(
        (e) => e.type === EVENT_INTERCOM_VOICE_SYNC && e.detail.voiceId === 'robot-male'
      )
    );

    // Output speaker change dispatches live sink update event
    dispatchedWindowEvents = [];
    settingsState.setValue('outputDeviceId', 'spk-usb-7');
    assert.ok(
      dispatchedWindowEvents.some(
        (e) => e.type === EVENT_AUDIO_OUTPUT_CHANGED && e.detail.deviceId === 'spk-usb-7'
      )
    );

    // Noise suppression toggle & Reset Defaults sync
    const noiseRow = renderNoiseSuppressionSetting();
    const noiseToggle = noiseRow.querySelector('#setting-noise-suppression');
    noiseToggle.checked = false;
    await noiseToggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('noiseSuppression'), false);

    // Loudspeaker volume remains local-only
    const speakerRow = renderLoudspeakerVolumeSetting();
    const speakerBtns = speakerRow.querySelectorAll('#setting-speaker-volume .settings-segmented-btn');
    await speakerBtns[1].dispatchEvent('click');
    assert.strictEqual(settingsState.getValue('loudspeakerVolume'), 'high');

    // Reset Defaults restores everything cleanly
    settingsState.resetToDefaults();
    assert.strictEqual(voiceSelect.value, 'original');
    assert.strictEqual(noiseToggle.checked, true);
    assert.strictEqual(mockStorage[VOICE_STORAGE_KEY], 'original');

    dispose();
  });

  it('OpusAudioPlayer applies configured output speaker on init and reacts to live sink changes', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { OpusAudioPlayer } = await import('../src/js/main-content/screen/opus-audio-player.js');
    const { EVENT_AUDIO_OUTPUT_CHANGED } = await import(
      '../src/js/main-content/mic/audio-device-service.js'
    );

    const sinkCalls = [];
    global.window.AudioContext = class MockAudioContext {
      constructor() {
        this.currentTime = 0;
        this.state = 'running';
        this.destination = {};
      }
      createGain() {
        return {
          gain: { setValueAtTime: () => {} },
          connect: () => {},
        };
      }
      async setSinkId(id) {
        sinkCalls.push(id);
      }
      async close() {
        this.state = 'closed';
      }
    };

    settingsState.resetToDefaults();
    settingsState.setValue('outputDeviceId', 'headphones-1');

    const player = new OpusAudioPlayer();
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(sinkCalls, ['headphones-1']);

    // Live output device switch while screen audio is active
    window.dispatchEvent(
      new CustomEvent(EVENT_AUDIO_OUTPUT_CHANGED, { detail: { deviceId: 'default' } })
    );
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(sinkCalls, ['headphones-1', '']);

    player.stop();
    // After stop(), further events must not target closed context
    window.dispatchEvent(
      new CustomEvent(EVENT_AUDIO_OUTPUT_CHANGED, { detail: { deviceId: 'headphones-2' } })
    );
    await new Promise((r) => setTimeout(r, 10));
    assert.deepStrictEqual(sinkCalls, ['headphones-1', '']);
  });
});
