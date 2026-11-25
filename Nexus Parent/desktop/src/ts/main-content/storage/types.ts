/**
 * @file types.ts
 * @description Type definitions and protocol contracts for the Remote Storage Monitoring feature.
 *
 * Hey friend! This file holds the contracts between our desktop frontend and the Android child:
 * - DataChannel command strings and response types (including smart cache checks).
 * - Sibling data structures representing files, folders, progressive batches, and directory cache entries.
 * - Transfer session models for reassembling chunked downloads and in-app image previews.
 */

// Protocol Commands (Parent -> Child)
export const CMD_STORAGE_CHECK_DIR = 'STORAGE_CHECK_DIR';
export const CMD_STORAGE_LIST = 'STORAGE_LIST';
export const CMD_STORAGE_GET_FILE = 'STORAGE_GET_FILE';
export const CMD_STORAGE_CANCEL_TRANSFER = 'STORAGE_CANCEL_TRANSFER';
export const CMD_STORAGE_DELETE_FILE = 'STORAGE_DELETE_FILE';

// Protocol Responses & Events (Child -> Parent)
export const TYPE_STORAGE_DIR_UNCHANGED = 'STORAGE_DIR_UNCHANGED';
export const TYPE_STORAGE_DIR_CHANGED = 'STORAGE_DIR_CHANGED';
export const TYPE_STORAGE_LIST_BATCH = 'STORAGE_LIST_BATCH';
export const TYPE_STORAGE_LIST_ERROR = 'STORAGE_LIST_ERROR';
export const TYPE_STORAGE_FILE_START = 'STORAGE_FILE_START';
export const TYPE_STORAGE_FILE_CHUNK = 'STORAGE_FILE_CHUNK';
export const TYPE_STORAGE_FILE_COMPLETE = 'STORAGE_FILE_COMPLETE';
export const TYPE_STORAGE_FILE_ERROR = 'STORAGE_FILE_ERROR';
export const TYPE_STORAGE_DELETE_SUCCESS = 'STORAGE_DELETE_SUCCESS';
export const TYPE_STORAGE_DELETE_ERROR = 'STORAGE_DELETE_ERROR';

/**
 * High-level categories for UI filtering.
 */
export type FileCategory = 'all' | 'image' | 'video' | 'audio' | 'document' | 'archive' | 'other';

/**
 * Representation of a remote file or directory on the child device.
 */
export interface RemoteFileItem {
  id: string;
  name: string;
  path: string;
  isFolder: boolean;
  size: string;
  rawBytes: number;
  modified: string;
  rawModified: number;
  type: FileCategory;
  extension: string;
  mimeType: string;
  thumbnail?: string | null;
}

/**
 * Raw item structure received from Android Child in STORAGE_LIST_BATCH.
 */
export interface RawStorageItemPayload {
  name: string;
  absolutePath: string;
  size: number;
  modified: number;
  isDirectory: boolean;
  mimeType: string;
  category: string;
  thumbnail?: string | null;
}

/**
 * Batch envelope received over the DataChannel with pagination support.
 */
export interface StorageListBatchPacket {
  type: typeof TYPE_STORAGE_LIST_BATCH;
  path: string;
  items: RawStorageItemPayload[];
  isLast: boolean;
  totalCount: number;
  offset?: number;
  limit?: number;
  hasMore?: boolean;
  nextOffset?: number;
  dirModified?: number;
  requestId?: string | null;
}

/**
 * Cached directory entry stored in desktop memory.
 */
export interface DirectoryCacheEntry {
  path: string;
  items: RemoteFileItem[];
  totalCount: number;
  hasMore: boolean;
  nextOffset: number;
  dirModified: number;
  cachedAt: number;
}

/**
 * State representing an active chunked file transfer (download or in-app preview).
 */
export interface FileTransferSession {
  transferId: string;
  path: string;
  fileName: string;
  size: number;
  totalChunks: number;
  receivedChunks: Map<number, string>;
  purpose: 'download' | 'preview';
  mimeType: string;
  progressPercent: number;
  targetDirectory?: string; // Configured default folder for automatic saves
  exactFilePath?: string; // Explicit destination path chosen via OS Save As dialog
  fileHandle?: any; // Native FileSystemFileHandle fallback when running outside Tauri
}

/**
 * Converts a raw byte count into a human-friendly formatted string (e.g. "4.2 MB").
 */
export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const val = bytes / Math.pow(1024, i);
  return `${val.toFixed(val >= 100 || i === 0 ? 0 : 1)} ${units[i]}`;
}

/**
 * Converts epoch timestamp into human-readable relative time (e.g. "12 mins ago", "Yesterday").
 */
export function formatRelativeTime(epochMillis: number): string {
  if (!epochMillis || epochMillis <= 0) return 'Unknown';
  const now = Date.now();
  const diffSec = Math.floor((now - epochMillis) / 1000);

  if (diffSec < 60) return 'Just now';
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} mins ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} hours ago`;
  if (diffSec < 172800) return 'Yesterday';

  const date = new Date(epochMillis);
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: date.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined
  });
}
