/**
 * @file empty-state.ts
 * @description Pairing & Child Device Connection Screen (Phase 1 of Empty State) for Nexus Parent.
 *
 * Hey friend! This renders the "Enter Child ID" screen whenever no child phone is connected.
 * - Clean standard numeric input without hyphen separators.
 * - Real-time validation, clearing action, and keyboard Enter shortcut.
 * - Wires directly into deviceState to transition to the connected dashboard.
 */

import { deviceState } from '../../shared/device-state.js';

/**
 * Renders the Phase 1 "Connect Child Device" pairing card.
 *
 * @param onConnected Callback invoked when connection succeeds
 * @returns The container HTMLElement
 */
export function renderPairingScreen(onConnected: () => void): HTMLElement {
  const card = document.createElement('div');
  card.className = 'empty-connect-card';

  card.innerHTML = `
    <!-- Card Brand & Icon Header -->
    <div class="connect-card-header">
      <div class="connect-brand-icon">
        <img src="assets/logo/nexus-child.svg" alt="Nexus Child Logo" />
      </div>
      <h1 class="connect-card-title">Connect Child Device</h1>
      <p class="connect-card-subtitle">
        Enter the 12-digit Child ID provided by the Nexus Child app on your child's phone to establish an encrypted monitoring connection.
      </p>
    </div>

    <!-- 12-Digit Child ID Form -->
    <div class="connect-form">
      <div class="connect-input-header">
        <label class="connect-input-label" for="child-id-input">CHILD DEVICE ID</label>
        <span class="mono-badge">12 DIGITS</span>
      </div>

      <div class="connect-input-wrapper">
        <span class="input-prefix-icon">
          <img src="assets/icons/main-content/empty-state/key-round.svg" alt="" />
        </span>
        <input
          type="text"
          id="child-id-input"
          class="child-id-input"
          placeholder="Enter 12-digit Child ID"
          maxlength="12"
          autocomplete="off"
          spellcheck="false"
          inputmode="numeric"
        />
        <button id="btn-clear-id" class="btn-clear-id" title="Clear input" style="display: none;">✕</button>
      </div>

      <!-- Live Validation & Progress Feedback -->
      <div id="connect-feedback" class="connect-feedback">
        <span class="feedback-text">Enter the 12-digit identifier from the child app</span>
      </div>

      <!-- Primary Action Buttons -->
      <div class="connect-actions">
        <button id="btn-connect-device" class="btn-connect-primary" disabled>
          <img src="assets/icons/main-content/empty-state/link.svg" alt="" style="filter: invert(0); width: 14px; height: 14px;" />
          <span>Connect Device</span>
        </button>
      </div>

      <!-- Footer Help Hint -->
      <div class="connect-footer-hint">
        <img src="assets/icons/main-content/empty-state/info.svg" alt="" style="filter: invert(1); width: 13px; height: 13px; opacity: 0.6;" />
        <span>Hint: Open Nexus Child on your child's phone to view the <strong>12-digit Child ID</strong>.</span>
      </div>
    </div>
  `;

  // Attach event handlers
  setTimeout(() => {
    bindPairingEvents(card, onConnected);
  }, 0);

  return card;
}

/**
 * Binds input typing, validation, and connection submission events.
 */
function bindPairingEvents(card: HTMLElement, onConnected: () => void): void {
  const input = card.querySelector<HTMLInputElement>('#child-id-input');
  const clearBtn = card.querySelector<HTMLButtonElement>('#btn-clear-id');
  const connectBtn = card.querySelector<HTMLButtonElement>('#btn-connect-device');
  const feedback = card.querySelector<HTMLElement>('#connect-feedback');

  if (!input || !connectBtn || !feedback) return;

  /**
   * Filters input to strictly digits (0-9) capped at 12 characters.
   * No hyphens or spaces are inserted, giving a natural and normal input experience.
   */
  const cleanInput = (val: string): string => {
    return val.replace(/\D/g, '').slice(0, 12);
  };

  /**
   * Updates clear button visibility, validation messages, and Connect button readiness.
   */
  const validate = (): void => {
    const raw = cleanInput(input.value);
    if (clearBtn) {
      clearBtn.style.display = raw.length > 0 ? 'flex' : 'none';
    }

    if (raw.length === 0) {
      feedback.innerHTML = '<span class="feedback-text">Enter the 12-digit identifier from the child app</span>';
      connectBtn.disabled = true;
    } else if (raw.length < 12) {
      feedback.innerHTML = `<span class="feedback-text text-ready">${12 - raw.length} more digits needed</span>`;
      connectBtn.disabled = true;
    } else {
      feedback.innerHTML = '<span class="feedback-text text-ready">Valid 12-digit ID. Ready to connect.</span>';
      connectBtn.disabled = false;
    }
  };

  // Keystroke input handler: clean and validate without inserting hyphens
  input.addEventListener('input', () => {
    const selStart = input.selectionStart || 0;
    const cleaned = cleanInput(input.value);
    input.value = cleaned;
    validate();
    // Maintain cursor position
    input.setSelectionRange(selStart, selStart);
  });

  // Paste handler: strips non-numeric characters and enforces 12-digit maximum
  input.addEventListener('paste', (e) => {
    e.preventDefault();
    const text = (e.clipboardData || (window as unknown as { clipboardData: DataTransfer }).clipboardData)?.getData('text') || '';
    input.value = cleanInput(text);
    validate();
  });

  // Clear button click handler
  clearBtn?.addEventListener('click', () => {
    input.value = '';
    validate();
    input.focus();
  });

  // Submission handler
  const submitConnection = async (): Promise<void> => {
    const raw = cleanInput(input.value);
    if (raw.length < 12) return;

    connectBtn.disabled = true;
    feedback.innerHTML = '<span class="feedback-text text-connecting">Authenticating anonymously...</span>';

    try {
      await deviceState.connectLive(raw, (progressText) => {
        feedback.innerHTML = `<span class="feedback-text text-connecting">${progressText}</span>`;
      });
      feedback.innerHTML = '<span class="feedback-text text-ready">WebRTC link established!</span>';
      setTimeout(() => {
        onConnected();
      }, 300);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to connect. Please verify device ID.';
      feedback.innerHTML = `<span class="feedback-text text-error">${message}</span>`;
      connectBtn.disabled = false;
    }
  };

  connectBtn.addEventListener('click', () => {
    void submitConnection();
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !connectBtn.disabled) {
      void submitConnection();
    }
  });

  input.focus();
}
