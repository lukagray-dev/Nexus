import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/components/settings/settings';

describe('SettingsSidebar', () => {
  let appMock;
  let settings;

  beforeEach(() => {
    appMock = {};
    window.IPCBridge = {
      showOpenDirectoryDialog: vi.fn().mockResolvedValue({ canceled: false, filePaths: ['C:\\Music'] })
    };

    document.body.innerHTML = `
      <div id="settings-sidebar" class="hidden"></div>
      <button id="settings-close-btn"></button>
      <button id="logout-btn"></button>
      <button id="delete-account-btn-settings"></button>
      
      <input type="text" id="video-path-input"/>
      <button id="browse-videos-btn"></button>

      <div id="disconnect-dialog" class="hidden"></div>
      <div id="delete-account-dialog-1" class="hidden"></div>
    `;

    settings = new window.SettingsSidebar(appMock);
    settings.init();
  });

  it('opens and closes settings sidebar', () => {
    const el = document.getElementById('settings-sidebar');
    
    settings.open();
    expect(el.classList.contains('hidden')).toBe(false);

    settings.close();
    // Use immediate check for simplicity, animation timeout exists in code
    expect(el.classList.contains('open')).toBe(false);
  });

  it('logout shows disconnect dialog', () => {
    const logoutBtn = document.getElementById('logout-btn');
    const dialog = document.getElementById('disconnect-dialog');
    
    logoutBtn.click();
    expect(dialog.classList.contains('hidden')).toBe(false);
  });

  it('delete account shows delete account dialog', () => {
    const deleteBtn = document.getElementById('delete-account-btn-settings');
    const dialog = document.getElementById('delete-account-dialog-1');
    
    deleteBtn.click();
    expect(dialog.classList.contains('hidden')).toBe(false);
  });

  it('browsing videos updates input format', async () => {
    const btn = document.getElementById('browse-videos-btn');
    const input = document.getElementById('video-path-input');
    
    await settings.handleBrowseFolder('videos');
    
    expect(window.IPCBridge.showOpenDirectoryDialog).toHaveBeenCalled();
    expect(input.value).toBe('C:\\Music');
  });
});
