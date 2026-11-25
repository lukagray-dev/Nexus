/**
 * @file dvr-duration.ts
 * @description Rolling DVR Memory Retention dropdown setting.
 *
 * Hey friend! Configures how many minutes of live surveillance video are buffered
 * in RAM for instant DVR scrubbing.
 */

import { settingsState } from '../../sidebar/state.js';

/**
 * Renders the Rolling DVR Memory Retention dropdown setting row.
 */
export function renderDvrDurationSetting(): HTMLElement {
  const row = document.createElement('div');
  row.className = 'settings-row';

  const currentDuration = settingsState.getValue('dvrBufferDuration');

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Rolling DVR Memory Retention</span>
      <span class="settings-label-desc">Timeline depth retained in parent RAM for instant rewinds and scrubber snaps</span>
    </div>
    <select id="setting-dvr-duration" class="settings-select">
      <option value="15m" ${currentDuration === '15m' ? 'selected' : ''}>15 Minutes (~140 MB RAM)</option>
      <option value="30m" ${currentDuration === '30m' ? 'selected' : ''}>30 Minutes (~280 MB RAM, Default)</option>
      <option value="60m" ${currentDuration === '60m' ? 'selected' : ''}>60 Minutes (~560 MB RAM)</option>
    </select>
  `;

  const dvrSelect = row.querySelector<HTMLSelectElement>('#setting-dvr-duration');
  dvrSelect?.addEventListener('change', () => {
    settingsState.setValue('dvrBufferDuration', dvrSelect.value as '15m' | '30m' | '60m');
  });

  return row;
}
