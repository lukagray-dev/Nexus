/**
 * @file state.ts
 * @description State management for Nexus Parent Right Sidebar.
 *
 * Hey friend! This manages the active inspection and control panel open in the right sidebar.
 * Features like SMS, Call Log, Notifications, Keyboard, Social/AI (WhatsApp, Telegram, ChatGPT, Claude, Grok),
 * and Device Controls (AppLock, Vibrate/Flash, Wallpaper) all open here!
 */

import type { RightSidebarPanelId, RightSidebarListener } from './types.js';

class RightSidebarStateManager {
  private activePanelId: RightSidebarPanelId | null = null;
  private width: number = 380;
  private listeners: Set<RightSidebarListener> = new Set();

  /**
   * Returns whether the right sidebar is currently open.
   */
  public isOpen(): boolean {
    return this.activePanelId !== null;
  }

  /**
   * Returns the currently active panel identifier, or null if closed.
   */
  public getActivePanelId(): RightSidebarPanelId | null {
    return this.activePanelId;
  }

  /**
   * Opens the specified panel in the right sidebar.
   * If the panel is already open, clicking it again toggles it closed!
   */
  public openPanel(panelId: RightSidebarPanelId): void {
    if (this.activePanelId === panelId) {
      this.close();
      return;
    }
    this.activePanelId = panelId;
    this.notify();
  }

  /**
   * Closes the right sidebar.
   */
  public close(): void {
    if (this.activePanelId !== null) {
      this.activePanelId = null;
      this.notify();
    }
  }

  /**
   * Gets the width of the right sidebar.
   */
  public getWidth(): number {
    return this.width;
  }

  /**
   * Sets the right sidebar width when dragged.
   */
  public setWidth(newWidth: number): void {
    this.width = Math.max(300, Math.min(600, newWidth));
  }

  /**
   * Subscribes a callback to state changes.
   */
  public subscribe(listener: RightSidebarListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    for (const listener of this.listeners) {
      try {
        listener();
      } catch (err) {
        console.error('[RightSidebarState] Error in listener callback:', err);
      }
    }
  }
}

export const rightSidebarState = new RightSidebarStateManager();
export type { RightSidebarPanelId };
