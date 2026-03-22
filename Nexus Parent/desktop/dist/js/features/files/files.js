/**
 * Files Feature Module
 * Displays and manages files from child device
 */

// Global storage for Files data (persists across feature instances)
if (!window.filesFeatureData) {
    window.filesFeatureData = {
        files: [],
        fileMap: new Map(),
        visibleCount: 60,
        currentPath: null,
        isListing: false,
        selectedFile: null
    };
}

class FilesFeature {
    constructor(container) {
        this.container = container;
        this.data = window.filesFeatureData;
        this.currentRequestId = null;
        
        this.ICON_MAP = {
            folder: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>',
            image: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>',
            video: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>',
            audio: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>',
            pdf: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>',
            archive: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="21 8 21 21 3 21 3 8"></polyline><rect x="1" y="3" width="22" height="5"></rect><line x1="10" y1="12" x2="14" y2="12"></line></svg>',
            doc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><line x1="10" y1="9" x2="8" y2="9"></line></svg>',
            text: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><line x1="10" y1="9" x2="8" y2="9"></line></svg>',
            file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>'
        };
        
        this.init();
    }

    async init() {
        // Load HTML template
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        // Load CSS
        await this.loadStyles();

        // Setup event listeners
        this.setupEventListeners();

        // Listen for file messages
        this.listenForFileMessages();

        // Render existing files if any
        this.renderGallery();
        
        // Restore detail view if file was selected
        if (this.data.selectedFile) {
            this.showDetail(this.data.selectedFile);
        }
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/files/files.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Files template:', error);
            return '<div>Failed to load Files feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/files/files.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Files styles:', error);
        }
    }

    setupEventListeners() {
        const refreshBtn = this.container.querySelector('#files-refresh-btn');
        const backBtn = this.container.querySelector('#files-back-btn');
        const loadMoreBtn = this.container.querySelector('#files-loadmore-btn');
        const detailBackBtn = this.container.querySelector('#files-detail-back');
        const previewBtn = this.container.querySelector('#files-preview-btn');
        const downloadBtn = this.container.querySelector('#files-download-btn');

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.refreshListing());
        }

        if (backBtn) {
            backBtn.addEventListener('click', () => this.navigateBack());
        }

        if (loadMoreBtn) {
            loadMoreBtn.addEventListener('click', () => this.loadMore());
        }

        if (detailBackBtn) {
            detailBackBtn.addEventListener('click', () => this.hideDetail());
        }

        if (previewBtn) {
            previewBtn.addEventListener('click', () => this.requestPreview());
        }

        if (downloadBtn) {
            downloadBtn.addEventListener('click', () => this.downloadFile());
        }
    }

    listenForFileMessages() {
        // Listen for file list messages
        window.addEventListener('file-list-batch', (event) => {
            console.log('📁 File list batch received:', event.detail);
            this.handleListBatch(event.detail);
        });

        window.addEventListener('file-list-complete', (event) => {
            console.log('📁 File list complete:', event.detail);
            this.handleListComplete(event.detail);
        });

        window.addEventListener('file-list-error', (event) => {
            console.log('📁 File list error:', event.detail);
            this.handleListError(event.detail);
        });

        // Listen for file transfer messages
        window.addEventListener('file-transfer', (event) => {
            console.log('📁 File transfer update:', event.detail);
            this.handleTransferUpdate(event.detail);
        });
    }

    startListing() {
        console.log('📁 Starting file listing');

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            if (this.data.isListing) {
                this.showStatus('Scan already in progress', 'info');
                return;
            }

            this.data.isListing = true;
            this.currentRequestId = `files-${Date.now()}`;
            this.data.files = [];
            this.data.fileMap.clear();
            this.data.visibleCount = 60;
            this.renderGallery();
            this.showStatus('Scanning storage...', 'active');

            const command = {
                cmd: 'LIST_FILES',
                request_id: this.currentRequestId,
                include_hidden: false
            };

            if (this.data.currentPath) {
                command.path = this.data.currentPath;
            }

            app.connectionManager.sendCommand(JSON.stringify(command));

        } catch (error) {
            console.error('❌ Failed to start file listing:', error);
            this.showStatus('Failed to start scan: ' + error.message, 'error');
            this.data.isListing = false;
        }
    }

    refreshListing() {
        if (this.data.isListing) {
            this.showStatus('Scan already in progress', 'info');
            return;
        }
        this.startListing();
    }

    handleListBatch(data) {
        if (data.request_id && this.currentRequestId && data.request_id !== this.currentRequestId) {
            return;
        }

        const items = Array.isArray(data.items) ? data.items : [];
        items.forEach((item) => this.upsertFile(item, data));

        this.showStatus(`Received ${this.data.files.length} files`, 'success');
        this.renderGallery();
    }

    handleListComplete(data) {
        if (data.request_id && this.currentRequestId && data.request_id !== this.currentRequestId) {
            return;
        }

        this.data.isListing = false;
        const total = this.data.files.length;

        if (total === 0) {
            this.showStatus('No files found', 'warning');
        } else {
            this.showStatus(`${total} files`, 'success');
        }
    }

    handleListError(data) {
        if (data.request_id && this.currentRequestId && data.request_id !== this.currentRequestId) {
            return;
        }

        this.data.isListing = false;
        const reason = data.reason || 'Unknown error';
        this.showStatus(`Scan failed: ${reason}`, 'error');
    }

    upsertFile(item, meta) {
        if (!item || !item.absolute_path) return;

        const key = item.absolute_path;
        const existing = this.data.fileMap.get(key) || {};

        const normalized = {
            ...existing,
            ...item,
            storage_id: meta.storage_id,
            storage_label: meta.storage_label,
            parent_path: meta.parent_path
        };

        if (!normalized.created_timestamp && normalized.modified_timestamp) {
            normalized.created_timestamp = normalized.modified_timestamp;
        }

        this.data.fileMap.set(key, normalized);

        if (!existing || Object.keys(existing).length === 0) {
            this.data.files.push(normalized);
        }

        // Sort by timestamp (newest first)
        this.data.files.sort((a, b) => {
            const aTime = a.created_timestamp || a.modified_timestamp || 0;
            const bTime = b.created_timestamp || b.modified_timestamp || 0;
            return bTime - aTime;
        });
    }

    renderGallery() {
        const gallery = this.container.querySelector('#files-gallery');
        const loadMoreWrap = this.container.querySelector('#files-loadmore-wrap');
        
        if (!gallery) return;

        gallery.innerHTML = '';

        if (this.data.files.length === 0) {
            gallery.classList.add('empty');
            gallery.innerHTML = `
                <div class="files-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                    </svg>
                    <p>No files yet</p>
                </div>
            `;
            
            if (loadMoreWrap) loadMoreWrap.classList.add('hidden');
            return;
        }

        gallery.classList.remove('empty');

        const visible = this.data.files.slice(0, this.data.visibleCount);
        visible.forEach((file) => {
            gallery.appendChild(this.createFileCard(file));
        });

        // Show/hide load more button
        if (loadMoreWrap) {
            if (this.data.files.length > this.data.visibleCount) {
                loadMoreWrap.classList.remove('hidden');
            } else {
                loadMoreWrap.classList.add('hidden');
            }
        }
    }

    createFileCard(file) {
        const card = document.createElement('button');
        card.className = 'file-card';
        card.type = 'button';

        const icon = this.ICON_MAP[file.file_type_icon] || this.ICON_MAP.file;
        const thumbnail = file.thumbnail_base64
            ? `url(data:image/jpeg;base64,${file.thumbnail_base64})`
            : 'none';
        const hasThumb = Boolean(file.thumbnail_base64);

        const sizeStr = file.is_directory ? 'Folder' : this.formatBytes(file.file_size);
        
        // Extract just the folder name from the path for display
        let displayName = file.filename;
        if (file.is_directory && file.absolute_path) {
            const pathParts = file.absolute_path.split('/').filter(p => p);
            displayName = pathParts[pathParts.length - 1] || file.filename;
        }
        
        const metaStr = `${sizeStr} • ${this.formatDate(file.created_timestamp)}`;

        card.innerHTML = `
            <div class="file-thumb ${hasThumb ? '' : 'no-thumb'}" style="background-image:${thumbnail};">
                ${!hasThumb ? `<div class="file-icon">${icon}</div>` : ''}
            </div>
            <div class="file-info">
                <div class="file-name" title="${this.escapeHtml(displayName)}">${this.escapeHtml(displayName)}</div>
                <div class="file-meta">${metaStr}</div>
            </div>
        `;

        card.addEventListener('click', () => {
            if (file.is_directory) {
                this.navigateToFolder(file);
            } else {
                this.showDetail(file);
            }
        });

        return card;
    }

    navigateToFolder(folder) {
        console.log('📁 Navigating to folder:', folder.absolute_path);

        this.data.currentPath = folder.absolute_path;
        this.updateBreadcrumb(folder);

        const backBtn = this.container.querySelector('#files-back-btn');
        if (backBtn) {
            backBtn.style.display = 'flex';
        }

        this.data.files = [];
        this.data.fileMap.clear();
        this.data.visibleCount = 60;
        this.renderGallery();

        this.startListing();
    }

    navigateBack() {
        this.data.currentPath = null;
        this.updateBreadcrumb(null);

        const backBtn = this.container.querySelector('#files-back-btn');
        if (backBtn) {
            backBtn.style.display = 'none';
        }

        this.data.files = [];
        this.data.fileMap.clear();
        this.data.visibleCount = 60;
        this.renderGallery();

        this.startListing();
    }

    updateBreadcrumb(folder) {
        const breadcrumb = this.container.querySelector('#files-breadcrumb');
        if (!breadcrumb) return;

        if (!folder || !this.data.currentPath) {
            breadcrumb.innerHTML = '<span class="breadcrumb-item active">Root</span>';
            return;
        }

        const pathParts = this.data.currentPath.split('/').filter(part => part.length > 0);
        const breadcrumbItems = ['Root'];

        const visibleParts = pathParts.slice(-3);
        if (pathParts.length > 3) {
            breadcrumbItems.push('...');
        }
        breadcrumbItems.push(...visibleParts);

        breadcrumb.innerHTML = breadcrumbItems
            .map((item, index) => {
                const isLast = index === breadcrumbItems.length - 1;
                return `<span class="breadcrumb-item ${isLast ? 'active' : ''}">${this.escapeHtml(item)}</span>`;
            })
            .join('<span class="breadcrumb-separator">/</span>');
    }

    loadMore() {
        this.data.visibleCount += 60;
        this.renderGallery();
    }

    showDetail(file) {
        this.data.selectedFile = file;

        const detail = this.container.querySelector('#files-detail');
        const content = this.container.querySelector('.files-content');

        if (!detail || !content) return;

        // Hide main content
        content.style.display = 'none';

        // Update detail view
        const filename = detail.querySelector('#files-detail-filename');
        if (filename) {
            filename.textContent = this.escapeHtml(file.filename);
        }

        // Update meta grid
        const metaGrid = detail.querySelector('#files-meta-grid');
        if (metaGrid) {
            metaGrid.innerHTML = '';

            const metaFields = [
                ['Filename', file.filename],
                ['Path', file.absolute_path],
                ['Size', this.formatBytes(file.file_size)],
                ['MIME', file.mime_type || 'unknown'],
                ['Created', this.formatFullDate(file.created_timestamp)],
                ['Modified', this.formatFullDate(file.modified_timestamp)],
                ['Storage', file.storage_label || file.storage_id || '—'],
                ['Type', file.file_type_icon || 'file']
            ];

            metaFields.forEach(([label, value]) => {
                const item = document.createElement('div');
                item.className = 'files-meta-item';
                item.innerHTML = `
                    <div class="files-meta-label">${this.escapeHtml(label)}</div>
                    <div class="files-meta-value">${this.escapeHtml(value || '—')}</div>
                `;
                metaGrid.appendChild(item);
            });
        }

        // Update preview pane
        this.updatePreviewPane(file);

        // Show detail view
        detail.classList.remove('hidden');
    }

    hideDetail() {
        const detail = this.container.querySelector('#files-detail');
        const content = this.container.querySelector('.files-content');

        if (detail) {
            detail.classList.add('hidden');
        }

        if (content) {
            content.style.display = 'flex';
        }

        this.data.selectedFile = null;
    }

    updatePreviewPane(file, localPath) {
        const preview = this.container.querySelector('#files-preview');
        if (!preview) return;

        preview.innerHTML = '';

        if (localPath) {
            if ((file.mime_type || '').startsWith('image/')) {
                const img = document.createElement('img');
                img.src = `file://${localPath}`;
                img.alt = file.filename;
                img.className = 'file-preview-media';
                preview.appendChild(img);
            } else if ((file.mime_type || '').startsWith('video/')) {
                const video = document.createElement('video');
                video.src = `file://${localPath}`;
                video.controls = true;
                video.autoplay = false;
                video.className = 'file-preview-media';
                video.preload = 'metadata';
                preview.appendChild(video);
            } else {
                preview.innerHTML = `
                    <div class="preview-placeholder">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14 2 14 8 20 8"></polyline>
                        </svg>
                        <p>File downloaded</p>
                        <p style="font-size: 10px; opacity: 0.6;">${this.escapeHtml(localPath)}</p>
                    </div>
                `;
            }
        } else if (file.thumbnail_base64) {
            const img = document.createElement('img');
            img.src = `data:image/jpeg;base64,${file.thumbnail_base64}`;
            img.alt = file.filename;
            img.className = 'file-preview-media ghost';
            preview.appendChild(img);
        } else {
            const icon = this.ICON_MAP[file.file_type_icon] || this.ICON_MAP.file;
            preview.innerHTML = `
                <div class="preview-placeholder">
                    <div style="width: 64px; height: 64px; color: rgba(255, 255, 255, 0.4);">${icon}</div>
                    <p>Click Preview to load</p>
                </div>
            `;
        }
    }

    requestPreview() {
        if (!this.data.selectedFile) return;

        const file = this.data.selectedFile;

        if (file.is_directory) {
            this.showStatus('Preview not available for directories', 'info');
            return;
        }

        console.log('📁 Requesting preview for:', file.filename);

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = {
                cmd: 'REQUEST_FILE',
                file_path: file.absolute_path,
                purpose: 'preview'
            };

            app.connectionManager.sendCommand(JSON.stringify(command));

            this.showProgress('Requesting file...', 0);

        } catch (error) {
            console.error('❌ Failed to request preview:', error);
            this.showStatus('Failed to request preview: ' + error.message, 'error');
        }
    }

    downloadFile() {
        if (!this.data.selectedFile) return;

        const file = this.data.selectedFile;

        if (file.is_directory) {
            this.showStatus('Downloading folders is not supported', 'warning');
            return;
        }

        console.log('📁 Downloading file:', file.filename);

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = {
                cmd: 'REQUEST_FILE',
                file_path: file.absolute_path,
                purpose: 'download'
            };

            app.connectionManager.sendCommand(JSON.stringify(command));

            this.showProgress('Starting download...', 0);

        } catch (error) {
            console.error('❌ Failed to start download:', error);
            this.showStatus('Failed to start download: ' + error.message, 'error');
        }
    }

    handleTransferUpdate(data) {
        if (!this.data.selectedFile) return;
        if (data.file_path !== this.data.selectedFile.absolute_path) return;

        if (data.status === 'progress') {
            const progress = data.progress || 0;
            this.showProgress(`Transfer — ${Math.round(progress * 100)}%`, progress);
        } else if (data.status === 'complete') {
            this.showProgress('Transfer complete', 1);
            if (data.local_path) {
                this.updatePreviewPane(this.data.selectedFile, data.local_path);
            }
        } else if (data.status === 'error') {
            this.showProgress(`Transfer failed: ${data.reason || 'Unknown error'}`, 0);
        }
    }

    showProgress(message, value) {
        const progress = this.container.querySelector('#files-progress');
        if (!progress) return;

        progress.textContent = message;
        progress.classList.remove('hidden');

        if (value >= 1) {
            setTimeout(() => {
                progress.classList.add('hidden');
            }, 3000);
        }
    }

    showStatus(text, tone = 'info') {
        const status = this.container.querySelector('#files-status');
        if (!status) return;

        status.textContent = text;
        status.setAttribute('data-tone', tone);
    }

    // Utility functions
    escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    formatBytes(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }

    formatDate(timestamp) {
        if (!timestamp) return '—';
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
        if (diff < 604800000) return `${Math.floor(diff / 86400000)}d ago`;

        return date.toLocaleDateString();
    }

    formatFullDate(timestamp) {
        if (!timestamp) return '—';
        const date = new Date(timestamp);
        return date.toLocaleString();
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.FilesFeature = FilesFeature;
