/**
 * @file ipc.ts
 * @description Safe typed wrapper around Tauri v2 native desktop window, tray, event & IPC APIs.
 *
 * Hey friend! This module wraps Tauri's window controls, tray/startup commands, and event listeners.
 * If opened in a regular web browser tab or Node.js test runner, every helper falls back gracefully!
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
declare global {
  interface Window {
    __TAURI__?: {
      window?: {
        getCurrentWindow?: () => {
          minimize: () => Promise<void>;
          unminimize?: () => Promise<void>;
          maximize: () => Promise<void>;
          unmaximize: () => Promise<void>;
          toggleMaximize: () => Promise<void>;
          close: () => Promise<void>;
          show?: () => Promise<void>;
          hide?: () => Promise<void>;
          setFocus?: () => Promise<void>;
          isMaximized: () => Promise<boolean>;
        };
      };
      core?: {
        invoke: <T = any>(cmd: string, args?: Record<string, unknown>) => Promise<T>;
      };
      event?: {
        listen: <T = any>(
          event: string,
          handler: (event: { event: string; payload: T }) => void
        ) => Promise<() => void>;
      };
    };
  }
}

/**
 * Valid close-button (`[X]`) behaviors enforced by the Rust window lifecycle manager.
 */
export type CloseActionMode = 'tray' | 'exit';

/**
 * Snapshot of native desktop window/tray settings returned by Rust `get_window_tray_settings`.
 */
export interface WindowTraySettingsSnapshot {
  closeAction: CloseActionMode;
  launchMinimized: boolean;
  autostartEnabled: boolean;
}

/**
 * Persisted window/tray settings payload returned by Rust setter commands.
 */
export interface WindowTraySettingsRecord {
  closeAction: CloseActionMode;
  launchMinimized: boolean;
}

/**
 * Request to minimize the native application window to the OS taskbar.
 */
export async function minimizeWindow(): Promise<void> {
  const tauriWindow = window.__TAURI__?.window?.getCurrentWindow?.();
  if (tauriWindow) {
    try {
      await tauriWindow.minimize();
    } catch (err) {
      console.error('[IPC] Failed to minimize window:', err);
    }
  } else {
    console.warn('[IPC] minimizeWindow called outside of Tauri environment.');
  }
}

/**
 * Request to maximize or restore the native application window.
 */
export async function maximizeWindow(): Promise<void> {
  const tauriWindow = window.__TAURI__?.window?.getCurrentWindow?.();
  if (tauriWindow) {
    try {
      await tauriWindow.toggleMaximize();
    } catch (err) {
      console.error('[IPC] Failed to toggle maximize window:', err);
    }
  } else {
    console.warn('[IPC] maximizeWindow called outside of Tauri environment.');
  }
}

/**
 * Request to close the native application window.
 * Hey friend! When "Close Button [X] Behavior" is set to "Minimize to Tray",
 * Rust intercepts this request, keeps the WebView alive, and hides the window to the tray.
 */
export async function closeWindow(): Promise<void> {
  const tauriWindow = window.__TAURI__?.window?.getCurrentWindow?.();
  if (tauriWindow) {
    try {
      await tauriWindow.close();
    } catch (err) {
      console.error('[IPC] Failed to close window:', err);
    }
  } else {
    console.warn('[IPC] closeWindow called outside of Tauri environment.');
  }
}

/**
 * Query whether the native window is currently maximized.
 */
export async function isWindowMaximized(): Promise<boolean> {
  const tauriWindow = window.__TAURI__?.window?.getCurrentWindow?.();
  if (tauriWindow) {
    try {
      return await tauriWindow.isMaximized();
    } catch (err) {
      console.error('[IPC] Failed to check maximized status:', err);
      return false;
    }
  }
  return false;
}

/**
 * Invoke a native Rust Tauri command safely.
 */
export async function invokeCommand<T = any>(command: string, args?: Record<string, unknown>): Promise<T | null> {
  const invoke = typeof window !== 'undefined' ? window.__TAURI__?.core?.invoke : undefined;
  if (invoke) {
    try {
      return await invoke<T>(command, args);
    } catch (err) {
      console.error(`[IPC] Command "${command}" failed:`, err);
      return null;
    }
  }
  console.warn(`[IPC] Command "${command}" called outside of Tauri environment.`);
  return null;
}

/**
 * Invoke a native Rust Tauri command and throw a descriptive error on failure
 * (returns `null` only when running outside Tauri, e.g., browser preview or unit tests).
 */
export async function invokeCommandStrict<T = any>(
  command: string,
  args?: Record<string, unknown>
): Promise<T | null> {
  const invoke = typeof window !== 'undefined' ? window.__TAURI__?.core?.invoke : undefined;
  if (!invoke) {
    return null;
  }
  return await invoke<T>(command, args);
}

/**
 * Subscribes to a backend Tauri event safely with automatic fallback outside Tauri.
 */
export async function listenTauriEvent<T = unknown>(
  eventName: string,
  handler: (payload: T) => void
): Promise<() => void> {
  const listen = typeof window !== 'undefined' ? window.__TAURI__?.event?.listen : undefined;
  if (!listen) {
    return () => {};
  }

  try {
    return await listen<T>(eventName, (event) => {
      handler(event.payload);
    });
  } catch (err) {
    console.error(`[IPC] Failed to subscribe to event "${eventName}":`, err);
    return () => {};
  }
}

/**
 * Queries persisted native window & tray settings from the Rust backend.
 */
export async function getWindowTraySettings(): Promise<WindowTraySettingsSnapshot | null> {
  return await invokeCommand<WindowTraySettingsSnapshot>('get_window_tray_settings');
}

/**
 * Persists the `[X]` close button behavior (`tray` vs `exit`) in the Rust backend.
 */
export async function setCloseActionSetting(action: CloseActionMode): Promise<boolean> {
  const invoke = typeof window !== 'undefined' ? window.__TAURI__?.core?.invoke : undefined;
  if (!invoke) {
    // Outside Tauri (browser preview / Node tests), state remains in localStorage.
    return true;
  }

  try {
    await invoke<WindowTraySettingsRecord>('set_close_action', { action });
    return true;
  } catch (err) {
    console.error('[IPC] Failed to update close action in Rust backend:', err);
    return false;
  }
}

/**
 * Enables or disables OS autostart (`--minimized`) and persists state in the Rust backend.
 * Throws if the native OS startup update fails so the caller can revert the UI switch.
 */
export async function setLaunchMinimizedSetting(enabled: boolean): Promise<boolean> {
  const invoke = typeof window !== 'undefined' ? window.__TAURI__?.core?.invoke : undefined;
  if (!invoke) {
    return true;
  }

  await invoke<WindowTraySettingsRecord>('set_launch_minimized', { enabled });
  return true;
}

/**
 * Hides the main window directly into the OS system tray.
 */
export async function hideWindowToTray(): Promise<void> {
  await invokeCommand<void>('hide_to_tray');
}

/**
 * Restores and focuses the main window from the OS system tray.
 */
export async function showMainWindow(): Promise<void> {
  await invokeCommand<void>('show_main_window');
}

/**
 * Open an external web URL using the system browser via Tauri or window.open fallback.
 */
export async function openExternalUrl(url: string): Promise<void> {
  const normalized = url.trim();
  if (!normalized) return;

  const invoke = window.__TAURI__?.core?.invoke;
  if (invoke) {
    try {
      await invoke('plugin:opener|open_url', { url: normalized });
      return;
    } catch {
      try {
        await invoke('open_external_url', { url: normalized });
        return;
      } catch {
        // Fallback to standard web open
      }
    }
  }

  window.open(normalized, '_blank');
}

/**
 * Exits the native application process cleanly, bypassing minimize-to-tray interception.
 */
export async function exitApplication(): Promise<void> {
  const invoke = typeof window !== 'undefined' ? window.__TAURI__?.core?.invoke : undefined;
  if (invoke) {
    try {
      await invoke('exit_application');
      return;
    } catch (err) {
      console.error('[IPC] Failed to invoke exit_application:', err);
    }
  }
  await closeWindow();
}
