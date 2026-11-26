/**
 * @file credits-links.ts
 * @description Creator attribution, GNU AGPL v3.0 license notice, and project links.
 *
 * Hey friend! Surfaces official maintainer, license, copyright, and external links
 * matching the repository `NOTICE`, `LICENSE`, and Titlebar Help menu.
 */

import { openExternalUrl } from '../../../shared/ipc.js';
import {
  DOCUMENTATION_URL,
  FOLLOW_CREATOR_URL,
  REPORT_BUG_URL,
  REPOSITORY_URL,
} from '../../../titlebar/menu.js';
import { createSettingsGroup } from '../helpers.js';

export const CREATOR_EMAIL_URL = 'mailto:heylukagray@gmail.com';

/**
 * Renders the Credits, License & Project Links group for the About page.
 */
export function renderCreditsLinksGroup(): HTMLElement {
  const infoRow = document.createElement('div');
  infoRow.className = 'settings-row';
  infoRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Luka Gray (aka Soumo Mukherjee) • West Bengal, India</span>
      <span class="settings-label-desc">
        Copyright © 2026 • Licensed under GNU Affero General Public License v3.0 or later (AGPLv3)
      </span>
    </div>
    <span class="settings-val-badge">AGPL-3.0</span>
  `;

  const linksRow = document.createElement('div');
  linksRow.className = 'settings-row-vertical';
  linksRow.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">Project & Support Resources</span>
      <span class="settings-label-desc">Open official documentation, source code, issue tracker, or creator channels</span>
    </div>
    <div class="settings-about-links-grid">
      <button type="button" id="btn-about-link-repo" class="settings-btn settings-btn-secondary">GitHub Repository</button>
      <button type="button" id="btn-about-link-docs" class="settings-btn settings-btn-secondary">Documentation</button>
      <button type="button" id="btn-about-link-issue" class="settings-btn settings-btn-secondary">Report Issue</button>
      <button type="button" id="btn-about-link-follow" class="settings-btn settings-btn-secondary">Follow Creator</button>
      <button type="button" id="btn-about-link-email" class="settings-btn settings-btn-secondary">Contact Email</button>
    </div>
  `;

  linksRow.querySelector('#btn-about-link-repo')?.addEventListener('click', () => {
    void openExternalUrl(REPOSITORY_URL);
  });
  linksRow.querySelector('#btn-about-link-docs')?.addEventListener('click', () => {
    void openExternalUrl(DOCUMENTATION_URL);
  });
  linksRow.querySelector('#btn-about-link-issue')?.addEventListener('click', () => {
    void openExternalUrl(REPORT_BUG_URL);
  });
  linksRow.querySelector('#btn-about-link-follow')?.addEventListener('click', () => {
    void openExternalUrl(FOLLOW_CREATOR_URL);
  });
  linksRow.querySelector('#btn-about-link-email')?.addEventListener('click', () => {
    void openExternalUrl(CREATOR_EMAIL_URL);
  });

  return createSettingsGroup('CREDITS, LICENSE & LINKS', [infoRow, linksRow]);
}
