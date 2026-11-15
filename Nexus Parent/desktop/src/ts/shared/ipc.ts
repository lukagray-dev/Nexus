/**
 * @file ipc.ts
 * @description Safe typed wrapper around Tauri v2 native desktop window & IPC APIs.
 *
 * Hey friend! This module wraps Tauri's window controls and command invokes.
 * If opened in a regular web browser tab for styling or testing, it falls back gracefully!
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
declare global {
  interface Window {
    __TAURI__?: {
      window?: {
        getCurrentWindow?: () => {
          minimize: () => Promise<void>;
          maximize: () => Promise<void>;
          unmaximize: () => Promise<void>;
          toggleMaximize: () => Promise<void>;
          close: () => Promise<void>;
          isMaximized: () => Promise<boolean>;
        };
      };
      core?: {
        invoke: <T = any>(cmd: string, args?: Record<string, unknown>) => Promise<T>;
      };
    };
  }
}

/**
 * Request to minimize the native application window.
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
  const invoke = window.__TAURI__?.core?.invoke;
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
