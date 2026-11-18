/**
 * @file calllog.ts
 * @description Call Log Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel displays intercepted call history and real-time live calls
 * (incoming, outgoing, missed, rejected, blocked) with contact names, numbers,
 * duration counters, category filters, and detailed technical metadata.
 */

import { rightSidebarState } from '../state.js';
import {
  type CallRecord,
  formatCallDuration,
  formatRelativeTime,
  formatFullDateTime,
  formatSimSlot
} from '../types.js';

let activeFilter: 'all' | 'missed' | 'incoming' | 'outgoing' | 'rejected' = 'all';
let expandedCallId: string | null = null;

/**
 * Creates and returns the Call Log Monitor panel element.
 */
export function renderCallLogPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-calllog-panel';

  const callRecords = rightSidebarState.getCallLogs();

  // Calculate dynamic tab counts
  const countAll = callRecords.length;
  const countMissed = callRecords.filter(c => c.callType === 'missed').length;
  const countIncoming = callRecords.filter(c => c.callType === 'incoming').length;
  const countOutgoing = callRecords.filter(c => c.callType === 'outgoing').length;
  const countRejected = callRecords.filter(c => c.callType === 'rejected' || c.callType === 'blocked').length;

  container.innerHTML = `
    <!-- Category Filter Tabs -->
    <div class="rs-subbar">
      <div class="rs-filter-tabs">
        <button class="rs-tab-pill ${activeFilter === 'all' ? 'active' : ''}" data-filter="all">All (${countAll})</button>
        <button class="rs-tab-pill ${activeFilter === 'missed' ? 'active' : ''}" data-filter="missed">Missed (${countMissed})</button>
        <button class="rs-tab-pill ${activeFilter === 'incoming' ? 'active' : ''}" data-filter="incoming">Incoming (${countIncoming})</button>
        <button class="rs-tab-pill ${activeFilter === 'outgoing' ? 'active' : ''}" data-filter="outgoing">Outgoing (${countOutgoing})</button>
        <button class="rs-tab-pill ${activeFilter === 'rejected' ? 'active' : ''}" data-filter="rejected">Declined (${countRejected})</button>
      </div>
    </div>

    <!-- Call Records List -->
    <div id="call-list" class="rs-list-container">
      ${callRecords.length === 0 ? renderCallLogEmptyState() : renderCallItems(callRecords)}
    </div>
  `;

  // Attach tab filter and expand listeners
  setTimeout(() => {
    attachCallLogListeners(container);
  }, 0);

  return container;
}

/**
 * Renders empty state when no call records are present.
 */
function renderCallLogEmptyState(): string {
  const isLive = rightSidebarState.getCallLogMonitoringState() === 'LIVE';
  return `
    <div class="rs-empty-state">
      <img src="assets/icons/left-sidebar/phone-call.svg" alt="" style="width: 32px; height: 32px; opacity: 0.35; filter: invert(1); margin-bottom: 8px;" />
      <span class="rs-empty-title">No Call History</span>
      <span class="rs-empty-desc">
        ${isLive
          ? 'Listening for calls in real time. Incoming and outgoing calls will appear here automatically.'
          : 'Click the Play button in the bottom dock to fetch latest 100 call records and begin live monitoring.'}
      </span>
    </div>
  `;
}

/**
 * Generates markup for the list of call records.
 */
function renderCallItems(records: CallRecord[]): string {
  return records.map(c => {
    const isExpanded = expandedCallId === c.id;
    const isMissedOrRejected = c.callType === 'missed' || c.callType === 'rejected' || c.callType === 'blocked';
    const iconName = c.callType === 'missed'
      ? 'phone-missed.svg'
      : c.callType === 'incoming'
      ? 'phone-incoming.svg'
      : c.callType === 'rejected'
      ? 'phone-missed.svg'
      : 'phone-outgoing.svg';

    const displayName = c.name.trim().length > 0 ? c.name : c.number;
    const metaNumber = c.name.trim().length > 0 ? `${c.number} • ${c.numberType || 'General'}` : (c.numberType || 'General');

    return `
      <div class="rs-list-item call-item ${isExpanded ? 'is-expanded' : ''}" data-id="${c.id}" data-type="${c.callType}">
        <div class="rs-item-top">
          <div style="display:flex; align-items:center; gap:8px; min-width:0;">
            <span class="rs-icon-badge">
              <img src="assets/icons/main-content/${iconName}" alt="" style="width:13px; height:13px; filter:invert(1);" />
            </span>
            <div style="display:flex; flex-direction:column; min-width:0;">
              <span class="rs-item-title" title="${displayName}">${displayName}</span>
              <span class="rs-item-meta" title="${metaNumber}">${metaNumber}</span>
            </div>
          </div>
          <div style="display:flex; flex-direction:column; align-items:flex-end; gap:2px; flex-shrink:0;">
            <span class="rs-item-time" title="${formatFullDateTime(c.timestamp)}">${formatRelativeTime(c.timestamp)}</span>
            <span class="rs-badge-small ${isMissedOrRejected ? 'call-missed-badge' : ''}">${formatCallDuration(c.duration)}</span>
          </div>
        </div>

        <!-- Optional Chips (SIM, Location) -->
        <div class="call-chips-row">
          ${c.simId ? `<span class="call-chip sim-chip">${formatSimSlot(c.simId)}</span>` : ''}
          ${c.location ? `<span class="call-chip loc-chip">${c.location}</span>` : ''}
          ${c.features && c.features.includes('VIDEO') ? `<span class="call-chip feat-chip">VIDEO</span>` : ''}
          ${c.features && c.features.includes('HD_VOICE') ? `<span class="call-chip feat-chip">HD</span>` : ''}
        </div>

        <!-- Expanded Detail Drawer -->
        ${isExpanded ? `
          <div class="call-expanded-details">
            <div class="call-detail-row"><span class="detail-label">Exact Time:</span><span class="detail-val">${formatFullDateTime(c.timestamp)}</span></div>
            <div class="call-detail-row"><span class="detail-label">Duration:</span><span class="detail-val">${c.duration}s (${formatCallDuration(c.duration)})</span></div>
            <div class="call-detail-row"><span class="detail-label">Direction:</span><span class="detail-val text-uppercase">${c.callType}</span></div>
            <div class="call-detail-row"><span class="detail-label">Phone:</span><span class="detail-val">${c.number}</span></div>
            ${c.location ? `<div class="call-detail-row"><span class="detail-label">Location:</span><span class="detail-val">${c.location}</span></div>` : ''}
            ${c.simId ? `<div class="call-detail-row"><span class="detail-label">SIM Slot:</span><span class="detail-val">${formatSimSlot(c.simId)}</span></div>` : ''}
          </div>
        ` : ''}
      </div>
    `;
  }).join('');
}

/**
 * Attaches filter tab clicks and item expandable toggles.
 */
function attachCallLogListeners(container: HTMLElement): void {
  const tabs = container.querySelectorAll<HTMLButtonElement>('.rs-tab-pill');
  const items = container.querySelectorAll<HTMLElement>('.call-item');

  // Filter tab click
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      activeFilter = (tab.dataset.filter || 'all') as typeof activeFilter;

      items.forEach(item => {
        const itemType = item.dataset.type || '';
        let match = false;
        if (activeFilter === 'all') match = true;
        else if (activeFilter === 'rejected') match = itemType === 'rejected' || itemType === 'blocked';
        else match = itemType === activeFilter;

        item.style.display = match ? 'flex' : 'none';
      });
    });
  });

  // Apply initial filter visibility
  if (activeFilter !== 'all') {
    items.forEach(item => {
      const itemType = item.dataset.type || '';
      let match = false;
      if (activeFilter === 'rejected') match = itemType === 'rejected' || itemType === 'blocked';
      else match = itemType === activeFilter;
      item.style.display = match ? 'flex' : 'none';
    });
  }

  // Expand/collapse detail drawer on item click
  items.forEach(item => {
    item.addEventListener('click', () => {
      const id = item.dataset.id;
      expandedCallId = expandedCallId === id ? null : (id || null);
      // Re-render list container
      const listContainer = container.querySelector('#call-list');
      if (listContainer) {
        listContainer.innerHTML = renderCallItems(rightSidebarState.getCallLogs());
        attachCallLogListeners(container);
      }
    });
  });
}
