/**
 * @file sms.ts
 * @description SMS Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays intercepted and synchronized SMS text messages
 * from the target child device, including search filtering and conversation previews.
 */

import { triggerSidebarToast } from '../shared.js';
import type { SmsItem } from '../types.js';

const INITIAL_SMS_CONVERSATIONS: SmsItem[] = [
  { sender: 'Mom (Sarah)', text: 'Don’t forget your lunch box today! Love you.', time: '10:14 AM', unread: false },
  { sender: 'School Alert (Admin)', text: 'Reminder: Early release this Friday at 1:30 PM.', time: '09:05 AM', unread: true },
  { sender: 'Alex Johnson', text: 'Hey bro, are you coming to soccer practice after school?', time: 'Yesterday', unread: false },
  { sender: '+1 (555) 019-2834', text: 'Your verification code is 492019. Do not share this with anyone.', time: 'Nov 09', unread: false },
  { sender: 'Dad', text: 'Call me when you get on the bus.', time: 'Nov 08', unread: false }
];

/**
 * Creates and returns the SMS Monitor panel element.
 */
export function renderSmsPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-sms-panel';

  container.innerHTML = `
    <!-- Search Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="sms-search-input" class="rs-search-input" placeholder="Search SMS messages..." spellcheck="false" />
      </div>
    </div>

    <!-- Messages List -->
    <div id="sms-list" class="rs-list-container">
      ${INITIAL_SMS_CONVERSATIONS.map(c => `
        <div class="rs-list-item sms-item" data-sender="${c.sender.toLowerCase()}" data-text="${c.text.toLowerCase()}">
          <div class="rs-item-top">
            <span class="rs-item-title">${c.sender}</span>
            <span class="rs-item-time">${c.time}</span>
          </div>
          <div class="rs-item-snippet">${c.text}</div>
          <div class="rs-item-meta">
            ${c.unread ? '<span class="rs-badge-small sms-unread-badge">NEW</span>' : ''}
            <span>SMS Message</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  // Attach search filtering and thread click listeners
  setTimeout(() => {
    const input = container.querySelector<HTMLInputElement>('#sms-search-input');
    const items = container.querySelectorAll<HTMLElement>('.sms-item');

    input?.addEventListener('input', () => {
      const q = input.value.toLowerCase().trim();
      items.forEach(item => {
        const match = item.dataset.sender?.includes(q) || item.dataset.text?.includes(q);
        item.style.display = match ? 'flex' : 'none';
      });
    });

    items.forEach(item => {
      item.addEventListener('click', () => {
        triggerSidebarToast('Viewing full SMS thread');
      });
    });
  }, 0);

  return container;
}


export const renderGmailPanel = renderSmsPanel;

