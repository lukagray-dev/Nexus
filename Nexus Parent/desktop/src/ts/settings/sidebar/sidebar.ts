/**
 * @file sidebar.ts
 * @description Settings Navigation Sidebar component for Nexus Parent.
 *
 * Hey friend! This module renders the tactical left navigation bar inside the Settings modal:
 * 1. Renders the 6 category tabs (General, Audio, Surveillance, Security, Devices, Diagnostics).
 * 2. Uses professional vector SVG icons matching the monochrome tactical theme (no emojis).
 * 3. Highlights the active tab with an animated left accent indicator.
 * 4. Dynamically reflects live Child Device status (`Online`, `Reconnecting`, `Offline`) on the
 *    Paired Devices tab badge.
 */

import { SETTINGS_TABS, SettingsTabId, SettingsTabItem } from './types.js';
import { settingsState } from './state.js';
import { deviceState } from '../../shared/device-state.js';

/**
 * Computes the live status badge text and CSS modifier for the Paired Devices tab.
 */
function resolveDevicesTabBadge(): { text: string; variantClass: string } | null {
  if (deviceState.isConnected()) {
    return { text: 'Online', variantClass: '' };
  }
  if (deviceState.isReconnecting()) {
    return { text: 'Reconnecting', variantClass: 'is-reconnecting' };
  }
  if (deviceState.getLastConnectedDevice()) {
    return { text: 'Offline', variantClass: 'is-offline' };
  }
  return null;
}

export class SettingsSidebar {
  private element: HTMLElement | null = null;
  private devicesButton: HTMLButtonElement | null = null;
  private devicesTabMeta: SettingsTabItem | null = null;
  private unbindTabChange: (() => void) | null = null;
  private unbindDeviceChange: (() => void) | null = null;

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

      btn.innerHTML = this.buildTabInnerHtml(tab);

      if (tab.id === 'devices') {
        this.devicesButton = btn;
        this.devicesTabMeta = tab;
      }

      btn.addEventListener('click', () => {
        settingsState.setActiveTab(tab.id);
      });

      nav.appendChild(btn);
    });

    this.element = nav;

    this.unbindTabChange = settingsState.onTabChange((newTabId: SettingsTabId) => {
      this.updateActiveItem(newTabId);
    });

    this.unbindDeviceChange = deviceState.subscribe(() => {
      if (this.devicesButton && this.devicesTabMeta) {
        this.devicesButton.innerHTML = this.buildTabInnerHtml(this.devicesTabMeta);
      }
    });

    return nav;
  }

  private buildTabInnerHtml(tab: SettingsTabItem): string {
    const dynamicBadge = tab.id === 'devices' ? resolveDevicesTabBadge() : null;
    const badgeHtml = dynamicBadge
      ? `<span class="settings-nav-badge ${dynamicBadge.variantClass}">${dynamicBadge.text}</span>`
      : tab.badge
      ? `<span class="settings-nav-badge">${tab.badge}</span>`
      : '';

    return `
      <span class="settings-nav-item-left">
        <img class="settings-nav-icon" src="${tab.iconPath}" alt="" />
        <span class="settings-nav-label">${tab.label}</span>
      </span>
      ${badgeHtml}
    `;
  }

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
    if (this.unbindDeviceChange) {
      this.unbindDeviceChange();
      this.unbindDeviceChange = null;
    }
    this.devicesButton = null;
    this.devicesTabMeta = null;
    this.element = null;
  }
}
