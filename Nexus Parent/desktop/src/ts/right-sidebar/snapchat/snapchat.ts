/**
 * @file whatsapp.ts
 * @description WhatsApp Activity Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays intercepted WhatsApp chats, groups, message snippets,
 * and unread indicators from the child device.
 */

import { triggerSidebarToast } from '../shared.js';
import type { ChatItem } from '../types.js';

const INITIAL_WHATSAPP_CHATS: ChatItem[] = [
  { name: 'Family Group', snippet: 'Mom: Please remember to take vitamins.', time: '11:02 AM', unread: 2 },
  { name: 'Ethan Miller', snippet: 'Did you finish question 5 on the math sheet?', time: '09:30 AM', unread: 0 },
  { name: 'Soccer Team', snippet: 'Coach: Jersey pickup scheduled tomorrow.', time: 'Yesterday', unread: 0 },
  { name: 'Study Group - Bio', snippet: 'Ashley shared "Bio_Review_Notes.pdf"', time: 'Nov 09', unread: 0 },
  { name: 'Grandma Rose', snippet: 'Have a wonderful day at school sweetheart!', time: 'Nov 07', unread: 0 }
];

/**
 * Creates and returns the WhatsApp Activity panel element.
 */
export function renderWhatsAppPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-whatsapp-panel';

  container.innerHTML = `
    <!-- Search Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="wa-search-input" class="rs-search-input" placeholder="Search WhatsApp chats..." spellcheck="false" />
      </div>
    </div>

    <!-- Chats List -->
    <div id="wa-list" class="rs-list-container">
      ${INITIAL_WHATSAPP_CHATS.map(c => `
        <div class="rs-list-item wa-item" data-name="${c.name.toLowerCase()}" data-snippet="${c.snippet.toLowerCase()}">
          <div class="rs-item-top">
            <span class="rs-item-title">${c.name}</span>
            <span class="rs-item-time">${c.time}</span>
          </div>
          <div class="rs-item-snippet">${c.snippet}</div>
          <div class="rs-item-meta">
            ${c.unread ? `<span class="rs-badge-small wa-unread-badge">${c.unread} NEW</span>` : ''}
            <span>WhatsApp Messenger</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  setTimeout(() => {
    const input = container.querySelector<HTMLInputElement>('#wa-search-input');
    const items = container.querySelectorAll<HTMLElement>('.wa-item');

    input?.addEventListener('input', () => {
      const q = input.value.toLowerCase().trim();
      items.forEach(item => {
        const match = item.dataset.name?.includes(q) || item.dataset.snippet?.includes(q);
        item.style.display = match ? 'flex' : 'none';
      });
    });

    items.forEach(item => {
      item.addEventListener('click', () => {
        triggerSidebarToast('Loading WhatsApp conversation history');
      });
    });
  }, 0);

  return container;
}


export const renderSnapchatPanel = renderWhatsAppPanel;

