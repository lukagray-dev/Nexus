/**
 * @file download-manager.ts
 * @description Universal download destination resolver and atomic file saver for Monitor Storage.
 *
 * Hey friend! Every file download in Nexus Parent (table action, inspector drawer, or
 * high-res image preview modal) flows through this module so behavior stays 100% consistent:
 * 1. When "Ask Every Time Before Downloading" is enabled -> prompts with the native OS Save As
 *    dialog on every download and keeps the default `downloadsDirectory` setting untouched.
 * 2. When "Ask Every Time Before Downloading" is disabled:
 *    - On a fresh install (`downloadsDirectory` is empty), prompts ONCE with the native OS folder
 *      picker, persists the chosen directory to `settingsState`, and saves there.
 *    - On all subsequent downloads, saves directly into `downloadsDirectory` with zero prompts.
 * 3. In Tauri desktop runtime, chunks are streamed to Rust (`begin_download_file` ->
 *    `append_download_chunk` -> `commit_download_file`), which handles `.nexuspart` staging and
 *    auto-incrementing duplicate filenames (`photo.jpg` -> `photo (1).jpg`).
 * 4. Outside Tauri (browser preview or unit tests), gracefully falls back to `showSaveFilePicker`
 *    or standard `<a download>` links.
 */

import { settingsState } from '../../settings/sidebar/state.js';
import {
  appendDownloadChunk,
  beginDownloadFile,
  cancelDownloadFile,
  commitDownloadFile,
  getDefaultDownloadsDirectory,
  isTauriRuntime,
  pickDownloadsDirectory,
  pickSaveFilePath,
} from '../../shared/ipc.js';

const BASE64_CHUNK_BYTES = 65536;

/**
 * Resolved local target returned before initiating or committing a download.
 */
export interface ResolvedDownloadTarget {
  mode: 'tauri-directory' | 'tauri-exact' | 'browser-handle' | 'browser-fallback';
  targetDirectory?: string;
  exactFilePath?: string;
  // Native FileSystemFileHandle when running in Chromium outside Tauri
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fileHandle?: any;
}

export interface ResolveDownloadOptions {
  fileName: string;
  mimeType?: string;
  extension?: string;
  onToast?: (message: string) => void;
}

/**
 * Encodes a byte slice into standard Base64 without blowing the JS call stack.
 */
function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = '';
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    const slice = bytes.subarray(i, Math.min(i + step, bytes.length));
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

/**
 * Resolves where a downloaded file should be saved according to the universal
 * General & System download settings. Returns `null` if the user cancels a prompt.
 */
export async function resolveDownloadDestination(
  options: ResolveDownloadOptions
): Promise<ResolvedDownloadTarget | null> {
  const askEveryTime = settingsState.getValue('askBeforeDownload');
  const configuredDir = settingsState.getValue('downloadsDirectory').trim();

  if (isTauriRuntime()) {
    if (askEveryTime) {
      const exactFilePath = await pickSaveFilePath(
        options.fileName,
        configuredDir || undefined
      );
      if (!exactFilePath) {
        return null;
      }
      return {
        mode: 'tauri-exact',
        exactFilePath,
      };
    }

    let targetDirectory = configuredDir;
    if (!targetDirectory) {
      const suggestedDir = (await getDefaultDownloadsDirectory()) || undefined;
      const pickedDir = await pickDownloadsDirectory(suggestedDir);
      if (!pickedDir || !pickedDir.trim()) {
        return null;
      }

      targetDirectory = pickedDir.trim();
      settingsState.setValue('downloadsDirectory', targetDirectory);
      options.onToast?.(`Default downloads folder saved: ${targetDirectory}`);
    }

    return {
      mode: 'tauri-directory',
      targetDirectory,
    };
  }

  // Non-Tauri browser fallback (dev preview / web)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const win = typeof window !== 'undefined' ? (window as any) : undefined;
  if (askEveryTime && win && typeof win.showSaveFilePicker === 'function') {
    try {
      const ext = options.fileName.includes('.')
        ? options.fileName.split('.').pop() || ''
        : '';
      const pickerOpts: Record<string, unknown> = {
        suggestedName: options.fileName,
      };
      if (ext) {
        pickerOpts.types = [
          {
            description: `${options.extension || ext.toUpperCase()} file`,
            accept: {
              [options.mimeType || 'application/octet-stream']: [`.${ext}`],
            },
          },
        ];
      }

      const fileHandle = await win.showSaveFilePicker(pickerOpts);
      return {
        mode: 'browser-handle',
        fileHandle,
      };
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        return null;
      }
      console.warn('[DownloadManager] showSaveFilePicker fallback failed:', err);
    }
  }

  return { mode: 'browser-fallback' };
}

/**
 * Streams ordered Base64 chunks into Rust's atomic `.nexuspart` writer and commits to disk.
 */
export async function saveBase64ChunksToTauri(params: {
  transferId: string;
  target: ResolvedDownloadTarget;
  fileName: string;
  orderedBase64Chunks: string[];
}): Promise<string> {
  const { transferId, target, fileName, orderedBase64Chunks } = params;

  try {
    await beginDownloadFile({
      transferId,
      targetDirectory: target.targetDirectory,
      exactFilePath: target.exactFilePath,
      fileName,
    });

    for (const chunk of orderedBase64Chunks) {
      if (chunk.length > 0) {
        await appendDownloadChunk(transferId, chunk);
      }
    }

    const savedPath = await commitDownloadFile(transferId);
    return savedPath || target.exactFilePath || fileName;
  } catch (err) {
    await cancelDownloadFile(transferId);
    throw err;
  }
}

/**
 * Triggers a standard browser `<a download>` fallback save for a Blob URL.
 */
export function triggerBrowserBlobDownload(blobUrl: string, fileName: string): void {
  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/**
 * Saves an already-assembled in-memory `Blob` (such as a cached Image Preview)
 * using the universal download settings.
 */
export async function saveBlobWithUniversalSettings(params: {
  blob: Blob;
  blobUrl?: string;
  fileName: string;
  mimeType?: string;
  onToast?: (message: string) => void;
}): Promise<{ saved: boolean; path?: string }> {
  const { blob, blobUrl, fileName, mimeType, onToast } = params;
  const target = await resolveDownloadDestination({
    fileName,
    mimeType: mimeType || blob.type || 'application/octet-stream',
    onToast,
  });

  if (!target) {
    onToast?.('Download cancelled');
    return { saved: false };
  }

  if (target.mode === 'tauri-directory' || target.mode === 'tauri-exact') {
    const buffer = await blob.arrayBuffer();
    const bytes = new Uint8Array(buffer);
    const chunks: string[] = [];

    for (let offset = 0; offset < bytes.length; offset += BASE64_CHUNK_BYTES) {
      const slice = bytes.subarray(offset, Math.min(offset + BASE64_CHUNK_BYTES, bytes.length));
      chunks.push(uint8ArrayToBase64(slice));
    }

    const transferId = `preview-save-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const savedPath = await saveBase64ChunksToTauri({
      transferId,
      target,
      fileName,
      orderedBase64Chunks: chunks,
    });

    onToast?.(`Saved to: ${savedPath}`);
    return { saved: true, path: savedPath };
  }

  if (target.mode === 'browser-handle' && target.fileHandle) {
    const writable = await target.fileHandle.createWritable();
    await writable.write(blob);
    await writable.close();
    onToast?.(`Saved successfully: ${fileName}`);
    return { saved: true, path: fileName };
  }

  const resolvedUrl = blobUrl || URL.createObjectURL(blob);
  triggerBrowserBlobDownload(resolvedUrl, fileName);
  if (!blobUrl) {
    setTimeout(() => URL.revokeObjectURL(resolvedUrl), 15000);
  }
  onToast?.(`Download complete: ${fileName}`);
  return { saved: true, path: fileName };
}
