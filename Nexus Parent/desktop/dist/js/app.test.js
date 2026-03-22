import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/app';

describe('NexusApp', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="sidebar-container"></div>
      <div id="ai-sidebar-container"></div>
      <div id="settings-sidebar-container"></div>
      <div id="feature-windows"></div>
    `;

    // Mocks for globals expected by App
    window.ComponentLoader = vi.fn().mockImplementation(() => ({
      load: vi.fn().mockResolvedValue('<div>HTML</div>')
    }));

    window.AuthUI = vi.fn().mockImplementation(() => ({
      load: vi.fn().mockResolvedValue()
    }));

    window.AuthManager = vi.fn().mockImplementation(() => ({
      init: vi.fn().mockResolvedValue()
    }));

    window.ConnectionManager = vi.fn().mockImplementation(() => ({
      connect: vi.fn().mockResolvedValue('123456789012'),
      waitForConnection: vi.fn().mockResolvedValue(),
      disconnect: vi.fn().mockResolvedValue()
    }));

    window.DialogManager = vi.fn().mockImplementation(() => ({
      cacheElements: vi.fn(),
      setupEventListeners: vi.fn()
    }));

    window.UIManager = vi.fn().mockImplementation(() => ({
      cacheElements: vi.fn(),
      setupEventListeners: vi.fn(),
      elements: { childIdInput: { value: '123456789012' } },
      setConnectingState: vi.fn(),
      showDashboard: vi.fn(),
      showError: vi.fn(),
      showConnectScreen: vi.fn()
    }));

    window.WindowManager = vi.fn().mockImplementation(() => ({
      init: vi.fn(),
      closeAll: vi.fn()
    }));

    window.MainSidebar = vi.fn().mockImplementation(() => ({ init: vi.fn() }));
    window.AISidebar = vi.fn().mockImplementation(() => ({ init: vi.fn() }));
    window.SettingsSidebar = vi.fn().mockImplementation(() => ({ init: vi.fn() }));

    // Re-initialize app since it binds on DOMContentLoaded
    window.nexusApp = new window.NexusApp();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('initializes components properly on construction', async () => {
    const app = new window.NexusApp();
    const initSpy = vi.spyOn(app, 'init');
    app.init();
    expect(initSpy).toHaveBeenCalled();
  });

  it('handleConnect successfully connects to Child Device', async () => {
    const app = new window.NexusApp();
    await app.init();
    
    // Simulate UI inputs and method calling
    await app.handleConnect();

    expect(app.isConnecting).toBe(false);
    expect(app.currentDeviceId).toBe('123456789012');
    expect(app.uiManager.showDashboard).toHaveBeenCalledWith('123456789012');
  });

  it('handleConnect failure displays UI errors correctly', async () => {
    const app = new window.NexusApp();
    await app.init();

    // Mock connection manager failure
    app.connectionManager.connect.mockRejectedValue(new Error('Connection Failed'));
    
    await app.handleConnect();
    
    expect(app.uiManager.showError).toHaveBeenCalledWith('Error: Connection Failed');
    expect(app.uiManager.setConnectingState).toHaveBeenCalledWith(false);
    expect(app.isConnecting).toBe(false);
  });

  it('handleDisconnect cleans up and navigates correctly', async () => {
    const app = new window.NexusApp();
    await app.init();
    app.currentDeviceId = '123456789012';

    await app.handleDisconnect();
    
    expect(app.connectionManager.disconnect).toHaveBeenCalled();
    expect(app.windowManager.closeAll).toHaveBeenCalled();
    expect(app.currentDeviceId).toBeNull();
    expect(app.uiManager.showConnectScreen).toHaveBeenCalled();
  });
});
