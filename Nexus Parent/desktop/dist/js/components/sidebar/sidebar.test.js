import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/components/sidebar/sidebar';

describe('MainSidebar', () => {
  let appMock;
  let sidebar;

  beforeEach(() => {
    appMock = {
      windowManager: { createWindow: vi.fn() },
      connectionManager: { sendCommand: vi.fn() }
    };

    window.nexusApp = appMock;

    document.body.innerHTML = `
      <div id="sidebar" class="sidebar"></div>
      <button id="sidebar-toggle-btn"></button>
      <button id="sidebar-logo-btn"></button>
      <div id="sidebar-resize-handle"></div>
      <button class="sidebar-btn" data-feature="screen"></button>
      <button class="sidebar-btn" data-feature="camera"></button>
      <button id="stealth-mode-btn" class="sidebar-btn"></button>
      <div class="main-area"></div>
      <div class="top-info"></div>
    `;

    sidebar = new window.MainSidebar(appMock);
    sidebar.init();
  });

  it('toggles sidebar collapse state', () => {
    const el = document.getElementById('sidebar');
    sidebar.toggle();
    expect(el.classList.contains('collapsed')).toBe(true);
    sidebar.toggle();
    expect(el.classList.contains('collapsed')).toBe(false);
  });

  it('clicking feature button creates window', () => {
    const btn = document.querySelector('.sidebar-btn[data-feature="screen"]');
    btn.click();
    expect(appMock.windowManager.createWindow).toHaveBeenCalledWith('screen');
    expect(btn.classList.contains('active')).toBe(true);
  });

  it('stealth mode logic initiates command', async () => {
    const stealthBtn = document.getElementById('stealth-mode-btn');
    
    // Bypass confirmation modal promise dynamically
    vi.spyOn(sidebar, 'showStealthConfirmation').mockResolvedValue(true);
    vi.spyOn(sidebar, 'waitForStealthConfirmation').mockResolvedValue(true);
    
    await sidebar.handleStealthMode();
    expect(appMock.connectionManager.sendCommand).toHaveBeenCalledWith('STEALTH_ON');
  });
});
