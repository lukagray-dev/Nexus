/**
 * @file wellbeing.ts
 * @description Digital Wellbeing & App Usage Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays daily screen time metrics, device unlock counters,
 * notification volume, and detailed app-by-app usage progress bars.
 */

import type { WellbeingAppItem } from '../types.js';

const INITIAL_APP_USAGE: WellbeingAppItem[] = [
  { app: 'YouTube', time: '1h 45m', percent: 45 },
  { app: 'Chrome', time: '1h 10m', percent: 30 },
  { app: 'Instagram', time: '42m', percent: 18 },
  { app: 'Google Classroom', time: '35m', percent: 15 },
  { app: 'Spotify', time: '28m', percent: 12 }
];

/**
 * Creates and returns the Wellbeing Usage panel element.
 */
export function renderWellbeingPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-wellbeing-panel';

  container.innerHTML = `
    <!-- Top Summary Card -->
    <div class="rs-subbar wellbeing-summary-subbar">
      <div class="wellbeing-stat-header">
        <span class="wellbeing-stat-label">TODAY'S SCREEN TIME</span>
        <span class="wellbeing-stat-value">4h 40m</span>
      </div>
      <div class="wellbeing-metrics-row">
        <span>Unlocks: 48 times</span>
        <span>Notifications: 184</span>
      </div>
    </div>

    <!-- Usage Bars List -->
    <div id="wellbeing-list" class="rs-list-container">
      ${INITIAL_APP_USAGE.map(u => `
        <div class="usage-app-row">
          <div class="usage-app-meta">
            <span class="usage-app-name">${u.app}</span>
            <span class="usage-app-time">${u.time}</span>
          </div>
          <div class="usage-progress-bar">
            <div class="usage-progress-fill" style="width: ${u.percent}%;"></div>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  return container;
}

