/**
 * @file storage.ts
 * @description Remote File Explorer & Storage Monitor View component for Nexus Parent.
 *
 * Hey friend! This controller powers the full-viewport remote storage monitoring interface:
 * 1. Live Storage Gauge: Real-time capacity bar dynamically bound to Android child hardware telemetry.
 * 2. Directory Caching & Smart Invalidation: Previously visited folders render instantaneously from memory;
 *    lightweight STORAGE_CHECK_DIR requests verify if contents changed and apply delta updates seamlessly.
 * 3. Progressive Pagination: On-demand 40-item pages with interactive "Load More" button and scroll support,
 *    preventing lag when exploring massive directories like DCIM.
 * 4. Image Preview Caching: Once an image is loaded, its Blob URL is cached in memory so reopening it is instantaneous.
 * 5. High-Throughput 64KB Streaming: Optimized DataChannel streaming without artificial delays.
 * 6. Native Save-As Location Prompt: Prompts user with native OS file picker before downloading files.
 * 7. Remote File Deletion: Tactical confirmation modal safeguard before permanently deleting child files.
 */

import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import { deviceState } from '../../shared/device-state.js';
import {
  type RemoteFileItem,
  type FileCategory,
  type StorageListBatchPacket,
  CMD_STORAGE_CHECK_DIR,
  CMD_STORAGE_LIST,
  CMD_STORAGE_GET_FILE,
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
  TYPE_STORAGE_DELETE_ERROR
} from './types.js';
import { storageState } from './state.js';

let isDataChannelListenerRegistered = false;

// Pending file save handles keyed by transferId or path
const pendingSaveHandles: Map<string, any> = new Map();

/**
 * Creates and returns the full-viewport Storage Monitor DOM element.
 */
export function renderStorageView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'files-container';
  container.id = 'storage-view-root';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Monitor Storage</span>
      </div>
      <div class="view-topbar-right">
        <button id="btn-files-refresh" class="topbar-btn" title="Refresh Directory (Bypass Cache)">
          <img src="assets/icons/main-content/refresh-cw.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
          <span>Refresh</span>
        </button>
        <button id="btn-files-up" class="topbar-btn" title="Parent Directory">
          <img src="assets/icons/main-content/arrow-left.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
          <span>Parent Dir</span>
        </button>
      </div>
    </div>

    <!-- Secondary Navigation & Breadcrumbs Bar -->
    <div class="files-subbar">
      <div class="files-breadcrumbs" id="files-breadcrumbs"></div>
      <div class="files-search-wrap">
        <input 
          type="text" 
          id="files-search-input" 
          class="files-search-input" 
          placeholder="Filter files by name or type..." 
          spellcheck="false" 
        />
      </div>
    </div>

    <!-- Category Filter Bar -->
    <div class="files-categories-bar">
      <button class="category-pill active" data-cat="all">
        <span>All Items</span>
        <span class="category-pill-badge" id="cat-count-all">0</span>
      </button>
      <button class="category-pill" data-cat="image">
        <span>Images & Photos</span>
        <span class="category-pill-badge" id="cat-count-image">0</span>
      </button>
      <button class="category-pill" data-cat="video">
        <span>Videos</span>
        <span class="category-pill-badge" id="cat-count-video">0</span>
      </button>
      <button class="category-pill" data-cat="audio">
        <span>Audio & Voice</span>
        <span class="category-pill-badge" id="cat-count-audio">0</span>
      </button>
      <button class="category-pill" data-cat="document">
        <span>Documents</span>
        <span class="category-pill-badge" id="cat-count-document">0</span>
      </button>
      <button class="category-pill" data-cat="archive">
        <span>Backups & Archives</span>
        <span class="category-pill-badge" id="cat-count-archive">0</span>
      </button>
    </div>

    <!-- Storage Capacity Strip -->
    <div class="storage-summary-strip">
      <div class="storage-progress-container">
        <span>STORAGE USAGE</span>
        <div class="storage-progress-bar">
          <div class="storage-progress-fill" id="storage-progress-fill" style="width: 0%;"></div>
        </div>
        <span id="storage-usage-text">CALCULATING...</span>
      </div>
      <div id="files-status-count">Showing 0 items</div>
    </div>

    <!-- Main Explorer Layout: Table on Left + Inspector Drawer on Right -->
    <div class="files-main-layout">
      <!-- File Explorer Table Area -->
      <div class="files-explorer-area" id="files-explorer-area">
        <div id="files-loading-overlay" class="files-loading-overlay hidden">
          <img src="assets/icons/animations/loading.svg" alt="Loading" class="files-spinner" />
          <span>Fetching directory contents from child...</span>
        </div>
        <table class="files-table">
          <thead>
            <tr>
              <th style="width: 44%;">Name</th>
              <th style="width: 14%;">Type</th>
              <th style="width: 14%;">Size</th>
              <th style="width: 16%;">Last Modified</th>
              <th style="width: 12%; text-align: right;">Actions</th>
            </tr>
          </thead>
          <tbody id="files-table-body"></tbody>
        </table>

        <!-- Progressive Load More Bar -->
        <div id="files-load-more-wrap" class="files-load-more-wrap hidden">
          <button id="btn-files-load-more" class="btn-load-more">
            <img src="assets/icons/main-content/refresh-cw.svg" alt="" class="load-more-icon" id="load-more-icon" />
            <span id="load-more-text">Load More Files</span>
          </button>
        </div>
      </div>

      <!-- File Metadata Inspector Drawer -->
      <aside class="files-inspector-drawer hidden" id="files-inspector-drawer">
        <div class="inspector-header">
          <span class="inspector-title">File Details</span>
          <button id="btn-inspector-close" class="inspector-close-btn" title="Close Details">
            <img src="assets/icons/main-content/x.svg" alt="Close" style="filter:invert(1);" />
          </button>
        </div>
        <div class="inspector-body" id="inspector-body"></div>
      </aside>
    </div>

    <!-- Feedback Toast -->
    <div id="files-toast" class="files-toast">
      <img src="assets/icons/main-content/info.svg" alt="" style="width:14px;height:14px;filter:invert(1);" id="files-toast-icon" />
      <span id="files-toast-text"></span>
    </div>

    <!-- Delete Confirmation Modal Dialog -->
    <div id="delete-modal-overlay" class="storage-modal-overlay hidden">
      <div class="storage-modal-dialog">
        <div class="storage-modal-header">
          <img src="assets/icons/main-content/storage/trash.svg" alt="Delete" class="storage-modal-icon" />
          <span class="storage-modal-title">Delete Remote File</span>
        </div>
        <div class="storage-modal-body">
          <p>Are you sure you want to permanently delete this file from the child device?</p>
          <div class="delete-target-preview" id="delete-target-preview"></div>
          <p class="delete-warning-text">This action cannot be undone.</p>
        </div>
        <div class="storage-modal-actions">
          <button id="btn-delete-cancel" class="storage-modal-btn btn-secondary">Cancel</button>
          <button id="btn-delete-confirm" class="storage-modal-btn btn-danger">Confirm Delete</button>
        </div>
      </div>
    </div>

    <!-- In-App High-Res Image Preview Modal -->
    <div id="image-modal-overlay" class="storage-modal-overlay hidden">
      <div class="storage-modal-dialog image-modal-dialog">
        <div class="storage-modal-header">
          <span class="storage-modal-title" id="image-modal-title">Photo Preview</span>
          <button id="btn-image-modal-close" class="inspector-close-btn" title="Close">
            <img src="assets/icons/main-content/x.svg" alt="Close" style="filter:invert(1);" />
          </button>
        </div>
        <div class="image-modal-body">
          <img id="image-modal-img" src="" alt="Full Preview" />
        </div>
        <div class="storage-modal-actions">
          <button id="btn-image-modal-download" class="storage-modal-btn btn-primary">
            <img src="assets/icons/main-content/download.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
            <span>Save Image As...</span>
          </button>
        </div>
      </div>
    </div>
  `;

  // Register WebRTC DataChannel message handler once
  registerDataChannelListener();

  // Attach interactive behavior after mounting
  setTimeout(() => {
    bindStorageEvents(container);
  }, 0);

  return container;
}

/**
 * Registers global WebRTC DataChannel packet listener for storage messages.
 */
function registerDataChannelListener(): void {
  if (isDataChannelListenerRegistered) return;
  isDataChannelListenerRegistered = true;

  webrtcManager.onMessage((rawMessage: string) => {
    if (typeof rawMessage !== 'string' || !rawMessage.startsWith('{')) return;

    try {
      const packet = JSON.parse(rawMessage);
      if (!packet || typeof packet.type !== 'string') return;

      switch (packet.type) {
        case TYPE_STORAGE_DIR_UNCHANGED:
          // Child verified our cached directory is 100% current
          console.log('[Storage] Directory cache validated as current:', packet.path);
          break;

        case TYPE_STORAGE_DIR_CHANGED:
          // Child notified directory has modified timestamp or count change
          console.log('[Storage] Directory changed on child, awaiting fresh batch:', packet.path);
          break;

        case TYPE_STORAGE_LIST_BATCH:
          handleListBatch(packet as StorageListBatchPacket);
          break;

        case TYPE_STORAGE_LIST_ERROR:
          storageState.setLoadingDirectory(false);
          storageState.setIsLoadingMore(false);
          showStorageToast(`Storage error: ${packet.reason || 'Failed to list directory'}`);
          break;

        case TYPE_STORAGE_FILE_START:
          storageState.startTransferSession({
            transferId: packet.transferId,
            path: packet.path,
            fileName: packet.fileName,
            size: packet.size,
            totalChunks: packet.totalChunks,
            receivedChunks: new Map(),
            purpose: packet.purpose || 'download',
            mimeType: packet.mimeType || 'application/octet-stream',
            progressPercent: 0,
            fileHandle: pendingSaveHandles.get(packet.path)
          });
          showStorageToast(`Streaming ${packet.fileName}... 0%`);
          break;

        case TYPE_STORAGE_FILE_CHUNK:
          storageState.appendTransferChunk(packet.transferId, packet.chunkIndex, packet.data);
          const session = storageState.getTransferSession(packet.transferId);
          if (session && session.totalChunks > 1) {
            showStorageToast(`Transferring ${session.fileName}... ${session.progressPercent}%`);
          }
          break;

        case TYPE_STORAGE_FILE_COMPLETE:
          handleFileComplete(packet.transferId);
          break;

        case TYPE_STORAGE_FILE_ERROR:
          storageState.removeTransferSession(packet.transferId);
          showStorageToast(`Transfer failed: ${packet.reason || 'Unknown error'}`);
          break;

        case TYPE_STORAGE_DELETE_SUCCESS:
          storageState.removeDeletedItem(packet.path);
          showStorageToast('File deleted successfully from child device');
          break;

        case TYPE_STORAGE_DELETE_ERROR:
          storageState.setDeleteModalTarget(null);
          showStorageToast(`Deletion failed: ${packet.reason || 'Could not delete file'}`);
          break;
      }
    } catch (e) {
      // Not a storage JSON packet; ignore
    }
  });
}

function handleListBatch(packet: StorageListBatchPacket): void {
  const isAppend = (packet.offset !== undefined && packet.offset > 0);
  if (packet.items && packet.items.length > 0) {
    storageState.appendBatchItems(packet.items, isAppend);
  }

  const hasMore = packet.hasMore ?? false;
  const nextOffset = packet.nextOffset ?? (packet.offset || 0) + (packet.items?.length || 0);
  const dirModified = packet.dirModified || 0;

  storageState.setPagination(hasMore, nextOffset, packet.totalCount, dirModified);
  storageState.setLoadingDirectory(false);
  storageState.setIsLoadingMore(false);
}

async function handleFileComplete(transferId: string): Promise<void> {
  const result = storageState.completeTransferSession(transferId);
  if (!result) {
    showStorageToast('Failed to reassemble file chunks');
    return;
  }

  const { blobUrl, blob, session } = result;

  if (session.purpose === 'preview') {
    // Open in-app image preview modal
    storageState.setImagePreview(blobUrl, session.fileName);
    showStorageToast(`Image loaded: ${session.fileName}`);
  } else {
    // Check if user selected a native file handle before downloading
    if (session.fileHandle) {
      try {
        const writable = await session.fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();
        pendingSaveHandles.delete(session.path);
        showStorageToast(`Saved successfully: ${session.fileName}`);
        return;
      } catch (err: any) {
        console.warn('[Storage] Error writing to native file handle, falling back to download link:', err);
      }
    }

    // Fallback: Trigger browser / OS file download link
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = session.fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showStorageToast(`Download complete: ${session.fileName}`);

    // Revoke Blob URL after short delay
    setTimeout(() => {
      URL.revokeObjectURL(blobUrl);
      storageState.removeTransferSession(transferId);
    }, 15000);
  }
}

/**
 * Binds DOM events, state subscriptions, and navigation handlers.
 */
function bindStorageEvents(container: HTMLElement): void {
  const tableBody = container.querySelector<HTMLTableSectionElement>('#files-table-body');
  const searchInput = container.querySelector<HTMLInputElement>('#files-search-input');
  const categoryPills = container.querySelectorAll<HTMLButtonElement>('.category-pill');
  const breadcrumbsEl = container.querySelector<HTMLElement>('#files-breadcrumbs');
  const btnUp = container.querySelector<HTMLButtonElement>('#btn-files-up');
  const btnRefresh = container.querySelector<HTMLButtonElement>('#btn-files-refresh');
  const statusCount = container.querySelector<HTMLElement>('#files-status-count');
  const loadingOverlay = container.querySelector<HTMLElement>('#files-loading-overlay');
  const loadMoreWrap = container.querySelector<HTMLElement>('#files-load-more-wrap');
  const btnLoadMore = container.querySelector<HTMLButtonElement>('#btn-files-load-more');
  const loadMoreText = container.querySelector<HTMLElement>('#load-more-text');
  const loadMoreIcon = container.querySelector<HTMLElement>('#load-more-icon');
  const inspectorDrawer = container.querySelector<HTMLElement>('#files-inspector-drawer');
  const inspectorBody = container.querySelector<HTMLElement>('#inspector-body');
  const btnInspectorClose = container.querySelector<HTMLButtonElement>('#btn-inspector-close');
  const deleteModal = container.querySelector<HTMLElement>('#delete-modal-overlay');
  const deleteTargetPreview = container.querySelector<HTMLElement>('#delete-target-preview');
  const btnDeleteCancel = container.querySelector<HTMLButtonElement>('#btn-delete-cancel');
  const btnDeleteConfirm = container.querySelector<HTMLButtonElement>('#btn-delete-confirm');
  const imageModal = container.querySelector<HTMLElement>('#image-modal-overlay');
  const imageModalImg = container.querySelector<HTMLImageElement>('#image-modal-img');
  const imageModalTitle = container.querySelector<HTMLElement>('#image-modal-title');
  const btnImageModalClose = container.querySelector<HTMLButtonElement>('#btn-image-modal-close');
  const btnImageModalDownload = container.querySelector<HTMLButtonElement>('#btn-image-modal-download');
  const storageFill = container.querySelector<HTMLElement>('#storage-progress-fill');
  const storageText = container.querySelector<HTMLElement>('#storage-usage-text');

  if (!tableBody) return;

  /**
   * Updates the live storage capacity bar using device telemetry.
   */
  const updateStorageGauge = () => {
    const info = deviceState.getConnectedDevice();
    if (!info || info.totalStorageBytes === undefined || info.freeStorageBytes === undefined || info.totalStorageBytes <= 0) {
      if (storageText) storageText.textContent = 'STORAGE TELEMETRY UNAVAILABLE';
      if (storageFill) storageFill.style.width = '0%';
      return;
    }

    const total = info.totalStorageBytes;
    const free = info.freeStorageBytes;
    const used = Math.max(0, total - free);
    const usedPercent = Math.min(100, Math.max(0, Math.round((used / total) * 100)));

    const usedGB = (used / (1024 * 1024 * 1024)).toFixed(1);
    const totalGB = (total / (1024 * 1024 * 1024)).toFixed(1);

    if (storageText) {
      storageText.textContent = `${usedGB} GB / ${totalGB} GB USED (${usedPercent}%)`;
    }
    if (storageFill) {
      storageFill.style.width = `${usedPercent}%`;
    }
  };

  /**
   * Renders the breadcrumb trail based on current directory path.
   */
  const renderBreadcrumbs = () => {
    if (!breadcrumbsEl) return;
    const currentPath = storageState.getCurrentPath();
    const parts = currentPath.split('/').filter(Boolean);
    let html = '';
    let accumulated = '';

    parts.forEach((part, idx) => {
      accumulated += '/' + part;
      const isLast = idx === parts.length - 1;
      const label = part === '0' ? '0 (Internal)' : part;
      html += `<span class="breadcrumb-item ${isLast ? 'active' : ''}" data-path="${accumulated}">${label}</span>`;
      if (!isLast) {
        html += `<span class="breadcrumb-separator">/</span>`;
      }
    });

    breadcrumbsEl.innerHTML = html;

    breadcrumbsEl.querySelectorAll('.breadcrumb-item').forEach((item) => {
      item.addEventListener('click', () => {
        const path = item.getAttribute('data-path');
        if (path) navigateTo(path);
      });
    });
  };

  /**
   * Updates category pill counts.
   */
  const updateCategoryCounts = () => {
    const counts = storageState.getCategoryCounts();
    for (const [cat, cnt] of Object.entries(counts)) {
      const badge = container.querySelector(`#cat-count-${cat}`);
      if (badge) badge.textContent = cnt.toString();
    }
  };

  /**
   * Navigates into a directory with instant cache lookup and smart modification validation.
   */
  const navigateTo = (path: string, forceFresh = false) => {
    storageState.setCurrentPath(path);

    if (!forceFresh) {
      const cached = storageState.getCachedDirectory(path);
      if (cached) {
        // 1. Instant Cache Hit: Load immediately from memory without waiting for network!
        storageState.loadFromCache(cached);

        // 2. Background Validation: Ask child if folder has changed
        const checkCmd = JSON.stringify({
          type: CMD_STORAGE_CHECK_DIR,
          path: path,
          cachedModified: cached.dirModified,
          cachedCount: cached.totalCount
        });
        webrtcManager.sendCommand(checkCmd);
        return;
      }
    }

    // Cache Miss or Force Refresh: Clear items and request first page
    storageState.clearItems();
    storageState.setLoadingDirectory(true);

    const listCmd = JSON.stringify({
      type: CMD_STORAGE_LIST,
      path: path,
      offset: 0,
      limit: 40
    });
    webrtcManager.sendCommand(listCmd);
  };

  /**
   * Re-renders the file table rows.
   */
  const renderTableRows = () => {
    const filtered = storageState.getFilteredItems();
    const selectedItem = storageState.getSelectedItem();
    const totalCount = storageState.getTotalCount();

    if (statusCount) {
      const totalStr = totalCount > filtered.length ? ` of ${totalCount}` : '';
      statusCount.textContent = `Showing ${filtered.length}${totalStr} item${filtered.length === 1 ? '' : 's'}`;
    }

    tableBody.innerHTML = '';

    if (filtered.length === 0) {
      const isLoading = storageState.getIsLoadingDirectory();
      if (!isLoading) {
        tableBody.innerHTML = `
          <tr>
            <td colspan="5" style="text-align:center; padding: 48px; color: var(--text-muted);">
              No files found in this folder.
            </td>
          </tr>
        `;
      }
      return;
    }

    filtered.forEach((item) => {
      const row = document.createElement('tr');
      if (selectedItem?.path === item.path) {
        row.classList.add('selected');
      }

      let iconHtml: string;
      if (item.thumbnail) {
        iconHtml = `<img src="${item.thumbnail}" alt="" class="file-table-thumb" />`;
      } else if (item.isFolder) {
        iconHtml = `<img src="assets/icons/main-content/folder.svg" alt="" style="filter:invert(1);" />`;
      } else if (item.type === 'image') {
        iconHtml = `<img src="assets/icons/main-content/image.svg" alt="" style="filter:invert(1);" />`;
      } else if (item.type === 'video') {
        iconHtml = `<img src="assets/icons/main-content/video.svg" alt="" style="filter:invert(1);" />`;
      } else if (item.type === 'audio') {
        iconHtml = `<img src="assets/icons/main-content/audio-lines.svg" alt="" style="filter:invert(1);" />`;
      } else if (item.type === 'document') {
        iconHtml = `<img src="assets/icons/main-content/file-text.svg" alt="" style="filter:invert(1);" />`;
      } else {
        iconHtml = `<img src="assets/icons/main-content/file.svg" alt="" style="filter:invert(1);" />`;
      }

      row.innerHTML = `
        <td>
          <div class="file-name-cell">
            <span class="file-icon-badge">${iconHtml}</span>
            <span class="file-name-text" title="${item.name}">${item.name}</span>
          </div>
        </td>
        <td><span class="file-mono-text">${item.extension}</span></td>
        <td><span class="file-mono-text">${item.size}</span></td>
        <td><span class="file-mono-text">${item.modified}</span></td>
        <td>
          <div class="file-actions-cell">
            ${
              !item.isFolder
                ? `
              <button class="file-action-btn btn-action-download" title="Download File">
                <img src="assets/icons/main-content/download.svg" alt="" style="filter:invert(1);" />
              </button>
            `
                : ''
            }
            <button class="file-action-btn btn-action-delete" title="Delete File from Child">
              <img src="assets/icons/main-content/storage/trash.svg" alt="" style="filter:invert(1);" />
            </button>
          </div>
        </td>
      `;

      // Single click selects file or folder
      row.addEventListener('click', (e) => {
        const target = e.target as HTMLElement;
        if (target.closest('.file-action-btn')) return;

        storageState.setSelectedItem(item);
      });

      // Double click on folder navigates into it
      row.addEventListener('dblclick', () => {
        if (item.isFolder) {
          navigateTo(item.path);
        }
      });

      // Download button action
      const downloadBtn = row.querySelector('.btn-action-download');
      downloadBtn?.addEventListener('click', async (e) => {
        e.stopPropagation();
        await triggerDownload(item);
      });

      // Delete button action
      const deleteBtn = row.querySelector('.btn-action-delete');
      deleteBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        storageState.setDeleteModalTarget(item);
      });

      tableBody.appendChild(row);
    });
  };

  /**
   * Updates the Load More button visibility and text.
   */
  const updateLoadMoreBar = () => {
    if (!loadMoreWrap || !loadMoreText || !loadMoreIcon) return;

    const hasMore = storageState.getHasMore();
    const isLoadingMore = storageState.getIsLoadingMore();

    if (!hasMore) {
      loadMoreWrap.classList.add('hidden');
      return;
    }

    loadMoreWrap.classList.remove('hidden');

    if (isLoadingMore) {
      loadMoreText.textContent = 'Loading next page...';
      loadMoreIcon.classList.add('files-spinner');
    } else {
      const itemsCount = storageState.getItems().length;
      const totalCount = storageState.getTotalCount();
      loadMoreText.textContent = `Load More Files (Showing ${itemsCount} of ${totalCount})`;
      loadMoreIcon.classList.remove('files-spinner');
    }
  };

  /**
   * Renders the File Metadata Inspector drawer on the right.
   */
  const renderInspector = () => {
    const selected = storageState.getSelectedItem();
    if (!inspectorDrawer || !inspectorBody) return;

    if (!selected) {
      inspectorDrawer.classList.add('hidden');
      return;
    }

    inspectorDrawer.classList.remove('hidden');

    let previewMediaHtml = '';
    if (selected.thumbnail) {
      previewMediaHtml = `
        <div class="inspector-thumbnail-container">
          <img src="${selected.thumbnail}" alt="Thumbnail" class="inspector-thumbnail-large" />
        </div>
      `;
    }

    let loadFullImageBtnHtml = '';
    if (selected.type === 'image') {
      const isCached = !!storageState.getCachedImagePreview(selected.path);
      const label = isCached ? 'View Full Image (Cached)' : 'Load Full Image';
      loadFullImageBtnHtml = `
        <button id="btn-inspector-load-image" class="inspector-btn btn-secondary" title="View Full High-Res Photo">
          <img src="assets/icons/main-content/eye.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
          <span>${label}</span>
        </button>
      `;
    }

    inspectorBody.innerHTML = `
      ${previewMediaHtml}
      <div class="inspector-file-name" title="${selected.name}">${selected.name}</div>
      <div class="inspector-meta-grid">
        <div class="inspector-meta-row">
          <span class="meta-label">Type:</span>
          <span class="meta-value">${selected.isFolder ? 'Folder' : selected.extension}</span>
        </div>
        <div class="inspector-meta-row">
          <span class="meta-label">Size:</span>
          <span class="meta-value">${selected.isFolder ? '--' : `${selected.size} (${selected.rawBytes.toLocaleString()} bytes)`}</span>
        </div>
        <div class="inspector-meta-row">
          <span class="meta-label">Modified:</span>
          <span class="meta-value">${selected.modified}</span>
        </div>
        <div class="inspector-meta-row">
          <span class="meta-label">MIME Type:</span>
          <span class="meta-value">${selected.mimeType}</span>
        </div>
        <div class="inspector-meta-row">
          <span class="meta-label">Path:</span>
          <span class="meta-value path-value" title="${selected.path}">${selected.path}</span>
        </div>
      </div>
      <div class="inspector-actions">
        ${loadFullImageBtnHtml}
        ${
          !selected.isFolder
            ? `
          <button id="btn-inspector-download" class="inspector-btn btn-primary">
            <img src="assets/icons/main-content/download.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
            <span>Download</span>
          </button>
        `
            : ''
        }
        <button id="btn-inspector-delete" class="inspector-btn btn-danger">
          <img src="assets/icons/main-content/storage/trash.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
          <span>Delete</span>
        </button>
      </div>
    `;

    // Bind inspector buttons
    const loadImgBtn = inspectorBody.querySelector<HTMLButtonElement>('#btn-inspector-load-image');
    loadImgBtn?.addEventListener('click', () => {
      triggerLoadFullImage(selected);
    });

    const downloadBtn = inspectorBody.querySelector<HTMLButtonElement>('#btn-inspector-download');
    downloadBtn?.addEventListener('click', async () => {
      await triggerDownload(selected);
    });

    const deleteBtn = inspectorBody.querySelector<HTMLButtonElement>('#btn-inspector-delete');
    deleteBtn?.addEventListener('click', () => {
      storageState.setDeleteModalTarget(selected);
    });
  };

  /**
   * Syncs the delete confirmation modal.
   */
  const renderDeleteModal = () => {
    const target = storageState.getDeleteModalTarget();
    if (!deleteModal || !deleteTargetPreview) return;

    if (!target) {
      deleteModal.classList.add('hidden');
      return;
    }

    deleteTargetPreview.innerHTML = `
      <div class="delete-preview-box">
        <strong>${target.name}</strong>
        <span class="delete-path-sub">${target.path}</span>
      </div>
    `;
    deleteModal.classList.remove('hidden');
  };

  /**
   * Syncs the in-app image preview modal.
   */
  const renderImageModal = () => {
    const preview = storageState.getImagePreview();
    if (!imageModal || !imageModalImg || !imageModalTitle) return;

    if (!preview.url) {
      imageModal.classList.add('hidden');
      imageModalImg.src = '';
      return;
    }

    imageModalTitle.textContent = preview.name || 'Photo Preview';
    imageModalImg.src = preview.url;
    imageModal.classList.remove('hidden');
  };

  /**
   * Streams or loads cached full image for in-app viewing.
   */
  const triggerLoadFullImage = (item: RemoteFileItem) => {
    // 1. Check Image Preview Cache first
    const cachedUrl = storageState.getCachedImagePreview(item.path);
    if (cachedUrl) {
      storageState.setImagePreview(cachedUrl, item.name);
      showStorageToast(`Loaded from cache: ${item.name}`);
      return;
    }

    // 2. Cache Miss: Request 64KB chunk stream
    showStorageToast(`Streaming full image: ${item.name}...`);
    const payload = JSON.stringify({
      type: CMD_STORAGE_GET_FILE,
      path: item.path,
      purpose: 'preview'
    });
    webrtcManager.sendCommand(payload);
  };

  /**
   * Prompts user for destination location before streaming and downloading.
   */
  const triggerDownload = async (item: RemoteFileItem) => {
    let fileHandle: any = null;

    // Use standard native File System Access API if supported
    if (typeof (window as any).showSaveFilePicker === 'function') {
      try {
        fileHandle = await (window as any).showSaveFilePicker({
          suggestedName: item.name,
          types: [{
            description: `${item.extension} file`,
            accept: {
              [item.mimeType || 'application/octet-stream']: [`.${item.name.split('.').pop() || ''}`]
            }
          }]
        });
      } catch (err: any) {
        if (err.name === 'AbortError') {
          showStorageToast('Download cancelled');
          return;
        }
        console.warn('[Storage] showSaveFilePicker failed:', err);
      }
    }

    if (fileHandle) {
      pendingSaveHandles.set(item.path, fileHandle);
    }

    showStorageToast(`Starting high-speed download: ${item.name}...`);
    const payload = JSON.stringify({
      type: CMD_STORAGE_GET_FILE,
      path: item.path,
      purpose: 'download'
    });
    webrtcManager.sendCommand(payload);
  };

  /**
   * Dispatches command to delete remote file after modal confirmation.
   */
  const confirmDelete = () => {
    const target = storageState.getDeleteModalTarget();
    if (!target) return;

    showStorageToast(`Deleting ${target.name} from child device...`);
    const payload = JSON.stringify({
      type: CMD_STORAGE_DELETE_FILE,
      path: target.path
    });
    webrtcManager.sendCommand(payload);
    storageState.setDeleteModalTarget(null);
  };

  // State Subscriptions
  storageState.subscribe(() => {
    renderBreadcrumbs();
    updateCategoryCounts();
    renderTableRows();
    updateLoadMoreBar();
    renderInspector();
    renderDeleteModal();
    renderImageModal();

    if (loadingOverlay) {
      if (storageState.getIsLoadingDirectory()) {
        loadingOverlay.classList.remove('hidden');
      } else {
        loadingOverlay.classList.add('hidden');
      }
    }
  });

  // Listen to device telemetry updates for storage gauge
  deviceState.subscribe(() => {
    updateStorageGauge();
  });

  // UI Event Handlers
  searchInput?.addEventListener('input', (e) => {
    storageState.setSearchQuery((e.target as HTMLInputElement).value);
  });

  categoryPills.forEach((pill) => {
    pill.addEventListener('click', () => {
      categoryPills.forEach((p) => p.classList.remove('active'));
      pill.classList.add('active');
      const cat = (pill.getAttribute('data-cat') || 'all') as FileCategory;
      storageState.setActiveCategory(cat);
    });
  });

  btnUp?.addEventListener('click', () => {
    const current = storageState.getCurrentPath();
    const parentPath = current.substring(0, current.lastIndexOf('/')) || '/storage/emulated/0';
    if (parentPath !== current) {
      navigateTo(parentPath);
    }
  });

  btnRefresh?.addEventListener('click', () => {
    storageState.invalidateDirectoryCache(storageState.getCurrentPath());
    navigateTo(storageState.getCurrentPath(), true);
  });

  btnLoadMore?.addEventListener('click', () => {
    if (storageState.getIsLoadingMore()) return;
    storageState.setIsLoadingMore(true);

    const listCmd = JSON.stringify({
      type: CMD_STORAGE_LIST,
      path: storageState.getCurrentPath(),
      offset: storageState.getNextOffset(),
      limit: 40
    });
    webrtcManager.sendCommand(listCmd);
  });

  btnInspectorClose?.addEventListener('click', () => {
    storageState.setSelectedItem(null);
  });

  btnDeleteCancel?.addEventListener('click', () => {
    storageState.setDeleteModalTarget(null);
  });

  btnDeleteConfirm?.addEventListener('click', () => {
    confirmDelete();
  });

  btnImageModalClose?.addEventListener('click', () => {
    storageState.setImagePreview(null, null);
  });

  btnImageModalDownload?.addEventListener('click', async () => {
    const preview = storageState.getImagePreview();
    if (preview.url && preview.name) {
      const link = document.createElement('a');
      link.href = preview.url;
      link.download = preview.name;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  });

  // Initial UI paint
  updateStorageGauge();
  renderBreadcrumbs();
  updateCategoryCounts();
  renderTableRows();

  // Initial directory fetch if connected
  if (deviceState.isConnected()) {
    navigateTo(storageState.getCurrentPath());
  }
}

/**
 * Displays a temporary status toast in the storage view.
 */
function showStorageToast(message: string): void {
  const toast = document.getElementById('files-toast');
  const toastText = document.getElementById('files-toast-text');
  if (!toast || !toastText) return;

  toastText.textContent = message;
  toast.classList.add('visible');

  setTimeout(() => {
    toast.classList.remove('visible');
  }, 3200);
}
