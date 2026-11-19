/**
 * @file keyboard.ts
 * @description Keystroke Logger & Input Audit Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! This panel displays real-time captured keystroke sequences and search queries
 * typed across different applications on the target child device.
 * It features dynamic search filtering, session history clearing, password masking indicators,
 * and live DataChannel telemetry synchronization.
 */

import { rightSidebarState } from '../state.js';
import { type KeyboardRecord, formatRelativeTime } from '../types.js';

let activeSearchQuery: string = '';

/**
 * Creates and returns the Keyboard Keystroke Audit panel element.
 */
export function renderKeyboardPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-keyboard-panel';

  const allRecords = rightSidebarState.getKeyboardRecords();
  const filteredRecords = filterRecords(allRecords, activeSearchQuery);

  container.innerHTML = `
    <!-- Search Subbar -->
    <div class="rs-subbar" style="flex-direction:column; gap:8px; align-items:stretch; height:auto; padding:10px 14px;">
      <div class="rs-search-wrap" style="width:100%;">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:12px;height:12px;filter:invert(1);opacity:0.5;" />
        <input type="text" id="keyboard-search-input" class="rs-search-input" placeholder="Search keystrokes or apps..." spellcheck="false" value="${escapeHtml(activeSearchQuery)}" />
      </div>

      <div class="keyboard-subbar-row">
        <div class="keyboard-telemetry-text">
          REAL-TIME KEYSTROKE CAPTURE • ${allRecords.length} LOGGED
        </div>
        ${allRecords.length > 0 ? `
          <button id="btn-keyboard-clear" class="keyboard-clear-btn" title="Clear recorded keystrokes">
            Clear
          </button>
        ` : ''}
      </div>
    </div>

    <!-- Keystroke Log Stream -->
    <div id="keyboard-list" class="rs-list-container">
      ${filteredRecords.length === 0 ? renderEmptyState(allRecords.length > 0) : renderRecordItems(filteredRecords)}
    </div>
  `;

  // Attach search and clear event listeners
  setTimeout(() => {
    attachKeyboardListeners(container);
  }, 0);

  return container;
}

/**
 * Renders individual keystroke cards.
 */
function renderRecordItems(records: KeyboardRecord[]): string {
  return records.map(record => {
    const formattedTime = formatRelativeTime(record.timestamp);
    const hintHtml = record.fieldHint ? `<div class="keyboard-field-hint">${escapeHtml(record.fieldHint)}</div>` : '';

    return `
      <div class="rs-list-item keyboard-item" data-id="${record.id}">
        <div class="rs-item-top">
          <div style="display:flex; align-items:center; gap:6px; min-width:0;">
            <span class="rs-icon-badge keyboard-icon-badge">
              <img src="assets/icons/main-content/keyboard.svg" alt="" style="width:11px;height:11px;" />
            </span>
            <span class="rs-item-title keyboard-app-title">${escapeHtml(record.appName)}</span>
          </div>
          <span class="rs-item-time">${formattedTime}</span>
        </div>

        ${hintHtml}

        <div class="keyboard-snippet-box">${escapeHtml(record.text)}</div>

        <div class="keyboard-meta-row">
          <span class="keyboard-pill">${escapeHtml(record.inputType)}</span>
          ${record.isPassword ? '<span class="keyboard-pill-pwd">PASSWORD</span>' : ''}
        </div>
      </div>
    `;
  }).join('');
}

/**
 * Renders the empty state when no keystrokes are recorded or matching search results.
 */
function renderEmptyState(hasUnfilteredRecords: boolean): string {
  if (hasUnfilteredRecords) {
    return `
      <div style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:40px 20px; text-align:center; color:var(--text-muted);">
        <img src="assets/icons/left-sidebar/search.svg" alt="" style="width:24px; height:24px; opacity:0.3; filter:invert(1); margin-bottom:12px;" />
        <div style="font-size:12px; font-weight:500; color:#ffffff; margin-bottom:4px;">No matching keystrokes</div>
        <div style="font-size:11px;">Try searching for a different keyword or app name</div>
      </div>
    `;
  }

  return `
    <div style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:40px 20px; text-align:center; color:var(--text-muted);">
      <img src="assets/icons/main-content/keyboard.svg" alt="" style="width:28px; height:28px; opacity:0.25; filter:invert(1); margin-bottom:12px;" />
      <div style="font-size:12px; font-weight:500; color:#ffffff; margin-bottom:4px;">No keystroke activity recorded</div>
      <div style="font-size:11px; max-width:240px; line-height:1.4;">Click the Play button in the bottom dock to start real-time keyboard auditing</div>
    </div>
  `;
}

/**
 * Filters keystroke records against the search query.
 */
function filterRecords(records: KeyboardRecord[], query: string): KeyboardRecord[] {
  const trimmed = query.trim().toLowerCase();
  if (!trimmed) return records;

  return records.filter(r =>
    r.appName.toLowerCase().includes(trimmed) ||
    r.app.toLowerCase().includes(trimmed) ||
    r.text.toLowerCase().includes(trimmed) ||
    (r.fieldHint && r.fieldHint.toLowerCase().includes(trimmed))
  );
}

/**
 * Binds search input and clear history action handlers.
 */
function attachKeyboardListeners(container: HTMLElement): void {
  const searchInput = container.querySelector<HTMLInputElement>('#keyboard-search-input');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      activeSearchQuery = (e.target as HTMLInputElement).value;
      const listContainer = container.querySelector<HTMLElement>('#keyboard-list');
      if (listContainer) {
        const allRecords = rightSidebarState.getKeyboardRecords();
        const filtered = filterRecords(allRecords, activeSearchQuery);
        listContainer.innerHTML = filtered.length === 0 ? renderEmptyState(allRecords.length > 0) : renderRecordItems(filtered);
      }
    });
  }

  const clearBtn = container.querySelector<HTMLButtonElement>('#btn-keyboard-clear');
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      rightSidebarState.clearKeyboardRecords();
    });
  }
}

/**
 * Safely escapes HTML special characters to prevent script injection.
 */
function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
