import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/managers/window-manager';

describe('WindowManager', () => {
  let windowManager;
  let featureWindowsContainer;

  beforeEach(() => {
    document.body.innerHTML = '<div id="feature-windows"></div>';
    featureWindowsContainer = document.getElementById('feature-windows');
    
    windowManager = new window.WindowManager();
    windowManager.init(featureWindowsContainer);
    
    // Mock the loader
    window.featureLoader = {
      loadFeature: vi.fn().mockImplementation(async (feature, container) => {
        container.innerHTML = `<div class="${feature}-header">Header Content</div><div class="test-content">Loaded ${feature}</div>`;
      })
    };
  });

  it('creates window correctly and prevents duplicates', async () => {
    windowManager.createWindow('camera');
    
    // Give time string for async feature load
    await new Promise(r => setTimeout(r, 60));
    
    expect(Object.keys(windowManager.openWindows).length).toBe(1);
    expect(featureWindowsContainer.children.length).toBe(1);
    
    const windowEl = windowManager.openWindows['camera'];
    expect(windowEl.getAttribute('data-feature')).toBe('camera');
    
    // Second create just focuses
    const focusSpy = vi.spyOn(windowManager, 'focusWindow');
    windowManager.createWindow('camera');
    
    expect(focusSpy).toHaveBeenCalledWith('camera');
    expect(Object.keys(windowManager.openWindows).length).toBe(1); // Still 1 window
  });

  it('sets up header correctly with a close button', async () => {
    windowManager.createWindow('camera');
    await new Promise(r => setTimeout(r, 60));
    
    const windowEl = windowManager.openWindows['camera'];
    const closeBtn = windowEl.querySelector('.feature-window-close');
    
    expect(closeBtn).not.toBeNull();
    expect(closeBtn.title).toBe('Close');
  });

  it('closes a window and removes it from DOM safely', async () => {
    windowManager.createWindow('camera');
    await new Promise(r => setTimeout(r, 60));
    
    const windowEl = windowManager.openWindows['camera'];
    const closeBtn = windowEl.querySelector('.feature-window-close');
    
    closeBtn.click();
    
    // wait for flicker-out transition
    await new Promise(r => setTimeout(r, 350));
    
    expect(Object.keys(windowManager.openWindows).length).toBe(0);
    expect(featureWindowsContainer.children.length).toBe(0);
  });

  it('focusing brings window to front by bumping z-index', () => {
    windowManager.createWindow('camera');
    windowManager.createWindow('screen');
    
    const initialZ = windowManager.zIndexCounter;
    windowManager.focusWindow('camera');
    
    const cameraWin = windowManager.openWindows['camera'];
    expect(parseInt(cameraWin.style.zIndex)).toBe(initialZ); // the counter was incremented
    expect(windowManager.zIndexCounter).toBe(initialZ + 1);
  });

  it('closeAll removes all windows instantly', () => {
    windowManager.createWindow('camera');
    windowManager.createWindow('screen');
    
    windowManager.closeAll();
    
    expect(Object.keys(windowManager.openWindows).length).toBe(0);
    expect(featureWindowsContainer.children.length).toBe(0);
  });
});
