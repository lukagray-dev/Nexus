/**
 * @file telegram.ts
 * @description Telegram Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! Telegram monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the Telegram Monitor panel element.
 */
export function renderTelegramPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/telegram/send.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      Telegram monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}
