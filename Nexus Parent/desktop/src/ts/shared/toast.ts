/**
 * @file toast.ts
 * @description Global non-intrusive feedback toast notification system for Nexus Parent.
 *
 * Hey friend! This module provides lightweight, floating toast alerts adhering
 * to the dark theme tokens and strict production design standards (no emojis).
 */

let toastTimeout: ReturnType<typeof setTimeout> | null = null;

/**
 * Displays a temporary floating tactical toast notification.
 *
 * @param message The text message to display
 * @param durationMs Duration in milliseconds before fading out (default: 2500ms)
 */
export function showToast(message: string, durationMs: number = 2500): void {
  // Gracefully no-op in headless/Node test environments where DOM is not loaded
  if (typeof document === 'undefined') return;

  let toastEl = document.getElementById('app-global-toast');

  if (!toastEl) {
    toastEl = document.createElement('div');
    toastEl.id = 'app-global-toast';
    toastEl.className = 'app-global-toast';
    toastEl.innerHTML = `
      <img src="assets/icons/main-content/empty-state/info.svg" alt="" class="toast-icon" />
      <span id="app-global-toast-text" class="toast-text"></span>
    `;
    document.body.appendChild(toastEl);
  }

  const textEl = toastEl.querySelector<HTMLElement>('#app-global-toast-text');
  if (textEl) {
    textEl.textContent = message;
  }

  // Clear previous timer if currently showing
  if (toastTimeout) {
    clearTimeout(toastTimeout);
    toastTimeout = null;
  }

  // Trigger smooth appearance animation
  toastEl.classList.add('visible');

  toastTimeout = setTimeout(() => {
    toastEl?.classList.remove('visible');
    toastTimeout = null;
  }, durationMs);
}
