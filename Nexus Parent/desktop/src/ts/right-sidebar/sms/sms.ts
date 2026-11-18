/**
 * @file sms.ts
 * @description SMS Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel displays intercepted and synchronized SMS text messages
 * from the target child device, including search filtering, conversation previews,
 * and full message thread inspection drawers.
 */

import { rightSidebarState } from '../state.js';
import {
  type SmsRecord,
  formatRelativeTime,
  formatFullDateTime,
  formatSimSlot
} from '../types.js';

let activeSearchQuery: string = '';
let selectedSmsId: string | null = null;

/**
 * Creates and returns the SMS Monitor panel element.
 */
export function renderSmsPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-sms-panel';

  const smsMessages = rightSidebarState.getSmsMessages();

  container.innerHTML = `
    <!-- Search Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="sms-search-input" class="rs-search-input" placeholder="Search SMS messages..." spellcheck="false" value="${activeSearchQuery}" />
      </div>
    </div>

    <!-- Messages List -->
    <div id="sms-list" class="rs-list-container">
      ${smsMessages.length === 0 ? renderSmsEmptyState() : renderSmsItems(smsMessages, activeSearchQuery)}
    </div>

    <!-- Full SMS Inspection Modal / Drawer -->
    <div id="sms-modal-overlay" class="sms-modal-overlay" style="${selectedSmsId ? 'display:flex;' : 'display:none;'}">
      ${renderSmsDetailModal(smsMessages.find(m => m.id === selectedSmsId))}
    </div>
  `;

  // Attach search filtering and thread click listeners
  setTimeout(() => {
    attachSmsListeners(container);
  }, 0);

  return container;
}

/**
 * Renders empty state when no SMS messages have been synchronized.
 */
function renderSmsEmptyState(): string {
  const isLive = rightSidebarState.getSmsMonitoringState() === 'LIVE';
  return `
    <div class="rs-empty-state">
      <img src="assets/icons/left-sidebar/message-square.svg" alt="" style="width: 32px; height: 32px; opacity: 0.35; filter: invert(1); margin-bottom: 8px;" />
      <span class="rs-empty-title">No SMS Messages</span>
      <span class="rs-empty-desc">
        ${isLive
          ? 'Listening for SMS in real time. Incoming and outgoing text messages will appear here automatically.'
          : 'Click the Play button in the bottom dock to fetch latest 100 SMS messages and begin live monitoring.'}
      </span>
    </div>
  `;
}

/**
 * Generates markup for the list of SMS message rows.
 */
function renderSmsItems(messages: SmsRecord[], query: string): string {
  const q = query.toLowerCase().trim();
  const filtered = q
    ? messages.filter(m =>
        m.senderName.toLowerCase().includes(q) ||
        m.address.toLowerCase().includes(q) ||
        m.body.toLowerCase().includes(q)
      )
    : messages;

  if (filtered.length === 0 && messages.length > 0) {
    return `
      <div class="rs-empty-state">
        <span class="rs-empty-title">No Search Matches</span>
        <span class="rs-empty-desc">No SMS messages matched "${query}".</span>
      </div>
    `;
  }

  return filtered.map(m => {
    const isInbox = m.smsType === 'inbox';
    const displaySender = m.senderName.trim().length > 0 ? m.senderName : m.address;
    const isSelected = selectedSmsId === m.id;

    return `
      <div class="rs-list-item sms-item ${isSelected ? 'selected' : ''}" data-id="${m.id}" data-sender="${displaySender.toLowerCase()}" data-text="${m.body.toLowerCase()}">
        <div class="rs-item-top">
          <div style="display:flex; align-items:center; gap:6px; min-width:0;">
            <span class="rs-item-title" title="${displaySender}">${displaySender}</span>
            ${m.senderName ? `<span class="rs-item-subaddress">(${m.address})</span>` : ''}
          </div>
          <span class="rs-item-time" title="${formatFullDateTime(m.timestamp)}">${formatRelativeTime(m.timestamp)}</span>
        </div>
        <div class="rs-item-snippet">${escapeHtml(m.body)}</div>
        <div class="rs-item-meta">
          <div style="display:flex; align-items:center; gap:6px;">
            <span class="rs-badge-small ${isInbox ? 'sms-badge-inbox' : 'sms-badge-sent'}">${isInbox ? 'INCOMING' : 'OUTGOING'}</span>
            ${!m.read && isInbox ? '<span class="rs-badge-small sms-unread-badge">UNREAD</span>' : ''}
            ${m.simId ? `<span class="call-chip sim-chip">${formatSimSlot(m.simId)}</span>` : ''}
          </div>
          <span class="sms-view-hint">View full text</span>
        </div>
      </div>
    `;
  }).join('');
}

/**
 * Renders the full message inspection modal drawer.
 */
function renderSmsDetailModal(msg?: SmsRecord): string {
  if (!msg) return '';

  const isInbox = msg.smsType === 'inbox';
  const displaySender = msg.senderName.trim().length > 0 ? msg.senderName : msg.address;

  return `
    <div class="sms-modal-card">
      <div class="sms-modal-header">
        <div style="display:flex; flex-direction:column; min-width:0;">
          <span class="sms-modal-sender">${displaySender}</span>
          <span class="sms-modal-phone">${msg.address}</span>
        </div>
        <button id="btn-close-sms-modal" class="rs-icon-btn close-btn" title="Close">
          <img src="assets/icons/main-content/x.svg" alt="" style="width:12px; height:12px; filter:invert(1);" />
        </button>
      </div>

      <div class="sms-modal-body">
        <div class="sms-modal-bubble ${isInbox ? 'bubble-inbox' : 'bubble-sent'}">
          <p class="sms-modal-text">${escapeHtml(msg.body)}</p>
        </div>
      </div>

      <div class="sms-modal-footer">
        <div class="sms-meta-row">
          <span class="sms-meta-lbl">Received Time:</span>
          <span class="sms-meta-val">${formatFullDateTime(msg.timestamp)}</span>
        </div>
        ${msg.dateSent ? `
          <div class="sms-meta-row">
            <span class="sms-meta-lbl">Dispatched Time:</span>
            <span class="sms-meta-val">${formatFullDateTime(msg.dateSent)}</span>
          </div>
        ` : ''}
        <div class="sms-meta-row">
          <span class="sms-meta-lbl">Status & SIM:</span>
          <span class="sms-meta-val">${isInbox ? 'Delivered to Child' : 'Sent from Child'} • ${formatSimSlot(msg.simId) || 'Default SIM'}</span>
        </div>
      </div>
    </div>
  `;
}

/**
 * Binds search filtering, modal toggling, and keyboard shortcuts.
 */
function attachSmsListeners(container: HTMLElement): void {
  const input = container.querySelector<HTMLInputElement>('#sms-search-input');
  const listContainer = container.querySelector<HTMLElement>('#sms-list');
  const modalOverlay = container.querySelector<HTMLElement>('#sms-modal-overlay');

  input?.addEventListener('input', () => {
    activeSearchQuery = input.value;
    if (listContainer) {
      listContainer.innerHTML = renderSmsItems(rightSidebarState.getSmsMessages(), activeSearchQuery);
      rebindItemClicks(container);
    }
  });

  rebindItemClicks(container);

  // Close modal button
  const closeBtn = container.querySelector('#btn-close-sms-modal');
  closeBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeSmsModal(container);
  });

  // Clicking outside modal card closes it
  modalOverlay?.addEventListener('click', (e) => {
    if (e.target === modalOverlay) {
      closeSmsModal(container);
    }
  });
}

function rebindItemClicks(container: HTMLElement): void {
  const items = container.querySelectorAll<HTMLElement>('.sms-item');
  items.forEach(item => {
    item.addEventListener('click', () => {
      const id = item.dataset.id;
      if (!id) return;
      selectedSmsId = id;
      const modalOverlay = container.querySelector<HTMLElement>('#sms-modal-overlay');
      if (modalOverlay) {
        modalOverlay.style.display = 'flex';
        modalOverlay.innerHTML = renderSmsDetailModal(rightSidebarState.getSmsMessages().find(m => m.id === selectedSmsId));
        // Wire close button inside new modal
        const closeBtn = modalOverlay.querySelector('#btn-close-sms-modal');
        closeBtn?.addEventListener('click', () => closeSmsModal(container));
      }
    });
  });
}

function closeSmsModal(container: HTMLElement): void {
  selectedSmsId = null;
  const modalOverlay = container.querySelector<HTMLElement>('#sms-modal-overlay');
  if (modalOverlay) {
    modalOverlay.style.display = 'none';
    modalOverlay.innerHTML = '';
  }
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
