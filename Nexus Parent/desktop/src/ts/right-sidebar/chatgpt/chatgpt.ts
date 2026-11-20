/**
 * @file chatgpt.ts
 * @description ChatGPT Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! ChatGPT monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the ChatGPT Monitor panel element.
 */
export function renderChatGptPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/chatgpt/bot.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      ChatGPT prompt and activity monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}

export const renderAiPanel = renderChatGptPanel;
