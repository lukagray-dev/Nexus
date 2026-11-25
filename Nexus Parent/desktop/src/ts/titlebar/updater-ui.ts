/**
 * @file updater-ui.ts
 * @description Titlebar "Restart to update" button controller for Nexus Parent.
 *
 * Hey friend! Keeps the titlebar completely uncluttered when Nexus Parent is up to date:
 * - Button `#btn-restart-to-update` stays hidden whenever status is `idle`, `checking`,
 *   `up-to-date`, `downloading`, or `error`.
 * - As soon as Rust finishes downloading & verifying a newer GitHub stable release
 *   (`status === 'ready'`), the button becomes visible in the titlebar.
 * - Clicking it cleanly disconnects active WebRTC sessions and invokes `restartToUpdate()`.
 */

import {
  UpdateStatusPayload,
  getUpdateStatus,
  isTauriRuntime,
  listenTauriEvent,
  restartToUpdate,
} from '../shared/ipc.js';
import { deviceState } from '../shared/device-state.js';
import { showToast } from '../shared/toast.js';
import { EVENT_UPDATE_STATUS } from '../settings/main-content/general-system/auto-update.js';

/**
 * Updates visibility and tooltip of `#btn-restart-to-update` based on updater status.
 */
export function syncTitlebarUpdateButton(
  button: HTMLButtonElement | null,
  payload: UpdateStatusPayload | null
): void {
  if (!button) return;

  const isReady = payload?.status === 'ready';
  button.classList.toggle('hidden', !isReady);

  if (isReady && payload?.availableVersion) {
    button.setAttribute('title', `Restart to update to v${payload.availableVersion}`);
  } else {
    button.setAttribute('title', 'Restart to apply update');
  }
}

/**
 * Initializes the titlebar "Restart to update" button and subscribes to Rust updater events.
 */
export function setupTitlebarUpdateButton(): () => void {
  const button = document.getElementById('btn-restart-to-update') as HTMLButtonElement | null;
  if (!button) {
    return () => {};
  }

  // Ensure hidden by default until Rust confirms a staged update is ready
  syncTitlebarUpdateButton(button, null);

  let disposed = false;
  let unlistenStatus: (() => void) | null = null;

  if (isTauriRuntime()) {
    void getUpdateStatus().then((snapshot) => {
      if (!disposed) {
        syncTitlebarUpdateButton(button, snapshot);
      }
    });

    void listenTauriEvent<UpdateStatusPayload>(EVENT_UPDATE_STATUS, (payload) => {
      if (!disposed) {
        syncTitlebarUpdateButton(button, payload);
      }
    }).then((unlisten) => {
      if (disposed) {
        unlisten();
      } else {
        unlistenStatus = unlisten;
      }
    });
  }

  button.addEventListener('click', async (e) => {
    e.stopPropagation();
    button.disabled = true;
    try {
      deviceState.disconnect();
      await restartToUpdate();
    } catch (err: unknown) {
      button.disabled = false;
      const msg = err instanceof Error ? err.message : 'Failed to launch update';
      showToast(`Update failed: ${msg}`);
    }
  });

  return () => {
    disposed = true;
    if (unlistenStatus) {
      unlistenStatus();
      unlistenStatus = null;
    }
  };
}
