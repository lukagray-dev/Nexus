/**
 * @file wellbeing.ts
 * @description Digital Wellbeing Monitoring Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel displays real-time screen time, unlock counts, app launches,
 * hourly/daily activity histograms, and per-app usage breakdowns from the child phone:
 * 1. Range Switching: Supports `Today` (hourly breakdown) and `7 Days` (daily breakdown).
 * 2. Live Synchronization: Driven by the bottom dock Play/Pause confirmation flow (`WELLBEING_ON` / `WELLBEING_OFF`)
 *    with 15-second live updates from the child plus an inline manual Refresh trigger.
 * 3. Search & Category Filtering: Filter apps by name, package, or category (`Social`, `Video`, `Games`, `Productivity`, `Other`).
 * 4. Monochrome Tactical Theme: Pure Black & White UI with vector SVG icons (zero emojis).
 */

import { rightSidebarState } from '../state.js';
import { triggerSidebarToast } from '../shared.js';
import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import {
  type WellbeingAppCategory,
  type WellbeingAppUsage,
  type WellbeingRange,
  type WellbeingSummary,
  type WellbeingTimeBucket,
  CMD_WELLBEING_RANGE_PREFIX,
  CMD_WELLBEING_REFRESH,
  formatRelativeTime,
  formatScreenTimeDuration
} from '../types.js';

let activeSearchQuery = '';
let activeCategoryFilter: 'all' | 'social' | 'video' | 'game' | 'productivity' | 'other' = 'all';

const CATEGORY_LABELS: Record<WellbeingAppCategory, string> = {
  social: 'Social',
  video: 'Video',
  audio: 'Audio',
  game: 'Games',
  productivity: 'Productivity',
  news: 'News',
  maps: 'Maps',
  other: 'Other'
};

/**
 * Formats an Android app category identifier into a concise display label.
 */
export function formatWellbeingCategoryLabel(category: WellbeingAppCategory): string {
  return CATEGORY_LABELS[category] ?? 'Other';
}

/**
 * Filters Digital Wellbeing app entries by category pill and search query.
 */
export function filterWellbeingApps(
  apps: WellbeingAppUsage[],
  query: string,
  category: string
): WellbeingAppUsage[] {
  const q = query.trim().toLowerCase();

  return apps.filter((app) => {
    if (category !== 'all') {
      if (category === 'other') {
        const isMajor =
          app.category === 'social' ||
          app.category === 'video' ||
          app.category === 'game' ||
          app.category === 'productivity';
        if (isMajor) return false;
      } else if (app.category !== category) {
        return false;
      }
    }

    if (!q) return true;
    const catLabel = formatWellbeingCategoryLabel(app.category).toLowerCase();
    return (
      app.appName.toLowerCase().includes(q) ||
      app.packageName.toLowerCase().includes(q) ||
      catLabel.includes(q)
    );
  });
}

/**
 * Creates and returns the Digital Wellbeing inspection panel element.
 */
export function renderWellbeingPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-wellbeing-panel';

  const monitoringState = rightSidebarState.getWellbeingMonitoringState();
  const range = rightSidebarState.getWellbeingRange();
  const summary = rightSidebarState.getWellbeingSummary();
  const apps = rightSidebarState.getWellbeingApps();
  const buckets = rightSidebarState.getWellbeingBuckets();
  const errorMsg = rightSidebarState.getWellbeingError();
  const filteredApps = filterWellbeingApps(apps, activeSearchQuery, activeCategoryFilter);

  container.innerHTML = `
    <!-- Subbar: Range Toggle, Refresh, Search, Summary KPIs -->
    <div class="rs-subbar wellbeing-summary-subbar" style="height:auto; padding:12px 14px; align-items:stretch;">
      <div class="wellbeing-controls-top">
        <div class="wellbeing-range-group" id="wellbeing-range-group">
          <button type="button" class="wellbeing-range-btn ${range === 'today' ? 'is-active' : ''}" data-range="today">Today</button>
          <button type="button" class="wellbeing-range-btn ${range === '7d' ? 'is-active' : ''}" data-range="7d">7 Days</button>
        </div>
        <button type="button" id="btn-wellbeing-refresh" class="wellbeing-refresh-btn" title="Refresh Usage Telemetry">
          <img src="assets/icons/settings/refresh-cw.svg" alt="" style="width:11px; height:11px; filter:invert(1);" />
          <span>Refresh</span>
        </button>
      </div>

      ${errorMsg ? `
        <div class="wellbeing-error-banner" id="wellbeing-error-banner">
          <span>${escapeHtml(errorMsg)}</span>
        </div>
      ` : ''}

      <div class="wellbeing-kpi-grid">
        <div class="wellbeing-kpi-card">
          <span class="wellbeing-stat-label">SCREEN TIME</span>
          <span class="wellbeing-stat-value">${summary ? formatScreenTimeDuration(summary.totalScreenTimeMs) : '--'}</span>
        </div>
        <div class="wellbeing-kpi-card">
          <span class="wellbeing-stat-label">UNLOCKS</span>
          <span class="wellbeing-stat-value">${summary ? summary.unlockCount : '--'}</span>
        </div>
        <div class="wellbeing-kpi-card">
          <span class="wellbeing-stat-label">LAUNCHES</span>
          <span class="wellbeing-stat-value">${summary ? summary.totalLaunches : '--'}</span>
        </div>
      </div>

      ${renderActiveAppStrip(summary, range)}

      ${renderHistogramSection(buckets, range)}

      <div class="rs-search-wrap" style="width:100%;">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="wellbeing-search-input" class="rs-search-input" placeholder="Search apps or categories..." spellcheck="false" value="${escapeHtml(activeSearchQuery)}" />
      </div>

      <div class="wellbeing-category-bar">
        ${renderCategoryPills(apps)}
      </div>
    </div>

    <!-- Per-App Usage List -->
    <div id="wellbeing-app-list" class="rs-list-container">
      ${filteredApps.length === 0 ? renderEmptyState(apps.length > 0, monitoringState === 'LIVE', errorMsg) : renderAppUsageRows(filteredApps)}
    </div>
  `;

  setTimeout(() => {
    attachWellbeingListeners(container);
  }, 0);

  return container;
}

function renderActiveAppStrip(summary: WellbeingSummary | null, range: WellbeingRange): string {
  if (!summary) return '';
  const updatedText = formatRelativeTime(summary.generatedAt);
  const activeText =
    range === 'today'
      ? summary.activeAppName
        ? `Active: ${escapeHtml(summary.activeAppName)}`
        : 'Foreground idle'
      : '7-Day Usage Summary';

  return `
    <div class="wellbeing-metrics-row">
      <span class="wellbeing-active-indicator">${activeText}</span>
      <span>${range === 'today' ? `Synced ${escapeHtml(updatedText)}` : `Updated ${escapeHtml(updatedText)}`}</span>
    </div>
  `;
}

function renderHistogramSection(buckets: WellbeingTimeBucket[], range: WellbeingRange): string {
  if (!buckets || buckets.length === 0) return '';

  const maxMs = Math.max(1, ...buckets.map((b) => b.durationMs));
  const barsHtml = buckets
    .map((bucket, idx) => {
      const pct = bucket.durationMs > 0 ? Math.max(8, Math.round((bucket.durationMs / maxMs) * 100)) : 0;
      const showLabel = range === '7d' || idx % 6 === 0 || idx === buckets.length - 1;
      const tooltip = `${bucket.label}${range === 'today' ? ':00' : ''} — ${formatScreenTimeDuration(bucket.durationMs)}`;
      return `
        <div class="wellbeing-bar-col" title="${escapeHtml(tooltip)}">
          <div class="wellbeing-bar-track">
            <div class="wellbeing-bar-fill" style="height:${pct}%;"></div>
          </div>
          <span class="wellbeing-bar-label">${showLabel ? escapeHtml(bucket.label) : ''}</span>
        </div>
      `;
    })
    .join('');

  return `
    <div class="wellbeing-chart-card">
      <div class="wellbeing-chart-header">
        <span class="wellbeing-stat-label">${range === 'today' ? 'HOURLY ACTIVITY' : 'DAILY ACTIVITY (7D)'}</span>
        <span class="wellbeing-chart-peak">Peak ${formatScreenTimeDuration(maxMs)}</span>
      </div>
      <div class="wellbeing-chart-bars ${range === '7d' ? 'is-weekly' : 'is-hourly'}">
        ${barsHtml}
      </div>
    </div>
  `;
}

function renderCategoryPills(apps: WellbeingAppUsage[]): string {
  const categories: Array<{ id: typeof activeCategoryFilter; label: string }> = [
    { id: 'all', label: 'All' },
    { id: 'social', label: 'Social' },
    { id: 'video', label: 'Video' },
    { id: 'game', label: 'Games' },
    { id: 'productivity', label: 'Work' },
    { id: 'other', label: 'Other' }
  ];

  return categories
    .map((cat) => {
      const count =
        cat.id === 'all' ? apps.length : filterWellbeingApps(apps, '', cat.id).length;
      const isActive = activeCategoryFilter === cat.id;
      return `
        <button type="button" class="wellbeing-cat-pill ${isActive ? 'active' : ''}" data-cat="${cat.id}">
          <span>${cat.label}</span>
          <span class="wellbeing-cat-count">${count}</span>
        </button>
      `;
    })
    .join('');
}

function renderAppUsageRows(apps: WellbeingAppUsage[]): string {
  return apps
    .map((app) => {
      const durationStr = formatScreenTimeDuration(app.totalTimeMs);
      const categoryLabel = formatWellbeingCategoryLabel(app.category);
      const widthPct = Math.max(2, Math.min(100, app.percent));

      return `
        <div class="usage-app-row" data-pkg="${escapeHtml(app.packageName)}">
          <div class="usage-app-meta">
            <div class="usage-app-title-group">
              <span class="usage-app-name">${escapeHtml(app.appName)}</span>
              <span class="usage-app-pkg">${escapeHtml(app.packageName)}</span>
            </div>
            <div class="usage-app-stats-right">
              <span class="usage-app-time">${escapeHtml(durationStr)}</span>
              <span class="usage-app-pct">${app.percent}%</span>
            </div>
          </div>
          <div class="usage-progress-bar">
            <div class="usage-progress-fill" style="width:${widthPct}%;"></div>
          </div>
          <div class="usage-app-footer">
            <span class="usage-category-badge">${escapeHtml(categoryLabel)}</span>
            <span>${app.launchCount} launch${app.launchCount === 1 ? '' : 'es'}</span>
          </div>
        </div>
      `;
    })
    .join('');
}

function renderEmptyState(
  hasAppsBeforeFilter: boolean,
  isLive: boolean,
  errorMsg: string | null
): string {
  if (hasAppsBeforeFilter) {
    return `
      <div class="rs-empty-state">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:24px;height:24px;opacity:0.3;filter:invert(1);margin-bottom:8px;" />
        <span class="rs-empty-title">No Matching Apps</span>
        <span class="rs-empty-desc">Try clearing the search query or switching category filters.</span>
      </div>
    `;
  }

  return `
    <div class="rs-empty-state">
      <img src="assets/icons/right-sidebar/wellbeing/activity.svg" alt="" style="width:30px;height:30px;opacity:0.3;filter:invert(1);margin-bottom:8px;" />
      <span class="rs-empty-title">${errorMsg ? 'Usage Access Required' : isLive ? 'Awaiting Usage Telemetry' : 'Digital Wellbeing Paused'}</span>
      <span class="rs-empty-desc">
        ${
          errorMsg
            ? escapeHtml(errorMsg)
            : isLive
            ? 'Collecting screen time, app launches, and hourly activity from the child device...'
            : 'Click the Play button in the bottom dock to fetch screen time and begin live monitoring.'
        }
      </span>
    </div>
  `;
}

function attachWellbeingListeners(container: HTMLElement): void {
  const rangeBtns = container.querySelectorAll<HTMLButtonElement>('.wellbeing-range-btn');
  rangeBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const nextRange = (btn.dataset.range === '7d' ? '7d' : 'today') as WellbeingRange;
      if (rightSidebarState.getWellbeingRange() === nextRange) return;

      rightSidebarState.setWellbeingRange(nextRange);
      if (rightSidebarState.getWellbeingMonitoringState() === 'LIVE') {
        const sent = webrtcManager.sendCommand(`${CMD_WELLBEING_RANGE_PREFIX}${nextRange}`);
        if (!sent) {
          triggerSidebarToast('Cannot switch range: DataChannel not connected');
        }
      }
    });
  });

  const refreshBtn = container.querySelector<HTMLButtonElement>('#btn-wellbeing-refresh');
  refreshBtn?.addEventListener('click', () => {
    const activeRange = rightSidebarState.getWellbeingRange();
    const sent = webrtcManager.sendCommand(
      JSON.stringify({ type: CMD_WELLBEING_REFRESH, range: activeRange })
    );
    if (sent) {
      triggerSidebarToast('Refreshing Digital Wellbeing telemetry...');
    } else {
      triggerSidebarToast('Cannot refresh: DataChannel not connected');
    }
  });

  const searchInput = container.querySelector<HTMLInputElement>('#wellbeing-search-input');
  searchInput?.addEventListener('input', () => {
    activeSearchQuery = searchInput.value;
    updateWellbeingListDom(container);
  });

  const catPills = container.querySelectorAll<HTMLButtonElement>('.wellbeing-cat-pill');
  catPills.forEach((pill) => {
    pill.addEventListener('click', () => {
      const cat = (pill.dataset.cat || 'all') as typeof activeCategoryFilter;
      activeCategoryFilter = cat;
      catPills.forEach((p) => p.classList.toggle('active', p === pill));
      updateWellbeingListDom(container);
    });
  });
}

function updateWellbeingListDom(container: HTMLElement): void {
  const listEl = container.querySelector<HTMLElement>('#wellbeing-app-list');
  if (!listEl) return;

  const apps = rightSidebarState.getWellbeingApps();
  const isLive = rightSidebarState.getWellbeingMonitoringState() === 'LIVE';
  const errorMsg = rightSidebarState.getWellbeingError();
  const filtered = filterWellbeingApps(apps, activeSearchQuery, activeCategoryFilter);

  listEl.innerHTML =
    filtered.length === 0
      ? renderEmptyState(apps.length > 0, isLive, errorMsg)
      : renderAppUsageRows(filtered);
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
