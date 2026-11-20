/**
 * @file about.ts
 * @description About Modal Dialog for Nexus Parent.
 *
 * Hey friend! This renders the native application About dialog:
 * - Displays brand identity, version, build architecture, and developer credentials.
 * - Adheres strictly to the dark theme tokens and zero-emoji production standards.
 */

/**
 * Opens the interactive About modal.
 */
export function openAboutDialog(): void {
  const existing = document.getElementById('about-dialog-overlay');
  if (existing) return;

  const overlay = document.createElement('div');
  overlay.id = 'about-dialog-overlay';
  overlay.className = 'settings-overlay';

  overlay.innerHTML = `
    <div class="settings-modal" style="max-width: 460px;">
      <div class="settings-header">
        <span class="settings-title">About Nexus Parent</span>
        <button id="btn-about-close" class="settings-close-btn" title="Close">✕</button>
      </div>
      <div class="settings-body" style="padding: 24px 20px; display: flex; flex-direction: column; align-items: center; text-align: center;">
        <div style="width: 64px; height: 64px; border-radius: 16px; background-color: #1a1a1a; border: 1px solid rgba(255, 255, 255, 0.16); display: flex; align-items: center; justify-content: center; margin-bottom: 16px; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);">
          <img src="assets/logo/nexus-parent.svg" alt="Nexus Parent Logo" style="width: 36px; height: 36px; object-fit: contain;" />
        </div>
        <h2 style="font-size: 18px; font-weight: 700; color: #ffffff; margin: 0 0 6px 0; letter-spacing: -0.3px;">Nexus Parent</h2>
        <span class="mono-badge" style="margin-bottom: 14px;">v0.1.0 (Production Release)</span>
        <p style="font-size: 13px; line-height: 1.6; color: var(--text-muted); margin: 0 0 20px 0; max-width: 380px;">
          Next-generation parental surveillance and child device management system built with Tauri v2, WebRTC P2P streams, and hardware-accelerated telemetry.
        </p>

        <div style="width: 100%; background: #181818; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px; padding: 12px 16px; text-align: left; font-size: 12px; display: flex; flex-direction: column; gap: 8px;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="color: var(--text-muted);">Runtime Host</span>
            <span style="color: #ffffff; font-weight: 500;">Tauri v2 + WebView2</span>
          </div>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="color: var(--text-muted);">Signaling</span>
            <span style="color: #ffffff; font-weight: 500;">Firebase Realtime Database</span>
          </div>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="color: var(--text-muted);">Creator</span>
            <span style="color: #ffffff; font-weight: 500;">Luka Gray</span>
          </div>
        </div>
      </div>
      <div class="settings-footer" style="padding: 12px 18px; background-color: #161616; border-top: 1px solid rgba(255, 255, 255, 0.08); display: flex; justify-content: flex-end;">
        <button id="btn-about-done" class="settings-btn settings-btn-primary" style="padding: 6px 20px;">Done</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const close = (): void => {
    if (overlay.parentElement) {
      overlay.parentElement.removeChild(overlay);
    }
  };

  overlay.querySelector('#btn-about-close')?.addEventListener('click', close);
  overlay.querySelector('#btn-about-done')?.addEventListener('click', close);
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      close();
    }
  });

  const onKeydown = (e: KeyboardEvent): void => {
    if (e.key === 'Escape') {
      close();
      window.removeEventListener('keydown', onKeydown);
    }
  };
  window.addEventListener('keydown', onKeydown);
}
