/**
 * @file files.ts
 * @description Remote File Explorer & Storage Monitor View component for G-Home desktop.
 *
 * Hey friend! This module implements the full-viewport file monitoring interface.
 * When the user selects "Monitor Files" from the sidebar, this view gives them full remote
 * visibility into the child device's storage (internal storage, SD card, photos, videos, voice notes,
 * documents, and downloads).
 *
 * Features included:
 *  1. Breadcrumb navigation trail to move up and down directory trees.
 *  2. Category filters (All, Images, Videos, Audio, Documents, System).
 *  3. Live instant search filtering across file and folder names.
 *  4. Storage capacity meter and detailed metadata (type, size, modified date).
 *  5. Direct download and preview actions with visual confirmation toasts.
 */

/**
 * Interface representing a remote file or directory on the target device.
 */
export interface RemoteFileItem {
  id: string;
  name: string;
  type: 'folder' | 'image' | 'video' | 'audio' | 'document' | 'archive';
  extension: string;
  size: string;
  rawBytes: number;
  modified: string;
  path: string;
  isFolder: boolean;
}

/**
 * Mock file system hierarchy for the target child device (Pixel 8 Pro).
 */
const INITIAL_REMOTE_FILES: RemoteFileItem[] = [
  // Folders
  { id: 'dir-1', name: 'DCIM', type: 'folder', extension: 'DIR', size: '4.8 GB', rawBytes: 5153960755, modified: '12 mins ago', path: '/storage/emulated/0/DCIM', isFolder: true },
  { id: 'dir-2', name: 'Pictures', type: 'folder', extension: 'DIR', size: '1.2 GB', rawBytes: 1288490188, modified: '2 hours ago', path: '/storage/emulated/0/Pictures', isFolder: true },
  { id: 'dir-3', name: 'Download', type: 'folder', extension: 'DIR', size: '2.4 GB', rawBytes: 2576980377, modified: 'Yesterday', path: '/storage/emulated/0/Download', isFolder: true },
  { id: 'dir-4', name: 'WhatsApp Media', type: 'folder', extension: 'DIR', size: '3.6 GB', rawBytes: 3865470566, modified: 'Nov 10, 2026', path: '/storage/emulated/0/WhatsApp Media', isFolder: true },
  { id: 'dir-5', name: 'Recordings', type: 'folder', extension: 'DIR', size: '640 MB', rawBytes: 671088640, modified: 'Nov 09, 2026', path: '/storage/emulated/0/Recordings', isFolder: true },
  { id: 'dir-6', name: 'Documents', type: 'folder', extension: 'DIR', size: '240 MB', rawBytes: 251658240, modified: 'Nov 05, 2026', path: '/storage/emulated/0/Documents', isFolder: true },

  // Images
  { id: 'file-1', name: 'IMG_20261111_143022.jpg', type: 'image', extension: 'JPG', size: '4.2 MB', rawBytes: 4404019, modified: '14 mins ago', path: '/storage/emulated/0/DCIM/Camera', isFolder: false },
  { id: 'file-2', name: 'IMG_20261111_112005.jpg', type: 'image', extension: 'JPG', size: '3.8 MB', rawBytes: 3984588, modified: '3 hours ago', path: '/storage/emulated/0/DCIM/Camera', isFolder: false },
  { id: 'file-3', name: 'Screenshot_20261111-094112.png', type: 'image', extension: 'PNG', size: '1.1 MB', rawBytes: 1153433, modified: '5 hours ago', path: '/storage/emulated/0/Pictures/Screenshots', isFolder: false },

  // Videos
  { id: 'file-4', name: 'VID_20261110_182011.mp4', type: 'video', extension: 'MP4', size: '84.6 MB', rawBytes: 88709120, modified: 'Yesterday', path: '/storage/emulated/0/DCIM/Camera', isFolder: false },
  { id: 'file-5', name: 'Screen_Recording_20261109.mp4', type: 'video', extension: 'MP4', size: '112.4 MB', rawBytes: 117859942, modified: 'Nov 09, 2026', path: '/storage/emulated/0/Movies', isFolder: false },

  // Audio & Voice Recordings
  { id: 'file-6', name: 'Voice_Note_20261111_0842.m4a', type: 'audio', extension: 'M4A', size: '4.8 MB', rawBytes: 5033164, modified: '6 hours ago', path: '/storage/emulated/0/Recordings', isFolder: false },
  { id: 'file-7', name: 'Call_Recording_+12025550192.m4a', type: 'audio', extension: 'M4A', size: '2.1 MB', rawBytes: 2202009, modified: 'Yesterday', path: '/storage/emulated/0/Recordings/Calls', isFolder: false },

  // Documents
  { id: 'file-8', name: 'School_Schedule_Term2.pdf', type: 'document', extension: 'PDF', size: '1.4 MB', rawBytes: 1468006, modified: 'Nov 08, 2026', path: '/storage/emulated/0/Documents', isFolder: false },
  { id: 'file-9', name: 'Biology_Assignment_Draft.docx', type: 'document', extension: 'DOCX', size: '420 KB', rawBytes: 430080, modified: 'Nov 07, 2026', path: '/storage/emulated/0/Documents', isFolder: false },

  // Backups / Archives
  { id: 'file-10', name: 'msgstore.db.crypt15', type: 'archive', extension: 'CRYPT15', size: '52.4 MB', rawBytes: 54945382, modified: 'Today 04:00 AM', path: '/storage/emulated/0/Android/media/com.whatsapp', isFolder: false }
];

/**
 * Creates and returns the full-viewport Monitor Files DOM element.
 */
export function renderFilesView(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'files-container';

  // Current active navigation state
  let currentPath = '/storage/emulated/0';
  let activeCategory: string = 'all';
  let searchQuery: string = '';

  container.innerHTML = `
    <!-- Topbar Header -->
    <div class="view-topbar">
      <div class="view-topbar-left">
        <span class="view-title">Monitor Files</span>
      </div>
      <div class="view-topbar-right">
        <button id="btn-files-up" class="topbar-btn" title="Parent Directory">
          <img src="assets/icons/main-content/arrow-left.svg" alt="" style="width:13px;height:13px;filter:invert(1);" />
          <span>Parent Dir</span>
        </button>
      </div>
    </div>

    <!-- Secondary Navigation & Breadcrumbs Bar -->
    <div class="files-subbar">
      <div class="files-breadcrumbs" id="files-breadcrumbs">
        <span class="breadcrumb-item" data-path="/storage">storage</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item" data-path="/storage/emulated">emulated</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active" data-path="/storage/emulated/0">0 (Internal)</span>
      </div>
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
        <span class="category-pill-badge" id="cat-count-all">16</span>
      </button>
      <button class="category-pill" data-cat="image">
        <span>Images & Photos</span>
        <span class="category-pill-badge" id="cat-count-image">3</span>
      </button>
      <button class="category-pill" data-cat="video">
        <span>Videos</span>
        <span class="category-pill-badge" id="cat-count-video">2</span>
      </button>
      <button class="category-pill" data-cat="audio">
        <span>Audio & Voice</span>
        <span class="category-pill-badge" id="cat-count-audio">2</span>
      </button>
      <button class="category-pill" data-cat="document">
        <span>Documents</span>
        <span class="category-pill-badge" id="cat-count-document">2</span>
      </button>
      <button class="category-pill" data-cat="archive">
        <span>Backups & Archives</span>
        <span class="category-pill-badge" id="cat-count-archive">1</span>
      </button>
    </div>

    <!-- Storage Capacity Strip -->
    <div class="storage-summary-strip">
      <div class="storage-progress-container">
        <span>STORAGE USAGE</span>
        <div class="storage-progress-bar">
          <div class="storage-progress-fill"></div>
        </div>
        <span>114.2 GB / 256 GB USED (44.6%)</span>
      </div>
      <div id="files-status-count">
        Showing 16 items
      </div>
    </div>

    <!-- File Explorer Table Area -->
    <div class="files-explorer-area">
      <table class="files-table">
        <thead>
          <tr>
            <th style="width: 48%;">Name</th>
            <th style="width: 14%;">Type</th>
            <th style="width: 14%;">Size</th>
            <th style="width: 14%;">Last Modified</th>
            <th style="width: 10%; text-align: right;">Actions</th>
          </tr>
        </thead>
        <tbody id="files-table-body">
          <!-- Populated dynamically -->
        </tbody>
      </table>
    </div>

    <!-- Feedback Toast -->
    <div id="files-toast" class="files-toast">
      <img src="assets/icons/main-content/download.svg" alt="" style="width:14px;height:14px;filter:invert(1);" />
      <span id="files-toast-text">File downloaded to /Downloads/Guardian</span>
    </div>
  `;

  // Attach interactive behavior after mounting
  setTimeout(() => {
    bindFilesEvents(container, currentPath, activeCategory, searchQuery);
  }, 0);

  return container;
}

/**
 * Sets up event listeners and dynamic rendering logic for the file view.
 */
function bindFilesEvents(
  container: HTMLElement, 
  currentPath: string, 
  activeCategory: string, 
  searchQuery: string
): void {
  const tableBody = container.querySelector<HTMLTableSectionElement>('#files-table-body');
  const searchInput = container.querySelector<HTMLInputElement>('#files-search-input');
  const categoryPills = container.querySelectorAll<HTMLButtonElement>('.category-pill');
  const breadcrumbsEl = container.querySelector<HTMLElement>('#files-breadcrumbs');
  const btnUp = container.querySelector<HTMLButtonElement>('#btn-files-up');
  const statusCount = container.querySelector<HTMLElement>('#files-status-count');
  const toast = container.querySelector<HTMLElement>('#files-toast');
  const toastText = container.querySelector<HTMLElement>('#files-toast-text');

  if (!tableBody) return;

  /**
   * Updates the breadcrumb trail visual elements based on current active directory.
   */
  const updateBreadcrumbs = (newPath: string) => {
    currentPath = newPath;
    if (!breadcrumbsEl) return;
    const parts = currentPath.split('/').filter(Boolean);
    let html = '';
    let accumulated = '';
    parts.forEach((part, idx) => {
      accumulated += '/' + part;
      const isLast = idx === parts.length - 1;
      html += `<span class="breadcrumb-item ${isLast ? 'active' : ''}" data-path="${accumulated}">${part}</span>`;
      if (!isLast) {
        html += `<span class="breadcrumb-separator">/</span>`;
      }
    });
    breadcrumbsEl.innerHTML = html;

    // Attach click listeners to newly rendered breadcrumbs
    breadcrumbsEl.querySelectorAll('.breadcrumb-item').forEach(item => {
      item.addEventListener('click', () => {
        const path = item.getAttribute('data-path');
        if (path) {
          updateBreadcrumbs(path);
          showToast(`Navigated to ${path}`);
        }
      });
    });
  };

  /**
   * Displays a temporary notification toast.
   */
  const showToast = (message: string) => {
    if (!toast || !toastText) return;
    toastText.textContent = message;
    toast.classList.add('visible');
    setTimeout(() => {
      toast.classList.remove('visible');
    }, 2800);
  };

  /**
   * Re-renders the table rows based on current filter states.
   */
  const renderRows = () => {
    let filtered = INITIAL_REMOTE_FILES;

    // Filter by Category
    if (activeCategory !== 'all') {
      filtered = filtered.filter(f => f.type === activeCategory);
    }

    // Filter by Search Query
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().trim();
      filtered = filtered.filter(f => f.name.toLowerCase().includes(q) || f.extension.toLowerCase().includes(q));
    }

    if (statusCount) {
      statusCount.textContent = `Showing ${filtered.length} item${filtered.length === 1 ? '' : 's'}`;
    }

    tableBody.innerHTML = '';

    if (filtered.length === 0) {
      tableBody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align:center; padding: 48px; color: var(--text-muted);">
            No matching files found in this directory.
          </td>
        </tr>
      `;
      return;
    }

    filtered.forEach(item => {
      const row = document.createElement('tr');

      // Determine icon based on file type
      let iconSrc = 'assets/icons/main-content/file.svg';
      if (item.isFolder) {
        iconSrc = 'assets/icons/main-content/folder.svg';
      } else if (item.type === 'image') {
        iconSrc = 'assets/icons/main-content/image.svg';
      } else if (item.type === 'video') {
        iconSrc = 'assets/icons/main-content/video.svg';
      } else if (item.type === 'audio') {
        iconSrc = 'assets/icons/main-content/audio-lines.svg';
      } else if (item.type === 'document') {
        iconSrc = 'assets/icons/main-content/file-text.svg';
      }

      row.innerHTML = `
        <td>
          <div class="file-name-cell">
            <span class="file-icon-badge">
              <img src="${iconSrc}" alt="" style="filter:invert(1);" />
            </span>
            <span>${item.name}</span>
          </div>
        </td>
        <td>
          <span class="file-mono-text">${item.extension}</span>
        </td>
        <td>
          <span class="file-mono-text">${item.size}</span>
        </td>
        <td>
          <span class="file-mono-text">${item.modified}</span>
        </td>
        <td>
          <div class="file-actions-cell">
            <button class="file-action-btn btn-action-preview" title="Preview File">
              <img src="assets/icons/main-content/eye.svg" alt="" style="filter:invert(1);" />
            </button>
            <button class="file-action-btn btn-action-download" title="Download File">
              <img src="assets/icons/main-content/download.svg" alt="" style="filter:invert(1);" />
            </button>
          </div>
        </td>
      `;

      // Handle row click (open folder)
      row.addEventListener('click', (e) => {
        const target = e.target as HTMLElement;
        if (target.closest('.file-action-btn')) return; // Ignore if clicking action buttons

        if (item.isFolder) {
          updateBreadcrumbs(item.path);
          showToast(`Opened folder: ${item.name}`);
        } else {
          row.classList.toggle('selected');
        }
      });

      // Handle download button click
      const downloadBtn = row.querySelector('.btn-action-download');
      downloadBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        showToast(`Downloaded ${item.name} to Guardian Storage`);
      });

      // Handle preview button click
      const previewBtn = row.querySelector('.btn-action-preview');
      previewBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        showToast(`Inspecting metadata for ${item.name}`);
      });

      tableBody.appendChild(row);
    });
  };

  // Search input handler
  searchInput?.addEventListener('input', (e) => {
    searchQuery = (e.target as HTMLInputElement).value;
    renderRows();
  });

  // Category pills handler
  categoryPills.forEach(pill => {
    pill.addEventListener('click', () => {
      categoryPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      activeCategory = pill.getAttribute('data-cat') || 'all';
      renderRows();
    });
  });

  // Up button handler
  btnUp?.addEventListener('click', () => {
    const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/storage';
    updateBreadcrumbs(parentPath);
    showToast(`Navigated up to ${parentPath}`);
  });

  // Initialize breadcrumb listeners and table render
  updateBreadcrumbs(currentPath);
  renderRows();
}

export const renderStorageView = renderFilesView;

