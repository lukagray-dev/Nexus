import { beforeEach, describe, expect, it, vi } from 'vitest';

const ipcRendererMock = {
  send: vi.fn(),
  invoke: vi.fn(async () => ({ ok: true }))
};

async function loadIpcBridge() {
  vi.resetModules();
  globalThis.require = (moduleName) => {
    if (moduleName === 'electron') {
      return { ipcRenderer: ipcRendererMock };
    }
    throw new Error(`Unexpected require: ${moduleName}`);
  };
  await import('./ipc-bridge.js');
  return window.IPCBridge;
}

describe('IPCBridge', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    ipcRendererMock.send.mockClear();
    ipcRendererMock.invoke.mockClear();
    delete globalThis.require;
  });

  it('forwards window control commands through ipcRenderer.send', async () => {
    const IPCBridge = await loadIpcBridge();

    IPCBridge.minimizeWindow();
    IPCBridge.maximizeWindow();
    IPCBridge.closeWindow();

    expect(ipcRendererMock.send).toHaveBeenNthCalledWith(1, 'window-minimize');
    expect(ipcRendererMock.send).toHaveBeenNthCalledWith(2, 'window-maximize');
    expect(ipcRendererMock.send).toHaveBeenNthCalledWith(3, 'window-close');
  });

  it('forwards invoke-based dialog and state queries', async () => {
    const IPCBridge = await loadIpcBridge();

    await IPCBridge.isMaximized();
    await IPCBridge.showSaveDialog({ defaultPath: 'demo.txt' });
    await IPCBridge.showOpenDirectoryDialog({ title: 'Pick directory' });

    expect(ipcRendererMock.invoke).toHaveBeenNthCalledWith(1, 'window-is-maximized');
    expect(ipcRendererMock.invoke).toHaveBeenNthCalledWith(2, 'show-save-dialog', { defaultPath: 'demo.txt' });
    expect(ipcRendererMock.invoke).toHaveBeenNthCalledWith(3, 'show-open-directory-dialog', { title: 'Pick directory' });
  });

  it('binds DOMContentLoaded titlebar handlers to buttons', async () => {
    document.body.innerHTML = `
      <button id="minimize-btn"></button>
      <button id="maximize-btn"></button>
      <button id="close-btn"></button>
    `;

    await loadIpcBridge();
    document.dispatchEvent(new Event('DOMContentLoaded'));

    document.getElementById('minimize-btn').click();
    document.getElementById('maximize-btn').click();
    document.getElementById('close-btn').click();

    expect(ipcRendererMock.send).toHaveBeenCalledWith('window-minimize');
    expect(ipcRendererMock.send).toHaveBeenCalledWith('window-maximize');
    expect(ipcRendererMock.send).toHaveBeenCalledWith('window-close');
  });
});
