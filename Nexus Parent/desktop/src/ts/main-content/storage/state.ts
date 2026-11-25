/**
 * @file state.ts
 * @description Central reactive state manager for the Remote Storage Monitoring subsystem.
 *
 * Hey friend! This state manager coordinates all live data for the storage monitor view:
 * - Active directory path (e.g. "/storage/emulated/0") and breadcrumb tracking.
 * - Directory Cache: In-memory cache of folder listings with instant display and smart modification checking.
 * - Progressive Pagination: Sliced page loading (e.g. 40 items/chunk) with hasMore and nextOffset tracking.
 * - Image Preview Cache: In-memory Blob URL cache so previously previewed images open instantaneously.
 * - Active category filtering ("all", "image", "video", "audio", "document", "archive").
 * - Live search text filter query.
 * - Selected file for the metadata inspector.
 * - Active chunked file transfer sessions (downloads with native FileSystemFileHandle and in-app image previews).
 * - Target item for the tactical deletion confirmation dialog.
 */

import {
  type RemoteFileItem,
  type RawStorageItemPayload,
  type FileCategory,
  type FileTransferSession,
  type DirectoryCacheEntry,
  formatBytes,
  formatRelativeTime
} from './types.js';

export type StorageStateChangeListener = () => void;

class StorageStateManager {
  private currentPath: string = '/storage/emulated/0';
  private items: RemoteFileItem[] = [];
  private activeCategory: FileCategory = 'all';
  private searchQuery: string = '';
  private selectedItem: RemoteFileItem | null = null;
  private isLoadingDirectory: boolean = false;
  private isLoadingMore: boolean = false;
  private hasMoreItems: boolean = false;
  private nextOffset: number = 0;
  private totalCount: number = 0;
  private dirModified: number = 0;

  // Caching systems
  private directoryCache: Map<string, DirectoryCacheEntry> = new Map();
  private imagePreviewCache: Map<string, string> = new Map();

  // Active transfers and modals
  private activeTransfers: Map<string, FileTransferSession> = new Map();
  private imagePreviewUrl: string | null = null;
  private imagePreviewName: string | null = null;
  private imagePreviewBlob: Blob | null = null;
  private imagePreviewBlobCache: Map<string, Blob> = new Map();
  private deleteModalTarget: RemoteFileItem | null = null;
  private listeners: Set<StorageStateChangeListener> = new Set();

  /**
   * Subscribes a listener to state mutations.
   * Returns an unsubscribe function.
   */
  public subscribe(listener: StorageStateChangeListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }

  // --- Directory & Path Navigation ---

  public getCurrentPath(): string {
    return this.currentPath;
  }

  public setCurrentPath(newPath: string): void {
    if (this.currentPath !== newPath) {
      this.currentPath = newPath;
      this.selectedItem = null; // Deselect inspector when changing folders
      this.hasMoreItems = false;
      this.nextOffset = 0;
      this.notify();
    }
  }

  public getIsLoadingDirectory(): boolean {
    return this.isLoadingDirectory;
  }

  public setLoadingDirectory(isLoading: boolean): void {
    if (this.isLoadingDirectory !== isLoading) {
      this.isLoadingDirectory = isLoading;
      this.notify();
    }
  }

  public getIsLoadingMore(): boolean {
    return this.isLoadingMore;
  }

  public setIsLoadingMore(isLoading: boolean): void {
    if (this.isLoadingMore !== isLoading) {
      this.isLoadingMore = isLoading;
      this.notify();
    }
  }

  public getHasMore(): boolean {
    return this.hasMoreItems;
  }

  public getNextOffset(): number {
    return this.nextOffset;
  }

  public getTotalCount(): number {
    return this.totalCount;
  }

  public getDirModified(): number {
    return this.dirModified;
  }

  public setPagination(hasMore: boolean, nextOffset: number, totalCount: number, dirModified: number = 0): void {
    this.hasMoreItems = hasMore;
    this.nextOffset = nextOffset;
    this.totalCount = totalCount;
    if (dirModified > 0) {
      this.dirModified = dirModified;
    }
    this.notify();
  }

  public clearItems(): void {
    this.items = [];
    this.selectedItem = null;
    this.hasMoreItems = false;
    this.nextOffset = 0;
    this.totalCount = 0;
    this.notify();
  }

  // --- Directory Cache System ---

  public getCachedDirectory(path: string): DirectoryCacheEntry | undefined {
    return this.directoryCache.get(path);
  }

  public setCachedDirectory(path: string, entry: DirectoryCacheEntry): void {
    this.directoryCache.set(path, entry);
  }

  public invalidateDirectoryCache(path: string): void {
    this.directoryCache.delete(path);
  }

  public loadFromCache(entry: DirectoryCacheEntry): void {
    this.items = [...entry.items];
    this.totalCount = entry.totalCount;
    this.hasMoreItems = entry.hasMore;
    this.nextOffset = entry.nextOffset;
    this.dirModified = entry.dirModified;
    this.isLoadingDirectory = false;
    this.notify();
  }

  // --- Image Preview Cache System ---

  public getCachedImagePreview(path: string): string | undefined {
    return this.imagePreviewCache.get(path);
  }

  public setCachedImagePreview(path: string, blobUrl: string): void {
    this.imagePreviewCache.set(path, blobUrl);
  }

  public invalidateImagePreview(path: string): void {
    const existingUrl = this.imagePreviewCache.get(path);
    if (existingUrl) {
      try {
        URL.revokeObjectURL(existingUrl);
      } catch (_) {}
      this.imagePreviewCache.delete(path);
    }
    this.imagePreviewBlobCache.delete(path);
  }

  /**
   * Appends incoming items from a progressive or paginated batch.
   * If isAppend is false (first page), replaces existing items.
   */
  public appendBatchItems(rawBatch: RawStorageItemPayload[], isAppend: boolean = false): void {
    const newItems: RemoteFileItem[] = rawBatch.map((raw, idx) => {
      const ext = raw.isDirectory ? 'DIR' : (raw.name.split('.').pop() || '').toUpperCase();
      const normCategory: FileCategory = raw.isDirectory
        ? 'all'
        : (raw.category as FileCategory) || 'other';

      return {
        id: `file-${raw.absolutePath.replace(/[^a-zA-Z0-9]/g, '_')}-${idx}`,
        name: raw.name,
        path: raw.absolutePath,
        isFolder: raw.isDirectory,
        size: raw.isDirectory ? '--' : formatBytes(raw.size),
        rawBytes: raw.size,
        modified: formatRelativeTime(raw.modified),
        rawModified: raw.modified,
        type: normCategory,
        extension: ext,
        mimeType: raw.mimeType,
        thumbnail: raw.thumbnail || null
      };
    });

    if (!isAppend) {
      this.items = newItems;
    } else {
      const existingPaths = new Set(this.items.map((i) => i.path));
      for (const item of newItems) {
        if (!existingPaths.has(item.path)) {
          this.items.push(item);
          existingPaths.add(item.path);
        }
      }
    }

    // Update Directory Cache with accumulated items
    this.directoryCache.set(this.currentPath, {
      path: this.currentPath,
      items: [...this.items],
      totalCount: this.totalCount,
      hasMore: this.hasMoreItems,
      nextOffset: this.nextOffset,
      dirModified: this.dirModified,
      cachedAt: Date.now()
    });

    this.notify();
  }

  public getItems(): RemoteFileItem[] {
    return this.items;
  }

  /**
   * Filters loaded items according to the active category and search filter query.
   */
  public getFilteredItems(): RemoteFileItem[] {
    let result = this.items;

    // Filter by Category
    if (this.activeCategory !== 'all') {
      result = result.filter((item) => !item.isFolder && item.type === this.activeCategory);
    }

    // Filter by Search Query
    if (this.searchQuery.trim().length > 0) {
      const q = this.searchQuery.toLowerCase().trim();
      result = result.filter(
        (item) =>
          item.name.toLowerCase().includes(q) ||
          item.extension.toLowerCase().includes(q)
      );
    }

    return result;
  }

  /**
   * Computes counts for category pill badges based on loaded items.
   */
  public getCategoryCounts(): Record<string, number> {
    const counts: Record<string, number> = {
      all: this.items.length,
      image: 0,
      video: 0,
      audio: 0,
      document: 0,
      archive: 0
    };

    for (const item of this.items) {
      if (!item.isFolder && counts[item.type] !== undefined) {
        counts[item.type]++;
      }
    }

    return counts;
  }

  // --- Filtering & Selection ---

  public getActiveCategory(): FileCategory {
    return this.activeCategory;
  }

  public setActiveCategory(cat: FileCategory): void {
    if (this.activeCategory !== cat) {
      this.activeCategory = cat;
      this.notify();
    }
  }

  public getSearchQuery(): string {
    return this.searchQuery;
  }

  public setSearchQuery(query: string): void {
    this.searchQuery = query;
    this.notify();
  }

  public getSelectedItem(): RemoteFileItem | null {
    return this.selectedItem;
  }

  public setSelectedItem(item: RemoteFileItem | null): void {
    this.selectedItem = item;
    this.notify();
  }

  // --- Delete Modal Target ---

  public getDeleteModalTarget(): RemoteFileItem | null {
    return this.deleteModalTarget;
  }

  public setDeleteModalTarget(item: RemoteFileItem | null): void {
    this.deleteModalTarget = item;
    this.notify();
  }

  /**
   * Removes a file from loaded items and cached directory entries after confirmed remote deletion.
   */
  public removeDeletedItem(deletedPath: string): void {
    this.items = this.items.filter((item) => item.path !== deletedPath);
    if (this.selectedItem?.path === deletedPath) {
      this.selectedItem = null;
    }
    this.deleteModalTarget = null;
    this.invalidateImagePreview(deletedPath);

    // Update Directory Cache entry
    const cached = this.directoryCache.get(this.currentPath);
    if (cached) {
      cached.items = cached.items.filter((i) => i.path !== deletedPath);
      cached.totalCount = Math.max(0, cached.totalCount - 1);
    }

    this.notify();
  }

  // --- Chunked Transfer Management ---

  public startTransferSession(session: FileTransferSession): void {
    this.activeTransfers.set(session.transferId, session);
    this.notify();
  }

  public appendTransferChunk(
    transferId: string,
    chunkIndex: number,
    base64Chunk: string
  ): FileTransferSession | null {
    const session = this.activeTransfers.get(transferId);
    if (!session) return null;

    session.receivedChunks.set(chunkIndex, base64Chunk);
    session.progressPercent = Math.round(
      (session.receivedChunks.size / session.totalChunks) * 100
    );
    this.notify();
    return session;
  }

  public getTransferSession(transferId: string): FileTransferSession | undefined {
    return this.activeTransfers.get(transferId);
  }

  public removeTransferSession(transferId: string): void {
    this.activeTransfers.delete(transferId);
    this.notify();
  }

  /**
   * Reassembles Base64 chunks into an in-memory Blob URL for downloading or viewing.
   */
  public completeTransferSession(transferId: string): { blobUrl: string; blob: Blob; session: FileTransferSession } | null {
    const session = this.activeTransfers.get(transferId);
    if (!session) return null;

    try {
      const sortedKeys = Array.from(session.receivedChunks.keys()).sort((a, b) => a - b);
      const byteArrays: Uint8Array[] = [];

      for (const k of sortedKeys) {
        const b64 = session.receivedChunks.get(k) || '';
        const binaryString = atob(b64);
        const len = binaryString.length;
        const bytes = new Uint8Array(len);
        for (let i = 0; i < len; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        byteArrays.push(bytes);
      }

      const blob = new Blob(byteArrays as BlobPart[], { type: session.mimeType || 'application/octet-stream' });
      const blobUrl = URL.createObjectURL(blob);

      // Cache image previews automatically
      if (session.purpose === 'preview') {
        this.setCachedImagePreview(session.path, blobUrl);
        this.imagePreviewBlobCache.set(session.path, blob);
      }

      return { blobUrl, blob, session };
    } catch (e) {
      console.error('[StorageState] Failed to reassemble file transfer chunks:', e);
      return null;
    }
  }

  // --- In-App Image Preview Modal ---

  public getCachedImagePreviewBlob(path: string): Blob | undefined {
    return this.imagePreviewBlobCache.get(path);
  }

  public getImagePreview(): { url: string | null; name: string | null; blob: Blob | null } {
    return { url: this.imagePreviewUrl, name: this.imagePreviewName, blob: this.imagePreviewBlob };
  }

  public setImagePreview(url: string | null, name: string | null, blob: Blob | null = null): void {
    this.imagePreviewUrl = url;
    this.imagePreviewName = name;
    this.imagePreviewBlob = blob;
    this.notify();
  }

  /**
   * Resets all internal state when connection is closed.
   */
  public reset(): void {
    this.currentPath = '/storage/emulated/0';
    this.items = [];
    this.activeCategory = 'all';
    this.searchQuery = '';
    this.selectedItem = null;
    this.isLoadingDirectory = false;
    this.isLoadingMore = false;
    this.hasMoreItems = false;
    this.nextOffset = 0;
    this.totalCount = 0;
    this.dirModified = 0;
    this.directoryCache.clear();
    this.imagePreviewCache.forEach((url) => {
      try {
        URL.revokeObjectURL(url);
      } catch (_) {}
    });
    this.imagePreviewCache.clear();
    this.imagePreviewBlobCache.clear();
    this.activeTransfers.clear();
    this.imagePreviewUrl = null;
    this.imagePreviewName = null;
    this.imagePreviewBlob = null;
    this.deleteModalTarget = null;
    this.notify();
  }
}

export const storageState = new StorageStateManager();
