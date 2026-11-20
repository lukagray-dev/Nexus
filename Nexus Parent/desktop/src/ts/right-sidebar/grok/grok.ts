/**
 * @file grok.ts
 * @description Grok Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! Grok monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the Grok Monitor panel element.
 */
export function renderGrokPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/grok/bot.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      Grok prompt and activity monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}

export const renderAiPanel = renderGrokPanel;
