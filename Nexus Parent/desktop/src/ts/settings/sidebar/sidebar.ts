/**
 * @file sidebar.ts
 * @description Settings Navigation Sidebar component for Nexus Parent.
 *
 * Hey friend! This module renders the tactical left navigation bar inside the Settings modal:
 * 1. Renders the 6 category tabs (General, Audio, Surveillance, Security, Devices, Diagnostics).
 * 2. Uses professional vector SVG icons matching the monochrome tactical theme (no emojis).
 * 3. Highlights the active tab with an animated left accent indicator.
 * 4. Listens to state transitions and updates active item styling smoothly.
 */

import { SETTINGS_TABS, SettingsTabId } from './types.js';
import { settingsState } from './state.js';

export class SettingsSidebar {
  private element: HTMLElement | null = null;
  private unbindTabChange: (() => void) | null = null;

  /**
   * Constructs and returns the settings sidebar navigation element.
   */
  public render(): HTMLElement {
    const nav = document.createElement('nav');
    nav.id = 'settings-sidebar-nav';
    nav.className = 'settings-nav';
    nav.setAttribute('role', 'tablist');
    nav.setAttribute('aria-label', 'Settings Categories');

    const activeTab = settingsState.getActiveTab();

    SETTINGS_TABS.forEach((tab) => {
      const btn = document.createElement('button');
      btn.className = `settings-nav-item ${tab.id === activeTab ? 'is-active' : ''}`;
      btn.dataset.tabId = tab.id;
      btn.setAttribute('role', 'tab');
      btn.setAttribute('aria-selected', tab.id === activeTab ? 'true' : 'false');
      btn.setAttribute('title', tab.description);

      btn.innerHTML = `
        <span class="settings-nav-item-left">
          <img class="settings-nav-icon" src="${tab.iconPath}" alt="" />
          <span class="settings-nav-label">${tab.label}</span>
        </span>
        ${tab.badge ? `<span class="settings-nav-badge">${tab.badge}</span>` : ''}
      `;

      btn.addEventListener('click', () => {
        settingsState.setActiveTab(tab.id);
      });

      nav.appendChild(btn);
    });

    this.element = nav;

    // Reactively update active class when active tab changes
    this.unbindTabChange = settingsState.onTabChange((newTabId: SettingsTabId) => {
      this.updateActiveItem(newTabId);
    });

    return nav;
  }

  /**
   * Updates the visual highlight on the sidebar items.
   */
  private updateActiveItem(activeTabId: SettingsTabId): void {
    if (!this.element) return;
    const items = this.element.querySelectorAll<HTMLButtonElement>('.settings-nav-item');
    items.forEach((item) => {
      const isMatch = item.dataset.tabId === activeTabId;
      item.classList.toggle('is-active', isMatch);
      item.setAttribute('aria-selected', isMatch ? 'true' : 'false');
    });
  }

  /**
   * Cleans up event listeners and references.
   */
  public destroy(): void {
    if (this.unbindTabChange) {
      this.unbindTabChange();
      this.unbindTabChange = null;
    }
    this.element = null;
  }
}
