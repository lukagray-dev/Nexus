import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/managers/ui-manager';

describe('UIManager', () => {
  let uiManager;

  beforeEach(() => {
    document.body.innerHTML = `
      <div id="connect-screen" class=""></div>
      <div id="dashboard-screen" class="hidden"></div>
      <input id="child-id-input" value="test" />
      <button id="connect-btn">Connect</button>
      <div id="connect-feedback"></div>
      
      <button id="ai-btn"></button>
      <button id="profile-btn"></button>
      <div id="profile-dropdown" class="hidden"></div>
      <button id="disconnect-btn"></button>
      <div id="device-id-display"></div>
      
      <button id="subscription-toggle-btn"></button>
      <div id="subscription-dropdown" class="hidden"></div>
      
      <button id="settings-btn"></button>
      <button id="report-issue-btn"></button>
      <button id="delete-account-btn"></button>
    `;

    uiManager = new window.UIManager({
      handleConnect: vi.fn(),
      aiSidebar: { toggle: vi.fn() },
      settingsSidebar: { open: vi.fn() },
      dialogManager: {
        showDisconnectDialog: vi.fn(),
        showDeleteAccountDialog1: vi.fn()
      }
    });

    uiManager.cacheElements();
  });

  it('caches elements successfully', () => {
    expect(uiManager.elements.connectScreen).not.toBeNull();
    expect(uiManager.elements.connectBtn).not.toBeNull();
    expect(uiManager.elements.settingsBtn).not.toBeNull();
  });

  it('setupEventListeners attaches expected events', () => {
    const connectSpy = vi.spyOn(uiManager.app, 'handleConnect');
    uiManager.setupEventListeners();
    
    uiManager.elements.connectBtn.click();
    expect(connectSpy).toHaveBeenCalled();
    
    uiManager.elements.aiBtn.click();
    expect(uiManager.app.aiSidebar.toggle).toHaveBeenCalled();
  });

  it('toggles profile dropdown clicking outside', () => {
    uiManager.setupEventListeners();
    
    uiManager.elements.profileBtn.click();
    expect(uiManager.elements.profileDropdown.classList.contains('hidden')).toBe(false);
    
    document.body.click();
    expect(uiManager.elements.profileDropdown.classList.contains('hidden')).toBe(true);
  });

  it('showError sets feedback text and animates', () => {
    uiManager.elements.childIdInput.animate = vi.fn();
    uiManager.showError('Invalid ID');
    
    expect(uiManager.elements.connectFeedback.textContent).toBe('Invalid ID');
    expect(uiManager.elements.childIdInput.animate).toHaveBeenCalled();
  });

  it('showDashboard changes screens and updates info', () => {
    uiManager.showDashboard('DEVICE-123.456');
    
    expect(uiManager.elements.connectScreen.classList.contains('hidden')).toBe(true);
    expect(uiManager.elements.dashboardScreen.classList.contains('hidden')).toBe(false);
    expect(uiManager.elements.deviceIdDisplay.textContent).toBe('Device: DEVICE-123.456');
  });

  it('showConnectScreen resets form state', () => {
    uiManager.showDashboard('dev');
    
    uiManager.showConnectScreen();
    expect(uiManager.elements.connectScreen.classList.contains('hidden')).toBe(false);
    expect(uiManager.elements.dashboardScreen.classList.contains('hidden')).toBe(true);
    expect(uiManager.elements.childIdInput.value).toBe('');
    expect(uiManager.elements.childIdInput.disabled).toBe(false);
  });

});
