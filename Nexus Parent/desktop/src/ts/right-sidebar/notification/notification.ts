/**
 * @file notification.ts
 * @description Notifications Surveillance Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel displays intercepted push notifications and status alerts
 * from the target child device, including search filtering, category pills,
 * real-time event updates, and full notification inspection drawers.
 */

import { rightSidebarState } from '../state.js';
import {
  type NotificationRecord,
  formatRelativeTime,
  formatFullDateTime
} from '../types.js';

let activeSearchQuery: string = '';
let activeCategoryFilter: string = 'all';
let selectedNotificationKey: string | null = null;

/**
 * Creates and returns the Notifications Monitor panel element.
 */
export function renderNotificationsPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-notifications-panel';

  const notifications = rightSidebarState.getNotifications();

  container.innerHTML = `
    <!-- Search & Filter Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="notif-search-input" class="rs-search-input" placeholder="Search notifications..." spellcheck="false" value="${escapeHtml(activeSearchQuery)}" />
      </div>
    </div>

    <!-- Category Filter Pills -->
    <div class="notif-category-bar">
      ${renderCategoryPills(notifications)}
    </div>

    <!-- Notifications List -->
    <div id="notif-list" class="rs-list-container">
      ${notifications.length === 0 ? renderNotificationEmptyState() : renderNotificationItems(notifications, activeSearchQuery, activeCategoryFilter)}
    </div>

    <!-- Full Notification Inspection Modal / Drawer -->
    <div id="notif-modal-overlay" class="notif-modal-overlay" style="${selectedNotificationKey ? 'display:flex;' : 'display:none;'}">
      ${renderNotificationDetailModal(notifications.find(n => n.key === selectedNotificationKey))}
    </div>
  `;

  // Attach search filtering, pill clicks, and card inspection listeners
  setTimeout(() => {
    attachNotificationListeners(container);
  }, 0);

  return container;
}

/**
 * Renders category filter pills with count badges.
 */
function renderCategoryPills(notifications: NotificationRecord[]): string {
  const categories = [
    { id: 'all', label: 'All' },
    { id: 'msg', label: 'Messages' },
    { id: 'social', label: 'Social' },
    { id: 'system', label: 'System' },
    { id: 'media', label: 'Media' }
  ];

  return categories.map(cat => {
    const isActive = activeCategoryFilter === cat.id;
    const count = cat.id === 'all'
      ? notifications.length
      : notifications.filter(n => matchesCategory(n, cat.id)).length;

    return `
      <button class="notif-pill-btn ${isActive ? 'active' : ''}" data-cat="${cat.id}">
        <span>${cat.label}</span>
        <span class="notif-pill-badge">${count}</span>
      </button>
    `;
  }).join('');
}

/**
 * Helper to check whether a record matches the selected category filter.
 */
function matchesCategory(record: NotificationRecord, cat: string): boolean {
  if (cat === 'all') return true;
  const rawCat = (record.category || '').toLowerCase();
  const pkg = (record.packageName || '').toLowerCase();

  if (cat === 'msg') {
    return rawCat === 'msg' || rawCat.includes('message') || pkg.includes('whatsapp') || pkg.includes('telegram') || pkg.includes('sms');
  }
  if (cat === 'social') {
    return rawCat === 'social' || pkg.includes('instagram') || pkg.includes('snapchat') || pkg.includes('facebook') || pkg.includes('twitter');
  }
  if (cat === 'system') {
    return rawCat === 'sys' || rawCat === 'service' || rawCat === 'alarm' || pkg.includes('android') || pkg.includes('system');
  }
  if (cat === 'media') {
    return rawCat === 'transport' || rawCat.includes('media') || pkg.includes('youtube') || pkg.includes('spotify');
  }
  return false;
}

/**
 * Renders empty state when no notifications have been intercepted.
 */
function renderNotificationEmptyState(): string {
  const isLive = rightSidebarState.getNotificationMonitoringState() === 'LIVE';
  return `
    <div class="rs-empty-state">
      <img src="assets/icons/left-sidebar/bell.svg" alt="" style="width: 32px; height: 32px; opacity: 0.35; filter: invert(1); margin-bottom: 8px;" />
      <span class="rs-empty-title">No Notifications</span>
      <span class="rs-empty-desc">
        ${isLive
          ? 'Listening for push notifications in real time. Incoming alerts from the child device will appear here automatically.'
          : 'Click the Play button in the bottom dock to fetch active notifications and begin live monitoring.'}
      </span>
    </div>
  `;
}

/**
 * Generates markup for the list of notification cards.
 */
function renderNotificationItems(notifications: NotificationRecord[], query: string, category: string): string {
  const q = query.toLowerCase().trim();

  let filtered = notifications;

  if (category !== 'all') {
    filtered = filtered.filter(n => matchesCategory(n, category));
  }

  if (q) {
    filtered = filtered.filter(n =>
      n.appName.toLowerCase().includes(q) ||
      n.packageName.toLowerCase().includes(q) ||
      n.title.toLowerCase().includes(q) ||
      n.text.toLowerCase().includes(q) ||
      (n.bigText && n.bigText.toLowerCase().includes(q))
    );
  }

  if (filtered.length === 0) {
    return `
      <div class="rs-empty-state">
        <span class="rs-empty-title">No Matching Notifications</span>
        <span class="rs-empty-desc">No notifications matched the current search or category filter.</span>
      </div>
    `;
  }

  return filtered.map(n => {
    const formattedTime = formatRelativeTime(n.postTime);
    const contentSnippet = n.text || n.bigText || 'No text content';

    return `
      <div class="rs-list-item notif-item" data-key="${escapeHtml(n.key)}">
        <div class="rs-item-top">
          <div class="notif-badge-group">
            <span class="rs-badge-small notif-app-badge">${escapeHtml(n.appName)}</span>
            ${n.isOngoing ? '<span class="notif-ongoing-badge">ONGOING</span>' : ''}
          </div>
          <span class="rs-item-time">${escapeHtml(formattedTime)}</span>
        </div>
        <div class="rs-item-title notif-card-title">${escapeHtml(n.title)}</div>
        <div class="rs-item-snippet notif-card-desc">${escapeHtml(contentSnippet)}</div>
        ${n.subText ? `<div class="notif-card-subtext">${escapeHtml(n.subText)}</div>` : ''}
        <div class="rs-item-meta notif-card-pkg">
          <span>${escapeHtml(n.packageName)}</span>
          ${n.actions && n.actions.length > 0 ? `<span class="notif-action-count">${n.actions.length} action${n.actions.length > 1 ? 's' : ''}</span>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

/**
 * Generates markup for the full Notification inspection drawer modal.
 */
function renderNotificationDetailModal(record?: NotificationRecord): string {
  if (!record) return '';

  const fullTime = formatFullDateTime(record.postTime);
  const fullBody = record.bigText || record.text || 'No text content available';

  return `
    <div class="notif-modal-content">
      <div class="notif-modal-header">
        <div class="notif-modal-title-group">
          <span class="rs-badge-small notif-app-badge">${escapeHtml(record.appName)}</span>
          <span class="notif-modal-pkg">${escapeHtml(record.packageName)}</span>
        </div>
        <button id="notif-modal-close" class="notif-modal-close-btn" title="Close">
          <img src="assets/icons/titlebar/close.svg" alt="" style="width:12px;height:12px;filter:invert(1);" />
        </button>
      </div>

      <div class="notif-modal-body">
        <div class="notif-modal-field">
          <span class="notif-field-label">TITLE</span>
          <span class="notif-field-value notif-highlight-title">${escapeHtml(record.title)}</span>
        </div>

        <div class="notif-modal-field">
          <span class="notif-field-label">BODY CONTENT</span>
          <div class="notif-field-text-box">${escapeHtml(fullBody)}</div>
        </div>

        ${record.subText ? `
          <div class="notif-modal-field">
            <span class="notif-field-label">SUBTEXT / ACCOUNT</span>
            <span class="notif-field-value">${escapeHtml(record.subText)}</span>
          </div>
        ` : ''}

        <div class="notif-modal-grid">
          <div class="notif-modal-field">
            <span class="notif-field-label">TIMESTAMP</span>
            <span class="notif-field-value">${escapeHtml(fullTime)}</span>
          </div>
          <div class="notif-modal-field">
            <span class="notif-field-label">CATEGORY</span>
            <span class="notif-field-value">${escapeHtml(record.category)}</span>
          </div>
          <div class="notif-modal-field">
            <span class="notif-field-label">PERSISTENCE</span>
            <span class="notif-field-value">${record.isOngoing ? 'Ongoing Background Task' : 'Standard Push Alert'}</span>
          </div>
          <div class="notif-modal-field">
            <span class="notif-field-label">CHANNEL ID</span>
            <span class="notif-field-value">${escapeHtml(record.channelId || 'Default')}</span>
          </div>
        </div>

        ${record.actions && record.actions.length > 0 ? `
          <div class="notif-modal-field">
            <span class="notif-field-label">AVAILABLE ACTIONS</span>
            <div class="notif-actions-container">
              ${record.actions.map(action => `<span class="notif-action-chip">${escapeHtml(action)}</span>`).join('')}
            </div>
          </div>
        ` : ''}

        ${record.messages && record.messages.length > 0 ? `
          <div class="notif-modal-field">
            <span class="notif-field-label">CONVERSATION THREAD</span>
            <div class="notif-thread-container">
              ${record.messages.map(msg => `
                <div class="notif-thread-msg">
                  <div class="notif-thread-msg-header">
                    <span class="notif-thread-sender">${escapeHtml(msg.sender)}</span>
                    <span class="notif-thread-time">${escapeHtml(formatRelativeTime(msg.timestamp))}</span>
                  </div>
                  <div class="notif-thread-text">${escapeHtml(msg.text)}</div>
                </div>
              `).join('')}
            </div>
          </div>
        ` : ''}
      </div>
    </div>
  `;
}

/**
 * Attaches DOM event listeners for search filtering, category pills, and card inspection.
 */
function attachNotificationListeners(container: HTMLElement): void {
  // Search input filtering
  const searchInput = container.querySelector<HTMLInputElement>('#notif-search-input');
  searchInput?.addEventListener('input', () => {
    activeSearchQuery = searchInput.value;
    updateNotificationListDom(container);
  });

  // Category pill selection
  const pillBtns = container.querySelectorAll<HTMLButtonElement>('.notif-pill-btn');
  pillBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      activeCategoryFilter = btn.dataset.cat || 'all';
      pillBtns.forEach(b => b.classList.toggle('active', b === btn));
      updateNotificationListDom(container);
    });
  });

  // Card click inspection
  attachCardClickListeners(container);

  // Close modal button
  const modalOverlay = container.querySelector<HTMLElement>('#notif-modal-overlay');
  const closeBtn = container.querySelector<HTMLButtonElement>('#notif-modal-close');
  closeBtn?.addEventListener('click', () => {
    selectedNotificationKey = null;
    if (modalOverlay) modalOverlay.style.display = 'none';
  });

  modalOverlay?.addEventListener('click', (e) => {
    if (e.target === modalOverlay) {
      selectedNotificationKey = null;
      modalOverlay.style.display = 'none';
    }
  });
}

/**
 * Attaches click handlers to individual notification rows.
 */
function attachCardClickListeners(container: HTMLElement): void {
  const cards = container.querySelectorAll<HTMLElement>('.notif-item');
  const modalOverlay = container.querySelector<HTMLElement>('#notif-modal-overlay');

  cards.forEach(card => {
    card.addEventListener('click', () => {
      const key = card.dataset.key;
      if (!key) return;
      selectedNotificationKey = key;
      const notifications = rightSidebarState.getNotifications();
      const record = notifications.find(n => n.key === key);

      if (modalOverlay && record) {
        modalOverlay.innerHTML = renderNotificationDetailModal(record);
        modalOverlay.style.display = 'flex';

        // Re-attach close button listener inside modal
        const closeBtn = modalOverlay.querySelector<HTMLButtonElement>('#notif-modal-close');
        closeBtn?.addEventListener('click', () => {
          selectedNotificationKey = null;
          modalOverlay.style.display = 'none';
        });
      }
    });
  });
}

/**
 * Re-renders only the notification list portion to preserve focus on the search input.
 */
function updateNotificationListDom(container: HTMLElement): void {
  const listContainer = container.querySelector<HTMLElement>('#notif-list');
  if (!listContainer) return;

  const notifications = rightSidebarState.getNotifications();
  listContainer.innerHTML = notifications.length === 0
    ? renderNotificationEmptyState()
    : renderNotificationItems(notifications, activeSearchQuery, activeCategoryFilter);

  attachCardClickListeners(container);
}

/**
 * Escapes HTML characters to prevent XSS.
 */
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

export const renderNotificationPanel = renderNotificationsPanel;
