import { beforeEach, describe, expect, it, vi } from 'vitest';

function createMainWindowMock() {
  const handlers = new Map();
  return {
    loadFile: vi.fn(),
    setMenuBarVisibility: vi.fn(),
    once: vi.fn((event, cb) => {
      if (event === 'ready-to-show') cb();
    }),
    show: vi.fn(),
    on: vi.fn((event, cb) => handlers.set(event, cb)),
    reload: vi.fn(),
    minimize: vi.fn(),
    maximize: vi.fn(),
    unmaximize: vi.fn(),
    close: vi.fn(),
    focus: vi.fn(),
    restore: vi.fn(),
    isMinimized: vi.fn(() => false),
    isMaximized: vi.fn(() => false),
    webContents: {
      on: vi.fn(),
      send: vi.fn(),
      openDevTools: vi.fn(),
      toggleDevTools: vi.fn()
    }
  };
}

function createElectronMocks(singleInstanceLock = true) {
  const windows = [];
  const BrowserWindow = vi.fn(() => {
    const win = createMainWindowMock();
    windows.push(win);
    return win;
  });
  BrowserWindow.getAllWindows = vi.fn(() => windows);

  const app = {
    setAsDefaultProtocolClient: vi.fn(),
    requestSingleInstanceLock: vi.fn(() => singleInstanceLock),
    quit: vi.fn(),
    whenReady: vi.fn(() => Promise.resolve()),
    on: vi.fn()
  };

  return {
    app,
    BrowserWindow,
    Menu: { setApplicationMenu: vi.fn() },
    ipcMain: {
      on: vi.fn(),
      handle: vi.fn()
    },
    dialog: {
      showSaveDialog: vi.fn(async () => ({ canceled: false, filePath: 'out.txt' })),
      showOpenDialog: vi.fn(async () => ({ canceled: false, filePaths: ['C:/tmp'] }))
    },
    shell: { openExternal: vi.fn() }
  };
}

async function loadMain({ singleInstanceLock = true, defaultApp = false } = {}) {
  vi.resetModules();

  const electronMocks = createElectronMocks(singleInstanceLock);
  vi.doMock('electron', () => electronMocks);
  vi.doMock('path', () => ({
    join: (...parts) => parts.join('/'),
    resolve: (...parts) => parts.join('/')
  }));

  process.defaultApp = defaultApp;
  process.argv = ['electron', 'src/main.js'];

  await import('./main.js');
  await Promise.resolve();

  return electronMocks;
}

describe('src/main.js', () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
  });

  it('registers protocol and creates the main window on app ready', async () => {
    const electron = await loadMain({ defaultApp: false });

    expect(electron.app.setAsDefaultProtocolClient).toHaveBeenCalledWith('nexus');
    expect(electron.BrowserWindow).toHaveBeenCalledTimes(1);

    const mainWindow = electron.BrowserWindow.mock.results[0].value;
    expect(mainWindow.loadFile).toHaveBeenCalledWith(expect.stringContaining('../dist/index.html'));
    expect(mainWindow.show).toHaveBeenCalled();
  });

  it('quits immediately when single instance lock is unavailable', async () => {
    const electron = await loadMain({ singleInstanceLock: false });

    expect(electron.app.quit).toHaveBeenCalled();
  });

  it('handles auth deep links from second-instance and focuses the window', async () => {
    const electron = await loadMain();

    const secondInstance = electron.app.on.mock.calls.find(([event]) => event === 'second-instance')[1];
    secondInstance({}, ['nexus://auth/callback?token=abc123&type=signup'], 'C:/');

    const mainWindow = electron.BrowserWindow.mock.results[0].value;
    expect(mainWindow.webContents.send).toHaveBeenCalledWith('auth-callback', {
      token: 'abc123',
      type: 'signup'
    });
    expect(mainWindow.focus).toHaveBeenCalled();
  });

  it('handles password recovery deep links with hash payload', async () => {
    const electron = await loadMain();

    const secondInstance = electron.app.on.mock.calls.find(([event]) => event === 'second-instance')[1];
    secondInstance({}, ['nexus://auth/#access_token=acc&refresh_token=ref&type=recovery'], 'C:/');

    const mainWindow = electron.BrowserWindow.mock.results[0].value;
    expect(mainWindow.webContents.send).toHaveBeenCalledWith('password-reset-callback', {
      token: 'acc',
      type: 'recovery',
      accessToken: 'acc',
      refreshToken: 'ref'
    });
  });

  it('wires IPC handlers for window controls and dialogs', async () => {
    const electron = await loadMain();
    const mainWindow = electron.BrowserWindow.mock.results[0].value;

    const closeHandler = electron.ipcMain.on.mock.calls.find(([event]) => event === 'window-close')[1];
    closeHandler();
    expect(mainWindow.close).toHaveBeenCalled();

    const maximizeHandler = electron.ipcMain.on.mock.calls.find(([event]) => event === 'window-maximize')[1];
    maximizeHandler();
    expect(mainWindow.maximize).toHaveBeenCalled();

    mainWindow.isMaximized.mockReturnValue(true);
    maximizeHandler();
    expect(mainWindow.unmaximize).toHaveBeenCalled();

    const isMaximizedHandler = electron.ipcMain.handle.mock.calls.find(([event]) => event === 'window-is-maximized')[1];
    await expect(isMaximizedHandler()).resolves.toBe(true);

    const saveDialogHandler = electron.ipcMain.handle.mock.calls.find(([event]) => event === 'show-save-dialog')[1];
    await expect(saveDialogHandler({}, { defaultPath: 'file.txt' })).resolves.toEqual({
      canceled: false,
      filePath: 'out.txt'
    });
  });

  it('returns canceled dialog payload when save dialog throws', async () => {
    const electron = await loadMain();

    electron.dialog.showSaveDialog.mockRejectedValueOnce(new Error('save failed'));
    const saveDialogHandler = electron.ipcMain.handle.mock.calls.find(([event]) => event === 'show-save-dialog')[1];

    await expect(saveDialogHandler({}, {})).resolves.toEqual({
      canceled: true,
      error: 'save failed'
    });
  });
});
