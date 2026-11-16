/**
 * @file notifications.ts
 * @description Notifications Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays intercepted push notifications from the child device,
 * complete with app packaging identifiers, instant text searching, and timestamps.
 */

import type { NotificationItem } from '../types.js';

const INITIAL_NOTIFICATIONS: NotificationItem[] = [
  { app: 'Instagram', title: 'New follower request', desc: 'emma_w wants to follow you.', time: '4m ago', pkg: 'com.instagram.android' },
  { app: 'YouTube', title: 'New Video uploaded', desc: 'Veritasium: How Quantum Computers Really Work', time: '18m ago', pkg: 'com.google.android.youtube' },
  { app: 'Snapchat', title: 'Snap received', desc: 'New Snap from Lucas (Streak 84)', time: '45m ago', pkg: 'com.snapchat.android' },
  { app: 'Google Classroom', title: 'Assignment Due Tomorrow', desc: 'Biology Chapter 4 Cell Division due at 11:59 PM', time: '2h ago', pkg: 'com.google.android.apps.classroom' },
  { app: 'Duolingo', title: 'Keep your streak alive!', desc: 'Take 3 minutes to practice Spanish today.', time: '5h ago', pkg: 'com.duolingo' }
];

/**
 * Creates and returns the Notifications Monitor panel element.
 */
export function renderNotificationsPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-notifications-panel';

  container.innerHTML = `
    <!-- Filter Search Subbar -->
    <div class="rs-subbar">
      <div class="rs-search-wrap">
        <img src="assets/icons/sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="notif-search-input" class="rs-search-input" placeholder="Filter notifications..." spellcheck="false" />
      </div>
    </div>

    <!-- Notifications List -->
    <div id="notif-list" class="rs-list-container">
      ${INITIAL_NOTIFICATIONS.map(n => `
        <div class="rs-list-item notif-item" data-app="${n.app.toLowerCase()}" data-text="${n.title.toLowerCase()} ${n.desc.toLowerCase()}">
          <div class="rs-item-top">
            <span class="rs-badge-small notif-app-badge">${n.app}</span>
            <span class="rs-item-time">${n.time}</span>
          </div>
          <div class="rs-item-title notif-card-title">${n.title}</div>
          <div class="rs-item-snippet notif-card-desc">${n.desc}</div>
          <div class="rs-item-meta notif-card-pkg">
            <span>${n.pkg}</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  // Search filtering
  setTimeout(() => {
    const input = container.querySelector<HTMLInputElement>('#notif-search-input');
    const items = container.querySelectorAll<HTMLElement>('.notif-item');

    input?.addEventListener('input', () => {
      const q = input.value.toLowerCase().trim();
      items.forEach(item => {
        const match = item.dataset.app?.includes(q) || item.dataset.text?.includes(q);
        item.style.display = match ? 'flex' : 'none';
      });
    });
  }, 0);

  return container;
}


export const renderNotificationPanel = renderNotificationsPanel;

