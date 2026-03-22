/**
 * IPC Bridge - Electron IPC Communication Helper
 * Provides simple interface for renderer <-> main process communication
 */

const { ipcRenderer } = require('electron');

class IPCBridge {
  /**
   * Window Controls
   */
  static minimizeWindow() {
    ipcRenderer.send('window-minimize');
  }

  static maximizeWindow() {
    ipcRenderer.send('window-maximize');
  }

  static closeWindow() {
    ipcRenderer.send('window-close');
  }

  static async isMaximized() {
    return await ipcRenderer.invoke('window-is-maximized');
  }

  /**
   * Dialogs
   */
  static async showSaveDialog(options) {
    return await ipcRenderer.invoke('show-save-dialog', options);
  }

  static async showOpenDirectoryDialog(options) {
    return await ipcRenderer.invoke('show-open-directory-dialog', options);
  }
}

// Export to window
window.IPCBridge = IPCBridge;

// Initialize title bar controls when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  const minimizeBtn = document.getElementById('minimize-btn');
  const maximizeBtn = document.getElementById('maximize-btn');
  const closeBtn = document.getElementById('close-btn');

  if (minimizeBtn) {
    minimizeBtn.addEventListener('click', () => IPCBridge.minimizeWindow());
  }

  if (maximizeBtn) {
    maximizeBtn.addEventListener('click', () => IPCBridge.maximizeWindow());
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', () => IPCBridge.closeWindow());
  }

  console.log('✅ Title bar controls initialized');
});
