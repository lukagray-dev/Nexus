/**
 * @file storage-protocol.test.js
 * @description Unit tests for Nexus Parent Remote Storage Monitoring DataChannel protocol,
 * state machine, progressive directory batching, directory caching, image preview caching,
 * search/category filtering, and chunked transfer reassembly.
 *
 * Hey friend! These unit tests verify:
 * 1. Monorepo contract constants for storage commands, responses, and transfer envelopes.
 * 2. Byte and relative time formatting helpers.
 * 3. Progressive directory batch loading and deduplication in StorageStateManager.
 * 4. Directory caching with instant load and smart modification checking.
 * 5. Image preview caching for instant re-opening.
 * 6. Progressive pagination (hasMore, nextOffset).
 * 7. Category filtering and instant search filtering.
 * 8. Chunked file transfer accumulation and progress percentage calculation.
 * 9. Remote file deletion confirmation state and item removal.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  CMD_STORAGE_CHECK_DIR,
  CMD_STORAGE_LIST,
  CMD_STORAGE_GET_FILE,
  CMD_STORAGE_CANCEL_TRANSFER,
  CMD_STORAGE_DELETE_FILE,
  TYPE_STORAGE_DIR_UNCHANGED,
  TYPE_STORAGE_DIR_CHANGED,
  TYPE_STORAGE_LIST_BATCH,
  TYPE_STORAGE_LIST_ERROR,
  TYPE_STORAGE_FILE_START,
  TYPE_STORAGE_FILE_CHUNK,
  TYPE_STORAGE_FILE_COMPLETE,
  TYPE_STORAGE_FILE_ERROR,
  TYPE_STORAGE_DELETE_SUCCESS,
  TYPE_STORAGE_DELETE_ERROR,
  formatBytes,
  formatRelativeTime
} from '../src/js/main-content/storage/types.js';
import { storageState } from '../src/js/main-content/storage/state.js';

describe('Storage Protocol & State Machine Tests', () => {

  it('Command and confirmation constants conform to monorepo contract', () => {
    assert.strictEqual(CMD_STORAGE_CHECK_DIR, 'STORAGE_CHECK_DIR');
    assert.strictEqual(CMD_STORAGE_LIST, 'STORAGE_LIST');
    assert.strictEqual(CMD_STORAGE_GET_FILE, 'STORAGE_GET_FILE');
    assert.strictEqual(CMD_STORAGE_CANCEL_TRANSFER, 'STORAGE_CANCEL_TRANSFER');
    assert.strictEqual(CMD_STORAGE_DELETE_FILE, 'STORAGE_DELETE_FILE');

    assert.strictEqual(TYPE_STORAGE_DIR_UNCHANGED, 'STORAGE_DIR_UNCHANGED');
    assert.strictEqual(TYPE_STORAGE_DIR_CHANGED, 'STORAGE_DIR_CHANGED');
    assert.strictEqual(TYPE_STORAGE_LIST_BATCH, 'STORAGE_LIST_BATCH');
    assert.strictEqual(TYPE_STORAGE_LIST_ERROR, 'STORAGE_LIST_ERROR');
    assert.strictEqual(TYPE_STORAGE_FILE_START, 'STORAGE_FILE_START');
    assert.strictEqual(TYPE_STORAGE_FILE_CHUNK, 'STORAGE_FILE_CHUNK');
    assert.strictEqual(TYPE_STORAGE_FILE_COMPLETE, 'STORAGE_FILE_COMPLETE');
    assert.strictEqual(TYPE_STORAGE_FILE_ERROR, 'STORAGE_FILE_ERROR');
    assert.strictEqual(TYPE_STORAGE_DELETE_SUCCESS, 'STORAGE_DELETE_SUCCESS');
    assert.strictEqual(TYPE_STORAGE_DELETE_ERROR, 'STORAGE_DELETE_ERROR');
  });

  it('formatBytes formats byte values accurately', () => {
    assert.strictEqual(formatBytes(0), '0 B');
    assert.strictEqual(formatBytes(512), '512 B');
    assert.strictEqual(formatBytes(2048), '2.0 KB');
    assert.strictEqual(formatBytes(4194304), '4.0 MB');
    assert.strictEqual(formatBytes(10737418240), '10.0 GB');
  });

  it('formatRelativeTime calculates human-readable relative timestamps', () => {
    const now = Date.now();
    assert.strictEqual(formatRelativeTime(now - 10000), 'Just now');
    assert.strictEqual(formatRelativeTime(now - 600000), '10 mins ago');
    assert.strictEqual(formatRelativeTime(now - 7200000), '2 hours ago');
    assert.strictEqual(formatRelativeTime(now - 90000000), 'Yesterday');
    assert.strictEqual(formatRelativeTime(0), 'Unknown');
  });

  it('StorageStateManager navigates directory paths cleanly', () => {
    storageState.reset();
    assert.strictEqual(storageState.getCurrentPath(), '/storage/emulated/0');

    storageState.setCurrentPath('/storage/emulated/0/DCIM/Camera');
    assert.strictEqual(storageState.getCurrentPath(), '/storage/emulated/0/DCIM/Camera');
  });

  it('Directory Caching saves folder contents and loads instantaneously', () => {
    storageState.reset();

    const mockItems = [
      {
        name: 'IMG_01.jpg',
        absolutePath: '/storage/emulated/0/DCIM/Camera/IMG_01.jpg',
        size: 3000000,
        modified: 1731900000000,
        isDirectory: false,
        mimeType: 'image/jpeg',
        category: 'image',
        thumbnail: 'data:image/jpeg;base64,t1'
      }
    ];

    storageState.setCurrentPath('/storage/emulated/0/DCIM/Camera');
    storageState.setPagination(false, 1, 1, 1731900000000);
    storageState.appendBatchItems(mockItems, false);

    // Verify directory cache has entry
    const cached = storageState.getCachedDirectory('/storage/emulated/0/DCIM/Camera');
    assert.ok(cached);
    assert.strictEqual(cached.items.length, 1);
    assert.strictEqual(cached.items[0].name, 'IMG_01.jpg');
    assert.strictEqual(cached.dirModified, 1731900000000);

    // Navigate away to Downloads
    storageState.setCurrentPath('/storage/emulated/0/Download');
    storageState.clearItems();
    assert.strictEqual(storageState.getItems().length, 0);

    // Navigate back to Camera using cache hit
    const cachedBack = storageState.getCachedDirectory('/storage/emulated/0/DCIM/Camera');
    assert.ok(cachedBack);
    storageState.loadFromCache(cachedBack);
    assert.strictEqual(storageState.getItems().length, 1);
    assert.strictEqual(storageState.getItems()[0].name, 'IMG_01.jpg');
  });

  it('Image Preview Cache stores and retrieves reassembled preview URLs', () => {
    storageState.reset();

    const photoPath = '/storage/emulated/0/DCIM/Camera/sunset.jpg';
    assert.strictEqual(storageState.getCachedImagePreview(photoPath), undefined);

    storageState.setCachedImagePreview(photoPath, 'blob:nexus-preview-url');
    assert.strictEqual(storageState.getCachedImagePreview(photoPath), 'blob:nexus-preview-url');

    storageState.invalidateImagePreview(photoPath);
    assert.strictEqual(storageState.getCachedImagePreview(photoPath), undefined);
  });

  it('Progressive Pagination tracks hasMore and nextOffset accurately', () => {
    storageState.reset();

    assert.strictEqual(storageState.getHasMore(), false);
    assert.strictEqual(storageState.getNextOffset(), 0);

    // Simulate page 1 response (offset 0, limit 40, total 100)
    storageState.setPagination(true, 40, 100, 12345);
    assert.strictEqual(storageState.getHasMore(), true);
    assert.strictEqual(storageState.getNextOffset(), 40);
    assert.strictEqual(storageState.getTotalCount(), 100);

    // Simulate page 2 response (offset 40, limit 40, total 100)
    storageState.setPagination(true, 80, 100, 12345);
    assert.strictEqual(storageState.getHasMore(), true);
    assert.strictEqual(storageState.getNextOffset(), 80);

    // Simulate final page response (offset 80, limit 40, total 100 -> hasMore=false)
    storageState.setPagination(false, 100, 100, 12345);
    assert.strictEqual(storageState.getHasMore(), false);
  });

  it('Progressive batch items are parsed, categorized, and deduplicated', () => {
    storageState.reset();

    const rawBatch1 = [
      {
        name: 'DCIM',
        absolutePath: '/storage/emulated/0/DCIM',
        size: 0,
        modified: Date.now() - 3600000,
        isDirectory: true,
        mimeType: 'inode/directory',
        category: 'folder',
        thumbnail: null
      },
      {
        name: 'Vacation.jpg',
        absolutePath: '/storage/emulated/0/Pictures/Vacation.jpg',
        size: 4200000,
        modified: Date.now() - 7200000,
        isDirectory: false,
        mimeType: 'image/jpeg',
        category: 'image',
        thumbnail: 'data:image/jpeg;base64,abc123'
      }
    ];

    storageState.appendBatchItems(rawBatch1, false);
    assert.strictEqual(storageState.getItems().length, 2);

    const folderItem = storageState.getItems().find((i) => i.name === 'DCIM');
    assert.ok(folderItem);
    assert.strictEqual(folderItem.isFolder, true);
    assert.strictEqual(folderItem.extension, 'DIR');

    const imageItem = storageState.getItems().find((i) => i.name === 'Vacation.jpg');
    assert.ok(imageItem);
    assert.strictEqual(imageItem.isFolder, false);
    assert.strictEqual(imageItem.type, 'image');
    assert.strictEqual(imageItem.thumbnail, 'data:image/jpeg;base64,abc123');

    // Duplicate append check
    storageState.appendBatchItems(rawBatch1, true);
    assert.strictEqual(storageState.getItems().length, 2);
  });

  it('Category filters and search query isolate relevant items', () => {
    storageState.reset();

    const mockItems = [
      {
        name: 'Camera',
        absolutePath: '/storage/emulated/0/DCIM/Camera',
        size: 0,
        modified: Date.now(),
        isDirectory: true,
        mimeType: 'inode/directory',
        category: 'folder',
        thumbnail: null
      },
      {
        name: 'sunset.png',
        absolutePath: '/storage/emulated/0/Pictures/sunset.png',
        size: 2048000,
        modified: Date.now(),
        isDirectory: false,
        mimeType: 'image/png',
        category: 'image',
        thumbnail: null
      },
      {
        name: 'clip.mp4',
        absolutePath: '/storage/emulated/0/Movies/clip.mp4',
        size: 15000000,
        modified: Date.now(),
        isDirectory: false,
        mimeType: 'video/mp4',
        category: 'video',
        thumbnail: null
      },
      {
        name: 'notes.pdf',
        absolutePath: '/storage/emulated/0/Documents/notes.pdf',
        size: 512000,
        modified: Date.now(),
        isDirectory: false,
        mimeType: 'application/pdf',
        category: 'document',
        thumbnail: null
      }
    ];

    storageState.appendBatchItems(mockItems, false);
    assert.strictEqual(storageState.getItems().length, 4);

    // Filter by Images
    storageState.setActiveCategory('image');
    let filtered = storageState.getFilteredItems();
    assert.strictEqual(filtered.length, 1);
    assert.strictEqual(filtered[0].name, 'sunset.png');

    // Filter by Videos
    storageState.setActiveCategory('video');
    filtered = storageState.getFilteredItems();
    assert.strictEqual(filtered.length, 1);
    assert.strictEqual(filtered[0].name, 'clip.mp4');

    // Reset category, search for "notes"
    storageState.setActiveCategory('all');
    storageState.setSearchQuery('notes');
    filtered = storageState.getFilteredItems();
    assert.strictEqual(filtered.length, 1);
    assert.strictEqual(filtered[0].name, 'notes.pdf');

    // Clear search
    storageState.setSearchQuery('');
    filtered = storageState.getFilteredItems();
    assert.strictEqual(filtered.length, 4);
  });

  it('Calculates category counts accurately for pill badges', () => {
    storageState.reset();

    storageState.appendBatchItems([
      { name: 'f1', absolutePath: '/p/f1', size: 0, modified: 0, isDirectory: true, mimeType: '', category: 'folder' },
      { name: 'i1.jpg', absolutePath: '/p/i1.jpg', size: 10, modified: 0, isDirectory: false, mimeType: 'image/jpeg', category: 'image' },
      { name: 'i2.jpg', absolutePath: '/p/i2.jpg', size: 10, modified: 0, isDirectory: false, mimeType: 'image/jpeg', category: 'image' },
      { name: 'v1.mp4', absolutePath: '/p/v1.mp4', size: 10, modified: 0, isDirectory: false, mimeType: 'video/mp4', category: 'video' },
      { name: 'a1.m4a', absolutePath: '/p/a1.m4a', size: 10, modified: 0, isDirectory: false, mimeType: 'audio/m4a', category: 'audio' },
      { name: 'd1.pdf', absolutePath: '/p/d1.pdf', size: 10, modified: 0, isDirectory: false, mimeType: 'application/pdf', category: 'document' },
      { name: 'z1.zip', absolutePath: '/p/z1.zip', size: 10, modified: 0, isDirectory: false, mimeType: 'application/zip', category: 'archive' }
    ], false);

    const counts = storageState.getCategoryCounts();
    assert.strictEqual(counts.all, 7);
    assert.strictEqual(counts.image, 2);
    assert.strictEqual(counts.video, 1);
    assert.strictEqual(counts.audio, 1);
    assert.strictEqual(counts.document, 1);
    assert.strictEqual(counts.archive, 1);
  });

  it('Accumulates chunked file transfer slices and calculates progress percentage', () => {
    storageState.reset();

    storageState.startTransferSession({
      transferId: 'tx-100',
      path: '/storage/emulated/0/DCIM/sample.jpg',
      fileName: 'sample.jpg',
      size: 196608,
      totalChunks: 3,
      receivedChunks: new Map(),
      purpose: 'download',
      mimeType: 'image/jpeg',
      progressPercent: 0
    });

    const s1 = storageState.appendTransferChunk('tx-100', 0, 'CHUNK_0');
    assert.strictEqual(s1?.progressPercent, 33);

    const s2 = storageState.appendTransferChunk('tx-100', 1, 'CHUNK_1');
    assert.strictEqual(s2?.progressPercent, 67);

    const s3 = storageState.appendTransferChunk('tx-100', 2, 'CHUNK_2');
    assert.strictEqual(s3?.progressPercent, 100);

    const session = storageState.getTransferSession('tx-100');
    assert.ok(session);
    assert.strictEqual(session.receivedChunks.size, 3);
  });

  it('Delete flow removes confirmed item and resets modal target', () => {
    storageState.reset();

    storageState.appendBatchItems([
      { name: 'file-to-delete.pdf', absolutePath: '/p/del.pdf', size: 100, modified: 0, isDirectory: false, mimeType: 'application/pdf', category: 'document' }
    ], false);
    assert.strictEqual(storageState.getItems().length, 1);

    const item = storageState.getItems()[0];
    storageState.setDeleteModalTarget(item);
    assert.strictEqual(storageState.getDeleteModalTarget()?.name, 'file-to-delete.pdf');

    // Simulate child confirming deletion
    storageState.removeDeletedItem('/p/del.pdf');
    assert.strictEqual(storageState.getItems().length, 0);
    assert.strictEqual(storageState.getDeleteModalTarget(), null);
  });
});
