import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/managers/dialog-manager';

describe('DialogManager', () => {
  let appMock;
  let dialogManager;

  beforeEach(() => {
    appMock = {
      handleDisconnect: vi.fn(),
    };

    document.body.innerHTML = `
      <div id="disconnect-dialog"></div>
      <button id="disconnect-cancel-btn"></button>
      <button id="disconnect-confirm-btn"></button>

      <div id="delete-account-dialog-1"></div>
      <input id="delete-password-1" />
      <button id="delete-cancel-btn-1"></button>
      <button id="delete-next-btn-1"></button>

      <div id="delete-account-dialog-2"></div>
      <input id="delete-password-2" />
      <button id="delete-cancel-btn-2"></button>
      <button id="delete-confirm-btn-2"></button>
    `;

    dialogManager = new window.DialogManager(appMock);
    dialogManager.cacheElements();
    dialogManager.setupEventListeners();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows and closes disconnect dialog', () => {
    dialogManager.showDisconnectDialog();
    expect(dialogManager.elements.disconnectDialog.classList.contains('hidden')).toBe(false);

    dialogManager.closeDisconnectDialog();
    expect(dialogManager.elements.disconnectDialog.classList.contains('hidden')).toBe(true);
  });

  it('disconnect confirm calls app handleDisconnect', async () => {
    await dialogManager.handleDisconnectConfirm();
    expect(appMock.handleDisconnect).toHaveBeenCalled();
  });

  it('shows delete account dialog 2 only if password entered in dialog 1', () => {
    window.alert = vi.fn();
    
    // empty password
    dialogManager.showDeleteAccountDialog2();
    expect(window.alert).toHaveBeenCalledWith('Please enter your password');

    // with password
    window.alert.mockClear();
    dialogManager.elements.deletePassword1.value = 'secret';
    dialogManager.showDeleteAccountDialog2();
    
    expect(dialogManager.elements.deleteDialog1.classList.contains('hidden')).toBe(true);
    expect(dialogManager.elements.deleteDialog2.classList.contains('hidden')).toBe(false);
  });
});
