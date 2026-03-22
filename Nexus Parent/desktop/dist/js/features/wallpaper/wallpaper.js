/**
 * Wallpaper Feature Module
 * Send wallpaper images to child device
 */

class WallpaperFeature {
    constructor(container) {
        this.container = container;
        this.selectedFile = null;
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
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/wallpaper/wallpaper.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Wallpaper template:', error);
            return '<div>Failed to load Wallpaper feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/wallpaper/wallpaper.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Wallpaper styles:', error);
        }
    }

    setupEventListeners() {
        const dropZone = this.container.querySelector('#wallpaper-drop-zone');
        const fileInput = this.container.querySelector('#wallpaper-file-input');
        const previewImg = this.container.querySelector('#wallpaper-preview-img');
        const previewContainer = this.container.querySelector('#wallpaper-preview');
        const placeholder = this.container.querySelector('#wallpaper-placeholder');
        const setBtn = this.container.querySelector('#set-wallpaper-btn');
        const clearBtn = this.container.querySelector('#clear-wallpaper-btn');

        // File input change
        if (fileInput) {
            fileInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file && file.type.startsWith('image/')) {
                    this.showPreview(file);
                }
            });
        }

        // Drag & drop
        if (dropZone) {
            // Click to browse
            dropZone.addEventListener('click', () => {
                if (fileInput) fileInput.click();
            });
            
            dropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                dropZone.style.borderColor = '#22c55e';
                dropZone.style.background = 'rgba(63, 63, 70, 0.8)';
            });

            dropZone.addEventListener('dragleave', () => {
                dropZone.style.borderColor = 'rgba(82, 82, 91, 0.5)';
                dropZone.style.background = 'rgba(39, 39, 42, 0.5)';
            });

            dropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                dropZone.style.borderColor = 'rgba(82, 82, 91, 0.5)';
                dropZone.style.background = 'rgba(39, 39, 42, 0.5)';
                const file = e.dataTransfer.files[0];
                if (file && file.type.startsWith('image/')) {
                    this.showPreview(file);
                }
            });
        }

        // Set wallpaper button
        if (setBtn) {
            setBtn.addEventListener('click', () => this.setWallpaper());
        }

        // Clear preview button
        if (clearBtn) {
            clearBtn.addEventListener('click', () => this.clearPreview());
        }
    }

    showPreview(file) {
        this.selectedFile = file;
        const reader = new FileReader();
        const previewImg = this.container.querySelector('#wallpaper-preview-img');
        const previewContainer = this.container.querySelector('#wallpaper-preview');
        const placeholder = this.container.querySelector('#wallpaper-placeholder');
        const setBtn = this.container.querySelector('#set-wallpaper-btn');

        reader.onload = (e) => {
            if (previewImg && previewContainer) {
                previewImg.src = e.target.result;
                previewContainer.style.display = 'block';
                if (placeholder) placeholder.style.display = 'none';
                if (setBtn) setBtn.style.display = 'block';
            }
        };
        reader.readAsDataURL(file);
    }

    async setWallpaper() {
        const setBtn = this.container.querySelector('#set-wallpaper-btn');
        const statusDiv = this.container.querySelector('#wallpaper-status');

        if (!this.selectedFile) {
            this.showStatus('Please select an image first', 'warning');
            return;
        }

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Check file size (limit to 10MB)
            if (this.selectedFile.size > 10 * 1024 * 1024) {
                this.showStatus('Image too large. Please select an image under 10MB.', 'error');
                return;
            }

            this.showStatus('Sending wallpaper...', 'info');
            setBtn.disabled = true;
            setBtn.textContent = 'Sending...';

            // Read file as ArrayBuffer
            const arrayBuffer = await this.selectedFile.arrayBuffer();

            // Send command header
            const command = JSON.stringify({
                cmd: 'SET_WALLPAPER',
                size: arrayBuffer.byteLength,
                filename: this.selectedFile.name
            });
            app.connectionManager.sendCommand(command);

            // Send image data in chunks (16KB chunks for reliability)
            const chunkSize = 16384;
            const totalChunks = Math.ceil(arrayBuffer.byteLength / chunkSize);

            for (let i = 0; i < totalChunks; i++) {
                const start = i * chunkSize;
                const end = Math.min(start + chunkSize, arrayBuffer.byteLength);
                const chunk = arrayBuffer.slice(start, end);
                
                // Send binary chunk
                app.connectionManager.sendBinaryData(chunk);

                // Update progress
                const progress = Math.round(((i + 1) / totalChunks) * 100);
                setBtn.textContent = `Sending... ${progress}%`;
            }

            console.log('📤 Wallpaper sent successfully');

            // Listen for status messages
            this.listenForWallpaperStatus(setBtn);

            // 30 second timeout
            setTimeout(() => {
                if (setBtn.disabled) {
                    setBtn.disabled = false;
                    setBtn.textContent = 'Set Wallpaper';
                    this.showStatus('Timeout waiting for response', 'error');
                }
            }, 30000);

        } catch (error) {
            console.error('Failed to send wallpaper:', error);
            this.showStatus(`Failed to send wallpaper: ${error.message}`, 'error');
            setBtn.disabled = false;
            setBtn.textContent = 'Set Wallpaper';
        }
    }

    listenForWallpaperStatus(setBtn) {
        const handler = (event) => {
            const message = event.detail.message;
            
            if (message === 'WALLPAPER_RECEIVING') {
                this.showStatus('Child is receiving wallpaper...', 'info');
            } else if (message === 'WALLPAPER_SET_SUCCESS') {
                this.showStatus('Wallpaper set successfully!', 'success');
                setBtn.disabled = false;
                setBtn.textContent = 'Set Wallpaper';
                window.removeEventListener('wallpaper-status', handler);
            } else if (message === 'WALLPAPER_SET_FAILED' || message.startsWith('WALLPAPER_ERROR')) {
                this.showStatus('Failed to set wallpaper on child device', 'error');
                setBtn.disabled = false;
                setBtn.textContent = 'Set Wallpaper';
                window.removeEventListener('wallpaper-status', handler);
            }
        };

        window.addEventListener('wallpaper-status', handler);
    }

    clearPreview() {
        this.selectedFile = null;
        const previewContainer = this.container.querySelector('#wallpaper-preview');
        const previewImg = this.container.querySelector('#wallpaper-preview-img');
        const placeholder = this.container.querySelector('#wallpaper-placeholder');
        const setBtn = this.container.querySelector('#set-wallpaper-btn');
        const fileInput = this.container.querySelector('#wallpaper-file-input');

        if (previewContainer) previewContainer.style.display = 'none';
        if (previewImg) previewImg.src = '';
        if (placeholder) placeholder.style.display = 'flex';
        if (setBtn) setBtn.style.display = 'none';
        if (fileInput) fileInput.value = '';
    }

    showStatus(message, type) {
        const statusDiv = this.container.querySelector('#wallpaper-status');
        if (!statusDiv) return;

        statusDiv.textContent = message;
        
        switch (type) {
            case 'success':
                statusDiv.style.color = '#4ade80';
                break;
            case 'error':
                statusDiv.style.color = '#ef4444';
                break;
            case 'warning':
                statusDiv.style.color = '#fbbf24';
                break;
            case 'info':
            default:
                statusDiv.style.color = '#60a5fa';
                break;
        }

        // Clear status after 5 seconds for success/info
        if (type === 'success' || type === 'info') {
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 5000);
        }
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.WallpaperFeature = WallpaperFeature;
