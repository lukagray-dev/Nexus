/**
 * @file calllog.ts
 * @description Call Log Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays intercepted call logs (incoming, outgoing, missed)
 * with contact names, phone numbers, duration counters, and category filters.
 */

import type { CallLogItem } from '../types.js';

const INITIAL_CALL_RECORDS: CallLogItem[] = [
  { name: 'Sarah (Mom)', number: '+1 (555) 234-5678', type: 'incoming', duration: '03:42', time: '11:20 AM' },
  { name: 'Unknown Caller', number: '+1 (800) 555-0199', type: 'missed', duration: '00:00', time: '09:45 AM' },
  { name: 'Dad', number: '+1 (555) 345-6789', type: 'outgoing', duration: '01:15', time: 'Yesterday' },
  { name: 'Dr. Henderson Clinic', number: '+1 (555) 890-1234', type: 'incoming', duration: '04:10', time: 'Nov 09' },
  { name: 'Alex Johnson', number: '+1 (555) 456-7890', type: 'missed', duration: '00:00', time: 'Nov 08' }
];

/**
 * Creates and returns the Call Log Monitor panel element.
 */
export function renderCallLogPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-calllog-panel';

  container.innerHTML = `
    <!-- Category Filter Tabs -->
    <div class="rs-subbar">
      <div class="rs-filter-tabs">
        <button class="rs-tab-pill active" data-filter="all">All (5)</button>
        <button class="rs-tab-pill" data-filter="missed">Missed (2)</button>
        <button class="rs-tab-pill" data-filter="incoming">Incoming (2)</button>
        <button class="rs-tab-pill" data-filter="outgoing">Outgoing (1)</button>
      </div>
    </div>

    <!-- Call Records List -->
    <div id="call-list" class="rs-list-container">
      ${INITIAL_CALL_RECORDS.map(c => `
        <div class="rs-list-item call-item" data-type="${c.type}">
          <div class="rs-item-top">
            <div style="display:flex; align-items:center; gap:8px;">
              <span class="rs-icon-badge">
                <img src="assets/icons/main-content/${c.type === 'missed' ? 'phone-missed.svg' : c.type === 'incoming' ? 'phone-incoming.svg' : 'phone-outgoing.svg'}" alt="" />
              </span>
              <div style="display:flex; flex-direction:column;">
                <span class="rs-item-title">${c.name}</span>
                <span class="rs-item-meta">${c.number}</span>
              </div>
            </div>
            <div style="display:flex; flex-direction:column; align-items:flex-end; gap:2px;">
              <span class="rs-item-time">${c.time}</span>
              <span class="rs-badge-small ${c.type === 'missed' ? 'call-missed-badge' : ''}">${c.duration}</span>
            </div>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  // Attach tab filter listeners
  setTimeout(() => {
    const tabs = container.querySelectorAll<HTMLButtonElement>('.rs-tab-pill');
    const items = container.querySelectorAll<HTMLElement>('.call-item');

    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const filter = tab.dataset.filter;
        items.forEach(item => {
          item.style.display = (filter === 'all' || item.dataset.type === filter) ? 'flex' : 'none';
        });
      });
    });
  }, 0);

  return container;
}

