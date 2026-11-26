/**
 * @file surveillance-loudspeaker-settings.test.js
 * @description Comprehensive unit and integration tests for Loudspeaker Target Volume
 *              and all 5 Surveillance & DVR settings in Nexus Parent Desktop.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createSurveillanceMockElement(tag) {
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

      const selectRegex = /<select[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/select>/g;
      let selectMatch;
      while ((selectMatch = selectRegex.exec(_innerHTML)) !== null) {
        const selectEl = createSurveillanceMockElement('select');
        selectEl.id = selectMatch[1];
        const options = [];
        const optRegex = /<option[^>]*value="([^"]*)"([^>]*)>([\s\S]*?)<\/option>/g;
        let optMatch;
        let selectedIdx = 0;
        while ((optMatch = optRegex.exec(selectMatch[2])) !== null) {
          const opt = {
            value: optMatch[1],
            selected: /\bselected\b/.test(optMatch[2]),
            textContent: optMatch[3].trim(),
          };
          if (opt.selected) selectedIdx = options.length;
          options.push(opt);
        }
        selectEl.options = options;
        selectEl.selectedIndex = selectedIdx;
        selectEl.value = options[selectedIdx]?.value ?? '';
        byId[selectEl.id] = selectEl;
      }

      const checkboxRegex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
      let match;
      while ((match = checkboxRegex.exec(_innerHTML)) !== null) {
        const input = createSurveillanceMockElement('input');
        input.id = match[1];
        input.checked = /\bchecked\b/.test(match[2]);
        byId[input.id] = input;
      }

      const btnRegex = /<button[^>]*id="([^"]+)"([^>]*)>/g;
      while ((match = btnRegex.exec(_innerHTML)) !== null) {
        const btn = createSurveillanceMockElement('button');
        btn.id = match[1];
        const styleMatch = match[2].match(/style="([^"]*)"/);
        if (styleMatch && styleMatch[1].includes('display:')) {
          const dMatch = styleMatch[1].match(/display:\s*([^;]+)/);
          if (dMatch) btn.style.display = dMatch[1].trim();
        }
        byId[btn.id] = btn;
      }

      const spanRegex = /<span[^>]*id="([^"]+)"([^>]*)>/g;
      while ((match = spanRegex.exec(_innerHTML)) !== null) {
        const span = createSurveillanceMockElement('span');
        span.id = match[1];
        const styleMatch = match[2].match(/style="([^"]*)"/);
        if (styleMatch && styleMatch[1].includes('display:')) {
          const dMatch = styleMatch[1].match(/display:\s*([^;]+)/);
          if (dMatch) span.style.display = dMatch[1].trim();
        }
        byId[span.id] = span;
      }

      const segRegex = /<div[^>]*class="settings-segmented-group"[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/div>/g;
      while ((match = segRegex.exec(_innerHTML)) !== null) {
        const segGroup = createSurveillanceMockElement('div');
        segGroup.id = match[1];
        const segBtnRegex = /<button[^>]*class="([^"]*)"[^>]*data-value="([^"]+)"[^>]*>/g;
        let bMatch;
        while ((bMatch = segBtnRegex.exec(match[2])) !== null) {
          const sBtn = createSurveillanceMockElement('button');
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
    removeChild: (child) => {
      el.children = el.children.filter((c) => c !== child);
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
    click: () => {},
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

describe('Loudspeaker Target Volume & Surveillance & DVR Settings Tests', () => {
  let mockStorage;
  let invokeCalls;
  let mockImpl;
  let windowListeners;
  let dispatchedWindowEvents;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    windowListeners = new Map();
    dispatchedWindowEvents = [];

    mockImpl = async (cmd, args) => {
      if (cmd === 'get_window_tray_settings') {
        return {
          closeAction: 'tray',
          launchOnStartup: false,
          startMinimized: false,
          alwaysOnTop: false,
          autostartEnabled: false,
          hardwareAcceleration: true,
          launchHardwareAcceleration: true,
        };
      }
      if (cmd === 'set_hardware_acceleration') {
        return {
          closeAction: 'tray',
          launchOnStartup: false,
          startMinimized: false,
          alwaysOnTop: false,
          autostartEnabled: false,
          hardwareAcceleration: Boolean(args?.enabled),
          launchHardwareAcceleration: true,
        };
      }
      return null;
    };

    const mockBody = createSurveillanceMockElement('body');

    global.CustomEvent = class MockCustomEvent {
      constructor(type, init = {}) {
        this.type = type;
        this.detail = init.detail;
      }
    };

    global.URL = {
      createObjectURL: () => 'blob:mock-clip-url',
      revokeObjectURL: () => {},
    };

    global.document = {
      body: mockBody,
      createElement: (tag) => createSurveillanceMockElement(tag),
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
      __TAURI__: {
        core: {
          invoke: async (cmd, args) => {
            invokeCalls.push({ cmd, args });
            return await mockImpl(cmd, args);
          },
        },
      },
    };
  });

  it('Loudspeaker Target Volume builds parameterized unmute & live volume commands', async () => {
    const {
      buildParentAudioUnmuteCommand,
      buildParentAudioVolumeCommand,
    } = await import('../src/js/main-content/mic/parent-audio-controller.js');
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { initAudioVoiceIntercomRuntime } = await import(
      '../src/js/settings/main-content/audio-voice-intercom/audio-voice-intercom.js'
    );
    const { webrtcManager } = await import('../src/js/webrtc/webrtc-manager.js');

    assert.strictEqual(buildParentAudioUnmuteCommand('max'), 'PARENT_AUDIO_UNMUTE:max');
    assert.strictEqual(buildParentAudioUnmuteCommand('high'), 'PARENT_AUDIO_UNMUTE:high');
    assert.strictEqual(buildParentAudioUnmuteCommand('normal'), 'PARENT_AUDIO_UNMUTE:normal');
    assert.strictEqual(buildParentAudioVolumeCommand('high'), 'PARENT_AUDIO_VOLUME:high');

    settingsState.resetToDefaults();
    const sentCommands = [];
    const origGetDc = webrtcManager.getDataChannel.bind(webrtcManager);
    const origSendCmd = webrtcManager.sendCommand.bind(webrtcManager);

    webrtcManager.getDataChannel = () => ({ readyState: 'open' });
    webrtcManager.sendCommand = (cmd) => {
      sentCommands.push(cmd);
      return true;
    };

    const dispose = initAudioVoiceIntercomRuntime();
    settingsState.setValue('loudspeakerVolume', 'high');
    settingsState.setValue('loudspeakerVolume', 'normal');

    assert.deepStrictEqual(sentCommands, [
      'PARENT_AUDIO_VOLUME:high',
      'PARENT_AUDIO_VOLUME:normal',
    ]);

    dispose();
    webrtcManager.getDataChannel = origGetDc;
    webrtcManager.sendCommand = origSendCmd;
  });

  it('Preferred Camera Lens & Screen Quality build expected parameterized commands and live sync', async () => {
    const {
      toCameraLensLabel,
      buildCameraOnCommand,
      EVENT_CAMERA_PREFERRED_LENS_CHANGED,
    } = await import('../src/js/main-content/camera/types.js');
    const {
      buildScreenOnCommand,
      buildScreenQualityCommand,
    } = await import('../src/js/main-content/screen/types.js');
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { initSurveillanceDvrRuntime } = await import(
      '../src/js/settings/main-content/surveillance-dvr/surveillance-dvr.js'
    );
    const { webrtcManager } = await import('../src/js/webrtc/webrtc-manager.js');

    assert.strictEqual(toCameraLensLabel('rear'), 'Rear');
    assert.strictEqual(toCameraLensLabel('front'), 'Front');
    assert.strictEqual(buildCameraOnCommand('rear'), 'CAMERA_ON:REAR');
    assert.strictEqual(buildCameraOnCommand('Front'), 'CAMERA_ON:FRONT');

    assert.strictEqual(buildScreenOnCommand('1080p'), 'SCREEN_ON:1080p');
    assert.strictEqual(buildScreenOnCommand('720p'), 'SCREEN_ON:720p');
    assert.strictEqual(buildScreenOnCommand('540p'), 'SCREEN_ON:540p');
    assert.strictEqual(buildScreenQualityCommand('540p'), 'SCREEN_QUALITY:540p');

    settingsState.resetToDefaults();
    const sentCommands = [];
    const origGetDc = webrtcManager.getDataChannel.bind(webrtcManager);
    const origSendCmd = webrtcManager.sendCommand.bind(webrtcManager);

    webrtcManager.getDataChannel = () => ({ readyState: 'open' });
    webrtcManager.sendCommand = (cmd) => {
      sentCommands.push(cmd);
      return true;
    };

    const dispose = initSurveillanceDvrRuntime();
    await new Promise((r) => setTimeout(r, 15));

    settingsState.setValue('screenQuality', '720p');
    settingsState.setValue('preferredCameraLens', 'front');

    assert.ok(sentCommands.includes('SCREEN_QUALITY:720p'));
    assert.ok(
      dispatchedWindowEvents.some(
        (e) => e.type === EVENT_CAMERA_PREFERRED_LENS_CHANGED && e.detail.lens === 'front'
      )
    );

    dispose();
    webrtcManager.getDataChannel = origGetDc;
    webrtcManager.sendCommand = origSendCmd;
  });

  it('Rolling DVR Engine enforces 15m/30m/60m retention, MP4/WebM fallback, and clip export', async () => {
    const {
      resolveDvrBufferMs,
      resolveDvrMimeType,
      buildDvrClipFileName,
      RollingDvrManager,
    } = await import('../src/js/shared/rolling-dvr.js');
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');

    assert.strictEqual(resolveDvrBufferMs('15m'), 15 * 60 * 1000);
    assert.strictEqual(resolveDvrBufferMs('30m'), 30 * 60 * 1000);
    assert.strictEqual(resolveDvrBufferMs('60m'), 60 * 60 * 1000);

    // MP4 preferred when supported
    const mp4Supported = resolveDvrMimeType('mp4', (mime) => mime === 'video/mp4;codecs=avc1');
    assert.strictEqual(mp4Supported.container, 'mp4');
    assert.strictEqual(mp4Supported.mimeType, 'video/mp4;codecs=avc1');
    assert.strictEqual(mp4Supported.usedFallback, false);

    // MP4 preferred when unsupported -> clean WebM fallback
    const mp4Fallback = resolveDvrMimeType('mp4', (mime) => mime === 'video/webm;codecs=vp8');
    assert.strictEqual(mp4Fallback.container, 'webm');
    assert.strictEqual(mp4Fallback.mimeType, 'video/webm;codecs=vp8');
    assert.strictEqual(mp4Fallback.usedFallback, true);

    const fileName = buildDvrClipFileName('camera', 'mp4', new Date(2026, 10, 26, 9, 5, 3));
    assert.strictEqual(fileName, 'nexus-camera-20261126-090503.mp4');

    // FIFO chunk pruning with dynamic maxBufferMs resize
    settingsState.resetToDefaults();
    const dvr = new RollingDvrManager({ logPrefix: 'TestDVR', feedLabel: 'camera' });
    dvr.setMaxBufferMs(10_000); // 10 seconds
    dvr.pushChunkForTesting(new Blob(['a']), 0, 4_000);
    dvr.pushChunkForTesting(new Blob(['b']), 4_000, 9_000);
    dvr.pushChunkForTesting(new Blob(['c']), 9_000, 16_000);

    // Cutoff = 16000 - 10000 = 6000 -> chunk 1 (endMs 4000) evicted, chunks 2 & 3 retained
    assert.strictEqual(dvr.getChunkCount(), 2);
    assert.strictEqual(dvr.getBufferStartSeconds(), 4);

    // Export clip uses universal Tauri download pipeline when in Tauri runtime
    settingsState.setValue('askBeforeDownload', false);
    settingsState.setValue('downloadsDirectory', 'D:\\NexusClips');
    const toastMsgs = [];
    const exportRes = await dvr.exportRecordedClip((m) => toastMsgs.push(m));
    assert.strictEqual(exportRes.saved, true);
    assert.ok(invokeCalls.some((c) => c.cmd === 'begin_download_file'));
    assert.ok(invokeCalls.some((c) => c.cmd === 'commit_download_file'));
    dvr.stop();
  });

  it('GPU Hardware Acceleration syncs with Rust, shows Restart Required when changed, and sorts WebRTC codecs', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const {
      renderHardwareAccelerationSetting,
      setLaunchHardwareAcceleration,
    } = await import('../src/js/settings/main-content/surveillance-dvr/hardware-acceleration.js');
    const { sortVideoCodecsForAcceleration } = await import('../src/js/webrtc/webrtc-manager.js');

    settingsState.resetToDefaults();
    setLaunchHardwareAcceleration(true);

    const row = renderHardwareAccelerationSetting();
    const toggle = row.querySelector('#setting-hardware-acceleration');
    const hint = row.querySelector('#setting-hw-accel-hint');
    const restartBtn = row.querySelector('#btn-restart-hw-accel');

    assert.strictEqual(toggle.checked, true);
    assert.strictEqual(hint.style.display, 'none');
    assert.strictEqual(restartBtn.style.display, 'none');

    // Toggle GPU off -> invokes Rust, persists, and exposes Restart Now button + hint
    toggle.checked = false;
    await toggle.dispatchEvent('change');

    assert.strictEqual(settingsState.getValue('hardwareAcceleration'), false);
    assert.ok(
      invokeCalls.some((c) => c.cmd === 'set_hardware_acceleration' && c.args.enabled === false)
    );
    assert.strictEqual(hint.style.display, 'block');
    assert.strictEqual(restartBtn.style.display, 'inline-flex');

    // Click Restart Now -> invokes restart_application
    await restartBtn.dispatchEvent('click');
    assert.ok(invokeCalls.some((c) => c.cmd === 'restart_application'));

    // Codec sorting prioritizes H264 when hardwareAcceleration=true and VP8 when false
    const rawCodecs = [
      { mimeType: 'video/VP8' },
      { mimeType: 'video/VP9' },
      { mimeType: 'video/H264' },
    ];
    assert.deepStrictEqual(
      sortVideoCodecsForAcceleration(rawCodecs, true).map((c) => c.mimeType),
      ['video/H264', 'video/VP9', 'video/VP8']
    );
    assert.deepStrictEqual(
      sortVideoCodecsForAcceleration(rawCodecs, false).map((c) => c.mimeType),
      ['video/VP8', 'video/VP9', 'video/H264']
    );
  });

  it('Surveillance & DVR UI rows persist changes and stay synced on Reset Defaults', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderScreenQualitySetting } = await import(
      '../src/js/settings/main-content/surveillance-dvr/screen-quality.js'
    );
    const { renderCameraLensSetting } = await import(
      '../src/js/settings/main-content/surveillance-dvr/camera-lens.js'
    );
    const { renderDvrDurationSetting } = await import(
      '../src/js/settings/main-content/surveillance-dvr/dvr-duration.js'
    );
    const { renderClipFormatSetting } = await import(
      '../src/js/settings/main-content/surveillance-dvr/clip-format.js'
    );

    settingsState.resetToDefaults();

    const qualityRow = renderScreenQualitySetting();
    const qualityBtns = qualityRow.querySelectorAll('#setting-screen-quality .settings-segmented-btn');
    await qualityBtns[1].dispatchEvent('click');
    assert.strictEqual(settingsState.getValue('screenQuality'), '720p');

    const lensRow = renderCameraLensSetting();
    const lensBtns = lensRow.querySelectorAll('#setting-camera-lens .settings-segmented-btn');
    await lensBtns[1].dispatchEvent('click');
    assert.strictEqual(settingsState.getValue('preferredCameraLens'), 'front');

    const dvrRow = renderDvrDurationSetting();
    const dvrSelect = dvrRow.querySelector('#setting-dvr-duration');
    dvrSelect.value = '60m';
    await dvrSelect.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('dvrBufferDuration'), '60m');

    const clipRow = renderClipFormatSetting();
    const clipBtns = clipRow.querySelectorAll('#setting-clip-format .settings-segmented-btn');
    await clipBtns[1].dispatchEvent('click');
    assert.strictEqual(settingsState.getValue('dvrClipFormat'), 'webm');

    // Reset Defaults restores all controls reactively
    settingsState.resetToDefaults();
    assert.strictEqual(qualityBtns[0].classList.contains('is-active'), true);
    assert.strictEqual(lensBtns[0].classList.contains('is-active'), true);
    assert.strictEqual(dvrSelect.value, '30m');
    assert.strictEqual(clipBtns[0].classList.contains('is-active'), true);
  });
});
