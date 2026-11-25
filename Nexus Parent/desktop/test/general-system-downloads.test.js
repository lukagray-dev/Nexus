/**
 * @file general-system-downloads.test.js
 * @description Comprehensive unit and integration tests for General & System download directory
 *              settings, Ask Every Time toggle, disabled state reactivity, and Monitor Storage
 *              universal download destination resolution + atomic Tauri chunked saving.
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
      if (el._parsedControls && selector.startsWith('#')) {
        const direct = el._parsedControls.byId[selector.slice(1)];
        if (direct) return [direct];
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

  // Parse checkbox inputs
  const checkboxRegex = /<input[^>]*type="checkbox"[^>]*id="([^"]+)"([^>]*)>/g;
  let match;
  while ((match = checkboxRegex.exec(html)) !== null) {
    const input = createMockElement('input');
    input.id = match[1];
    input.type = 'checkbox';
    input.checked = /\bchecked\b/.test(match[2]);
    input.disabled = /\bdisabled\b/.test(match[2]);
    byId[input.id] = input;
  }

  // Parse text inputs (attribute order across multiple lines supported)
  const inputRegex = /<input([\s\S]*?)>/g;
  while ((match = inputRegex.exec(html)) !== null) {
    const attrs = match[1];
    if (/type="checkbox"/.test(attrs)) continue;
    const idMatch = attrs.match(/id="([^"]+)"/);
    if (!idMatch) continue;

    const input = createMockElement('input');
    input.id = idMatch[1];
    input.type = 'text';
    const valMatch = attrs.match(/value="([^"]*)"/);
    input.value = valMatch ? valMatch[1] : '';
    input.disabled = /\bdisabled\b/.test(attrs);
    byId[input.id] = input;
  }

  // Parse buttons with id
  const buttonRegex = /<button([\s\S]*?)>/g;
  while ((match = buttonRegex.exec(html)) !== null) {
    const attrs = match[1];
    const idMatch = attrs.match(/id="([^"]+)"/);
    if (!idMatch) continue;

    const btn = createMockElement('button');
    btn.id = idMatch[1];
    btn.disabled = /\bdisabled\b/.test(attrs);
    byId[btn.id] = btn;
  }

  return { byId };
}

describe('General & System Download Settings & Monitor Storage Integration Tests', () => {
  let mockStorage;
  let invokeCalls;
  let mockInvokeImpl;

  beforeEach(() => {
    mockStorage = {};
    invokeCalls = [];
    mockInvokeImpl = async () => null;

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
            return await mockInvokeImpl(cmd, args);
          },
        },
      },
    };
  });

  it('ask-before-download.ts toggles setting, persists to localStorage, and resets cleanly', async () => {
    const { settingsState, SETTINGS_STORAGE_KEY } = await import(
      '../src/js/settings/sidebar/state.js'
    );
    const { renderAskBeforeDownloadSetting } = await import(
      '../src/js/settings/main-content/general-system/ask-before-download.js'
    );

    settingsState.resetToDefaults();
    assert.strictEqual(settingsState.getValue('askBeforeDownload'), false);

    const row = renderAskBeforeDownloadSetting();
    const toggle = row.querySelector('#setting-ask-before-download');
    assert.ok(toggle);
    assert.strictEqual(toggle.checked, false);

    toggle.checked = true;
    await toggle.dispatchEvent('change');
    assert.strictEqual(settingsState.getValue('askBeforeDownload'), true);

    const persisted = JSON.parse(mockStorage[SETTINGS_STORAGE_KEY]);
    assert.strictEqual(persisted.askBeforeDownload, true);

    settingsState.resetToDefaults();
    assert.strictEqual(settingsState.getValue('askBeforeDownload'), false);
  });

  it('downloads-directory.ts browses via native picker and disables cleanly when askBeforeDownload is true', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { renderDownloadsDirectorySetting } = await import(
      '../src/js/settings/main-content/general-system/downloads-directory.js'
    );
    const { renderAskBeforeDownloadSetting } = await import(
      '../src/js/settings/main-content/general-system/ask-before-download.js'
    );

    settingsState.resetToDefaults();
    mockInvokeImpl = async (cmd) => {
      if (cmd === 'get_default_downloads_directory') return 'C:\\Users\\Parent\\Downloads\\Nexus';
      if (cmd === 'pick_downloads_directory') return 'D:\\Media\\NexusExports';
      return null;
    };

    const dirRow = renderDownloadsDirectorySetting();
    const askRow = renderAskBeforeDownloadSetting();
    const dirInput = dirRow.querySelector('#setting-downloads-dir');
    const browseBtn = dirRow.querySelector('#btn-browse-downloads');
    const askToggle = askRow.querySelector('#setting-ask-before-download');

    assert.strictEqual(dirInput.value, '');
    assert.strictEqual(dirInput.disabled, false);
    assert.strictEqual(browseBtn.disabled, false);
    assert.strictEqual(dirRow.classList.contains('is-disabled'), false);

    // Click Browse -> invokes default suggestion + folder picker and saves result
    await browseBtn.dispatchEvent('click');
    assert.strictEqual(settingsState.getValue('downloadsDirectory'), 'D:\\Media\\NexusExports');
    assert.strictEqual(dirInput.value, 'D:\\Media\\NexusExports');
    assert.deepStrictEqual(invokeCalls, [
      { cmd: 'get_default_downloads_directory', args: undefined },
      { cmd: 'pick_downloads_directory', args: { initialDir: 'C:\\Users\\Parent\\Downloads\\Nexus' } },
    ]);

    // Enable Ask Every Time -> row visually disables and controls lock without erasing saved path
    askToggle.checked = true;
    await askToggle.dispatchEvent('change');
    assert.strictEqual(dirRow.classList.contains('is-disabled'), true);
    assert.strictEqual(dirInput.disabled, true);
    assert.strictEqual(browseBtn.disabled, true);
    assert.strictEqual(settingsState.getValue('downloadsDirectory'), 'D:\\Media\\NexusExports');

    // Disable Ask Every Time -> row re-enables with previous path intact
    askToggle.checked = false;
    await askToggle.dispatchEvent('change');
    assert.strictEqual(dirRow.classList.contains('is-disabled'), false);
    assert.strictEqual(dirInput.disabled, false);
    assert.strictEqual(browseBtn.disabled, false);
    assert.strictEqual(dirInput.value, 'D:\\Media\\NexusExports');
  });

  it('Monitor Storage fresh-install flow prompts once for folder, persists it, and skips prompt afterwards', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { resolveDownloadDestination } = await import(
      '../src/js/main-content/storage/download-manager.js'
    );

    settingsState.resetToDefaults();
    let folderPickerCount = 0;

    mockInvokeImpl = async (cmd) => {
      if (cmd === 'get_default_downloads_directory') return 'C:\\Users\\Parent\\Downloads\\Nexus';
      if (cmd === 'pick_downloads_directory') {
        folderPickerCount += 1;
        return 'C:\\Users\\Parent\\Downloads\\Nexus';
      }
      return null;
    };

    // First download on fresh install: asks once
    const firstTarget = await resolveDownloadDestination({
      fileName: 'IMG_001.jpg',
      mimeType: 'image/jpeg',
    });
    assert.deepStrictEqual(firstTarget, {
      mode: 'tauri-directory',
      targetDirectory: 'C:\\Users\\Parent\\Downloads\\Nexus',
    });
    assert.strictEqual(folderPickerCount, 1);
    assert.strictEqual(
      settingsState.getValue('downloadsDirectory'),
      'C:\\Users\\Parent\\Downloads\\Nexus'
    );

    // Second download: uses persisted folder immediately without opening dialog
    const secondTarget = await resolveDownloadDestination({
      fileName: 'clip.mp4',
      mimeType: 'video/mp4',
    });
    assert.deepStrictEqual(secondTarget, {
      mode: 'tauri-directory',
      targetDirectory: 'C:\\Users\\Parent\\Downloads\\Nexus',
    });
    assert.strictEqual(folderPickerCount, 1);
  });

  it('Cancelling first-time folder picker aborts download without persisting empty value', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { resolveDownloadDestination } = await import(
      '../src/js/main-content/storage/download-manager.js'
    );

    settingsState.resetToDefaults();
    mockInvokeImpl = async () => null;

    const target = await resolveDownloadDestination({ fileName: 'secret.pdf' });
    assert.strictEqual(target, null);
    assert.strictEqual(settingsState.getValue('downloadsDirectory'), '');
  });

  it('Ask Every Time mode opens Save As dialog on every download and keeps default directory intact', async () => {
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');
    const { resolveDownloadDestination } = await import(
      '../src/js/main-content/storage/download-manager.js'
    );

    settingsState.resetToDefaults();
    settingsState.setValue('downloadsDirectory', 'D:\\DefaultFolder');
    settingsState.setValue('askBeforeDownload', true);

    let savePickerCount = 0;
    mockInvokeImpl = async (cmd, args) => {
      if (cmd === 'pick_save_file_path') {
        savePickerCount += 1;
        assert.strictEqual(args.initialDir, 'D:\\DefaultFolder');
        return `E:\\Custom\\picked_${savePickerCount}_${args.suggestedName}`;
      }
      return null;
    };

    const target1 = await resolveDownloadDestination({ fileName: 'a.jpg' });
    const target2 = await resolveDownloadDestination({ fileName: 'b.mp4' });

    assert.strictEqual(savePickerCount, 2);
    assert.deepStrictEqual(target1, {
      mode: 'tauri-exact',
      exactFilePath: 'E:\\Custom\\picked_1_a.jpg',
    });
    assert.deepStrictEqual(target2, {
      mode: 'tauri-exact',
      exactFilePath: 'E:\\Custom\\picked_2_b.mp4',
    });
    assert.strictEqual(settingsState.getValue('downloadsDirectory'), 'D:\\DefaultFolder');
  });

  it('saveBase64ChunksToTauri streams chunks atomically and cancels cleanly on chunk failure', async () => {
    const { saveBase64ChunksToTauri, saveBlobWithUniversalSettings } = await import(
      '../src/js/main-content/storage/download-manager.js'
    );
    const { settingsState } = await import('../src/js/settings/sidebar/state.js');

    settingsState.resetToDefaults();
    settingsState.setValue('downloadsDirectory', 'D:\\NexusSaved');

    mockInvokeImpl = async (cmd) => {
      if (cmd === 'begin_download_file') return 'D:\\NexusSaved\\photo.jpg';
      if (cmd === 'commit_download_file') return 'D:\\NexusSaved\\photo (1).jpg';
      return null;
    };

    const committed = await saveBase64ChunksToTauri({
      transferId: 'tx-ok',
      target: { mode: 'tauri-directory', targetDirectory: 'D:\\NexusSaved' },
      fileName: 'photo.jpg',
      orderedBase64Chunks: ['QUJD', 'REVG'],
    });

    assert.strictEqual(committed, 'D:\\NexusSaved\\photo (1).jpg');
    assert.deepStrictEqual(
      invokeCalls.map((c) => c.cmd),
      ['begin_download_file', 'append_download_chunk', 'append_download_chunk', 'commit_download_file']
    );

    // Verify Image Preview Blob save also follows universal settings
    invokeCalls = [];
    const blobResult = await saveBlobWithUniversalSettings({
      blob: new Blob(['hello world'], { type: 'text/plain' }),
      fileName: 'hello.txt',
    });
    assert.deepStrictEqual(blobResult, { saved: true, path: 'D:\\NexusSaved\\photo (1).jpg' });
    assert.ok(invokeCalls.some((c) => c.cmd === 'begin_download_file'));
    assert.ok(invokeCalls.some((c) => c.cmd === 'commit_download_file'));

    // Verify failure during chunk append calls cancel_download_file
    invokeCalls = [];
    mockInvokeImpl = async (cmd) => {
      if (cmd === 'append_download_chunk') throw new Error('Disk full');
      return null;
    };

    await assert.rejects(
      () =>
        saveBase64ChunksToTauri({
          transferId: 'tx-fail',
          target: { mode: 'tauri-directory', targetDirectory: 'D:\\NexusSaved' },
          fileName: 'broken.bin',
          orderedBase64Chunks: ['QUJD'],
        }),
      /Disk full/
    );
    assert.ok(
      invokeCalls.some((c) => c.cmd === 'cancel_download_file' && c.args.transferId === 'tx-fail')
    );
  });
});
