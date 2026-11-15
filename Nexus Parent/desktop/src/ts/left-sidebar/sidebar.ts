/**
 * @file sidebar.ts
 * @description Left Collapsible Sidebar component for Nexus Parent.
 *
 * Hey friend! This manages the collapsible left sidebar containing all
 * Nexus monitoring features: live feeds, communications, app tracking, and controls.
 * It supports Ctrl+B collapse, titlebar brand logo toggle, search filtering, and width resizing!
 */

import { appState } from '../shared/state.js';
import { sidebarState } from './state.js';
import { deviceState } from '../shared/device-state.js';
import { rightSidebarState, type RightSidebarPanelId } from '../right-sidebar/state.js';
import type { SidebarSection } from './types.js';

// Features that open as inspection & control panels inside the Right Sidebar
const RIGHT_SIDEBAR_PANELS = new Set<string>([
  'sms',
  'calllog',
  'notification',
  'keyboard',
  'whatsapp',
  'telegram',
  'chatgpt',
  'claude',
  'grok',
  'chats',
  'gmail',
  'facebook',
  'instagram',
  'snapchat',
  'wellbeing',
  'applock',
  'vibrateflash',
  'wallpaper'
]);

// Monitoring categories and features ported from Nexus
const nexusSections: SidebarSection[] = [
  {
    id: 'live',
    title: 'Live Surveillance',
    collapsed: false,
    items: [
      { id: 'camera', label: 'Monitor Camera', iconClass: 'icon-sidebar-camera' },
      { id: 'mic', label: 'Monitor Mic', iconClass: 'icon-sidebar-mic' },
      { id: 'screen', label: 'Monitor Screen', iconClass: 'icon-sidebar-monitor' },
      { id: 'location', label: 'Monitor Location', iconClass: 'icon-sidebar-location' }
    ]
  },
  {
    id: 'comms',
    title: 'Communications',
    collapsed: false,
    items: [
      { id: 'sms', label: 'Monitor SMS', iconClass: 'icon-sidebar-sms' },
      { id: 'calllog', label: 'Monitor Call Log', iconClass: 'icon-sidebar-phone' },
      { id: 'notification', label: 'Monitor Notifications', iconClass: 'icon-sidebar-bell' },
      { id: 'keyboard', label: 'Monitor Keyboard', iconClass: 'icon-sidebar-keyboard' },
      { id: 'storage', label: 'Monitor Storage', iconClass: 'icon-sidebar-folder' }
    ]
  },
  {
    id: 'apps',
    title: 'Social & AI Activity',
    collapsed: false,
    items: [
      { id: 'whatsapp', label: 'Monitor WhatsApp', iconClass: 'icon-sidebar-chat' },
      { id: 'telegram', label: 'Monitor Telegram', iconClass: 'icon-sidebar-telegram' },
      { id: 'chatgpt', label: 'Monitor ChatGPT', iconClass: 'icon-sidebar-bot' },
      { id: 'claude', label: 'Monitor Claude', iconClass: 'icon-sidebar-bot' },
      { id: 'grok', label: 'Monitor Grok', iconClass: 'icon-sidebar-bot' },
      { id: 'wellbeing', label: 'Digital Wellbeing', iconClass: 'icon-sidebar-activity' }
    ]
  },
  {
    id: 'controls',
    title: 'Device Security & Controls',
    collapsed: false,
    items: [
      { id: 'applock', label: 'Monitor App Lock', iconClass: 'icon-sidebar-lock' },
      { id: 'vibrateflash', label: 'Vibrate & Flash Device', iconClass: 'icon-sidebar-zap' },
      { id: 'wallpaper', label: 'Change Wallpaper', iconClass: 'icon-sidebar-image' }
    ]
  }
];

/**
 * Initializes the left sidebar module.
 */
export function initSidebar(): void {
  setupSidebarCollapseSync();
  setupBrandToggle();
  setupKeyboardShortcuts();
  setupPairDeviceButton();
  setupResizeHandle();
  setupSearch();
  renderSidebarContent();

  // Re-render when sidebar state changes (active selection, search, or section collapse)
  sidebarState.subscribe(() => {
    renderSidebarContent();
  });

  // Re-render when right sidebar opens/switches/closes so active badges reflect accurately
  rightSidebarState.subscribe(() => {
    renderSidebarContent();
  });
}

/**
 * Synchronizes the sidebar collapsed class with the global appState.
 */
function setupSidebarCollapseSync(): void {
  const sidebar = document.getElementById('left-sidebar');
  if (!sidebar) return;

  appState.subscribe(() => {
    const isOpen = appState.getSidebarOpen();
    sidebar.classList.toggle('collapsed', !isOpen);
  });
}

/**
 * Connects the brand logo in the titlebar as a sidebar toggle button.
 */
function setupBrandToggle(): void {
  const brandContainer = document.getElementById('brand-container');
  brandContainer?.addEventListener('click', (e) => {
    e.stopPropagation();
    appState.toggleSidebar();
  });
}

/**
 * Connects keyboard shortcuts:
 * - Ctrl+B / Cmd+B: Toggle left sidebar
 * - Ctrl+P / Cmd+P: Pair child device (opens Enter Child ID screen)
 * - Ctrl+1 / Cmd+1: Return to Dashboard empty state
 */
function setupKeyboardShortcuts(): void {
  window.addEventListener('keydown', (e) => {
    // Ctrl+B: Toggle sidebar
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
      e.preventDefault();
      appState.toggleSidebar();
    }

    // Ctrl+P: Pair Child Device
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'p') {
      e.preventDefault();
      sidebarState.setActiveFeatureId(null);
      if (deviceState.isConnected()) {
        deviceState.disconnect();
      }
    }

    // Ctrl+1: Dashboard overview
    if ((e.ctrlKey || e.metaKey) && e.key === '1') {
      e.preventDefault();
      sidebarState.setActiveFeatureId(null);
    }
  });

  // Also wire View -> Toggle left-sidebar from titlebar menu
  document.getElementById('menu-item-toggle-sidebar')?.addEventListener('click', () => {
    appState.toggleSidebar();
  });
}

/**
 * Connects the "Pair Child Device" button at top of sidebar and titlebar menu.
 */
function setupPairDeviceButton(): void {
  const pairBtn = document.getElementById('btn-sidebar-pair');
  pairBtn?.addEventListener('click', () => {
    sidebarState.setActiveFeatureId(null);
    if (deviceState.isConnected()) {
      deviceState.disconnect();
    }
  });

  const pairMenuItem = document.getElementById('menu-item-pair-device');
  pairMenuItem?.addEventListener('click', () => {
    sidebarState.setActiveFeatureId(null);
    if (deviceState.isConnected()) {
      deviceState.disconnect();
    }
  });

  const dashboardMenuItem = document.getElementById('menu-item-view-dashboard');
  dashboardMenuItem?.addEventListener('click', () => {
    sidebarState.setActiveFeatureId(null);
  });
}

/**
 * Connects the drag-resize handle allowing custom sidebar width (200px - 450px).
 */
function setupResizeHandle(): void {
  const handle = document.getElementById('sidebar-resize-handle');
  const sidebar = document.getElementById('left-sidebar');
  if (!handle || !sidebar) return;

  let isResizing = false;
  let startX = 0;
  let startWidth = 260;

  handle.addEventListener('mousedown', (e) => {
    isResizing = true;
    startX = e.clientX;
    startWidth = sidebar.getBoundingClientRect().width;
    handle.classList.add('resizing');
    document.body.style.cursor = 'ew-resize';
  });

  window.addEventListener('mousemove', (e) => {
    if (!isResizing) return;
    const delta = e.clientX - startX;
    const newWidth = Math.max(200, Math.min(450, startWidth + delta));
    sidebar.style.width = `${newWidth}px`;
  });

  window.addEventListener('mouseup', () => {
    if (isResizing) {
      isResizing = false;
      handle.classList.remove('resizing');
      document.body.style.cursor = '';
    }
  });
}

/**
 * Connects the search filter input and clear button.
 */
function setupSearch(): void {
  const input = document.getElementById('sidebar-search-input') as HTMLInputElement | null;
  const clearBtn = document.getElementById('btn-search-clear');

  input?.addEventListener('input', () => {
    const val = input.value;
    sidebarState.setSearchQuery(val);
    if (clearBtn) {
      clearBtn.style.display = val.length > 0 ? 'block' : 'none';
    }
  });

  clearBtn?.addEventListener('click', () => {
    if (input) {
      input.value = '';
      sidebarState.setSearchQuery('');
      clearBtn.style.display = 'none';
      input.focus();
    }
  });
}

/**
 * Renders the sections and feature items into the scrollable content container.
 */
export function renderSidebarContent(): void {
  const container = document.getElementById('sidebar-scroll-content');
  if (!container) return;

  const searchQuery = sidebarState.getSearchQuery();
  const activeFeatureId = sidebarState.getActiveFeatureId();

  container.innerHTML = '';

  nexusSections.forEach((section) => {
    const isCollapsed = sidebarState.isSectionCollapsed(section.id);

    // Filter items based on active search query
    const filteredItems = searchQuery
      ? section.items.filter((item) => item.label.toLowerCase().includes(searchQuery))
      : section.items;

    // Skip section entirely if searching and no items match
    if (searchQuery && filteredItems.length === 0) {
      return;
    }

    const sectionElem = document.createElement('section');
    sectionElem.className = `sidebar-section ${isCollapsed ? 'collapsed' : ''}`;

    // Header
    const header = document.createElement('div');
    header.className = 'sidebar-section-header';
    header.innerHTML = `
      <div class="section-header-left">
        <span class="sidebar-icon icon-sidebar-chevron-down section-chevron"></span>
        <span>${section.title}</span>
      </div>
      <span class="count-pill">${filteredItems.length}</span>
    `;

    header.addEventListener('click', () => {
      sidebarState.toggleSectionCollapsed(section.id);
    });

    // Items Container
    const itemsContainer = document.createElement('div');
    itemsContainer.className = 'sidebar-section-items';

    filteredItems.forEach((item) => {
      const isMicItem = item.id === 'mic';
      const isRightSidebarItem = RIGHT_SIDEBAR_PANELS.has(item.id);

      let isItemActive = false;
      if (isMicItem) {
        isItemActive = sidebarState.getIsMicOpen();
      } else if (isRightSidebarItem) {
        isItemActive = rightSidebarState.getActivePanelId() === item.id;
      } else {
        isItemActive = item.id === activeFeatureId;
      }

      const itemBtn = document.createElement('div');
      itemBtn.className = `sidebar-item ${isItemActive ? 'active' : ''}`;
      itemBtn.dataset.feature = item.id;

      // Only display LIVE badge when this monitoring feature is actively live / playing
      const isFeatureLive = sidebarState.isFeatureLive(item.id);
      const badgeText = isFeatureLive ? 'LIVE' : '';

      itemBtn.innerHTML = `
        <div class="sidebar-item-left">
          <span class="sidebar-icon ${item.iconClass}"></span>
          <span class="sidebar-item-label">${item.label}</span>
        </div>
        ${badgeText ? `<span class="item-badge live">${badgeText}</span>` : ''}
      `;

      itemBtn.addEventListener('click', () => {
        if (isMicItem) {
          sidebarState.toggleMicOpen();
          return;
        }

        if (isRightSidebarItem) {
          rightSidebarState.openPanel(item.id as RightSidebarPanelId);
          return;
        }

        // If clicking the currently active feature, toggle it off to return to empty state
        if (sidebarState.getActiveFeatureId() === item.id) {
          sidebarState.setActiveFeatureId(null);
          return;
        }

        sidebarState.setActiveFeatureId(item.id);
      });

      itemsContainer.appendChild(itemBtn);
    });

    sectionElem.appendChild(header);
    sectionElem.appendChild(itemsContainer);
    container.appendChild(sectionElem);
  });
}
