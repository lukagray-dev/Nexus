/**
 * @file whatsapp.ts
 * @description WhatsApp Activity Monitor Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! WhatsApp monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the WhatsApp Activity panel element.
 */
export function renderWhatsAppPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/whatsapp/message-circle.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      WhatsApp monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}
