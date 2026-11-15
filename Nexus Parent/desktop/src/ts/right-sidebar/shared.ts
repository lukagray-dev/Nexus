/**
 * @file shared.ts
 * @description Shared UI helpers for Nexus Parent Right Sidebar panels.
 *
 * Hey friend! This helper displays non-intrusive feedback toasts inside the
 * right sidebar when users execute actions (e.g. vibrating, locking apps, refreshing).
 */

/**
 * Triggers a temporary toast notification inside the right sidebar.
 */
export function triggerSidebarToast(text: string): void {
  const toast = document.getElementById('rs-toast');
  const toastText = document.getElementById('rs-toast-text');
  if (toast && toastText) {
    toastText.textContent = text;
    toast.classList.add('visible');
    setTimeout(() => {
      toast.classList.remove('visible');
    }, 2400);
  }
}
