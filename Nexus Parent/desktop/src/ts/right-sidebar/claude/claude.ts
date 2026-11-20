/**
 * @file claude.ts
 * @description Claude Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! Claude monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the Claude Monitor panel element.
 */
export function renderClaudePanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/claude/bot.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      Claude prompt and activity monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}

export const renderAiPanel = renderClaudePanel;
