/**
 * @file telegram.ts
 * @description Telegram Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays Telegram channels, private chats, message snippets,
 * and search filtering for the child device.
 */

import { triggerSidebarToast } from '../shared.js';
import type { ChatItem } from '../types.js';

const INITIAL_TELEGRAM_CHATS: ChatItem[] = [
  { name: 'Coding Club HS', snippet: 'Liam: We are starting the Python robotics lab.', time: '10:15 AM' },
  { name: 'Tech News & Releases', snippet: 'Android 16 QPR1 beta announced with new security APIs.', time: '08:40 AM' },
  { name: 'Chess Enthusiasts', snippet: 'Game link: Challenge accepted by Magnusfan', time: 'Yesterday' }
];

/**
 * Creates and returns the Telegram Monitor panel element.
 */
export function renderTelegramPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-telegram-panel';

  container.innerHTML = `
    <!-- Search Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="tg-search-input" class="rs-search-input" placeholder="Search Telegram channels..." spellcheck="false" />
      </div>
    </div>

    <!-- Chats / Channels List -->
    <div id="tg-list" class="rs-list-container">
      ${INITIAL_TELEGRAM_CHATS.map(c => `
        <div class="rs-list-item tg-item" data-name="${c.name.toLowerCase()}" data-snippet="${c.snippet.toLowerCase()}">
          <div class="rs-item-top">
            <span class="rs-item-title">${c.name}</span>
            <span class="rs-item-time">${c.time}</span>
          </div>
          <div class="rs-item-snippet">${c.snippet}</div>
          <div class="rs-item-meta">
            <span>Telegram Channel / Chat</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  setTimeout(() => {
    const input = container.querySelector<HTMLInputElement>('#tg-search-input');
    const items = container.querySelectorAll<HTMLElement>('.tg-item');

    input?.addEventListener('input', () => {
      const q = input.value.toLowerCase().trim();
      items.forEach(item => {
        const match = item.dataset.name?.includes(q) || item.dataset.snippet?.includes(q);
        item.style.display = match ? 'flex' : 'none';
      });
    });

    items.forEach(item => {
      item.addEventListener('click', () => {
        triggerSidebarToast('Loading Telegram channel feed');
      });
    });
  }, 0);

  return container;
}

