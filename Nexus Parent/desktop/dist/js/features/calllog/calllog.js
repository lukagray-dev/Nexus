/**
 * Call Log Feature Module
 * Displays call logs from child device with search functionality
 */

// Global storage for Call Log data (persists across feature instances)
if (!window.calllogFeatureData) {
    window.calllogFeatureData = {
        logs: [],
        latestTimestamp: 0,
        isMonitoring: false
    };
}

class CalllogFeature {
    constructor(container) {
        this.container = container;
        this.searchTerm = '';
        this.confirmationTimeout = null;
        
        // Use global storage for persistence
        this.data = window.calllogFeatureData;
        
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

        // Listen for call log messages
        this.listenForLogs();

        // Restore previous logs if any
        this.renderLogs();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/calllog/calllog.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Call Log template:', error);
            return '<div>Failed to load Call Log feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/calllog/calllog.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Call Log styles:', error);
        }
    }

    setupEventListeners() {
        const playPauseBtn = this.container.querySelector('#calllog-play-pause-btn');
        const searchInput = this.container.querySelector('#calllog-search');

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.toggleMonitoring());
        }

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.searchTerm = e.target.value.toLowerCase();
                this.renderLogs();
            });
        }
    }

    listenForLogs() {
        // Listen for call log messages from connection manager
        window.addEventListener('calllog-message', (event) => {
            console.log('📞 Call log received:', event.detail);
            this.addLog(event.detail);
        });

        // Listen for confirmation messages
        window.addEventListener('calllog-confirmation', (event) => {
            const message = event.detail.message;
            console.log('📞 [CallLog] Received confirmation event:', message);
            
            if (message === 'CALLLOG_STARTED') {
                console.log('📞 [CallLog] Monitoring started confirmed');
                this.clearConfirmationTimeout();
                this.data.isMonitoring = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'CALLLOG_STOPPED') {
                console.log('📞 [CallLog] Monitoring stopped confirmed');
                this.clearConfirmationTimeout();
                this.data.isMonitoring = false;
                this.updateButton(false);
                this.hideLoading();
            }
        });
    }

    toggleMonitoring() {
        if (this.data.isMonitoring) {
            this.stopMonitoring();
        } else {
            this.startMonitoring();
        }
    }

    startMonitoring() {
        console.log('📞 Starting call log monitoring');

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();

            // Send CALLLOG_ON command with timestamp
            const command = {
                cmd: 'CALLLOG_ON',
                since: this.data.latestTimestamp
            };
            app.connectionManager.sendCommand(JSON.stringify(command));

            console.log('✅ Call log monitoring command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [CallLog] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Call log monitoring start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to start call log monitoring:', error);
            this.hideLoading();
            this.showError('Failed to start call log monitoring: ' + error.message);
        }
    }

    stopMonitoring() {
        console.log('📞 Stopping call log monitoring');

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();

            // Send CALLLOG_OFF command
            app.connectionManager.sendCommand('CALLLOG_OFF');

            console.log('✅ Call log monitoring stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [CallLog] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Call log monitoring stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to stop call log monitoring:', error);
            this.hideLoading();
        }
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [CallLog] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#calllog-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#calllog-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.data.isMonitoring) {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="display:none;">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="display:block;">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            } else {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="display:block;">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="display:none;">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            }
        }
    }

    updateButton(isActive) {
        const playPauseBtn = this.container.querySelector('#calllog-play-pause-btn');
        if (!playPauseBtn) return;

        const playIcon = playPauseBtn.querySelector('.play-icon');
        const pauseIcon = playPauseBtn.querySelector('.pause-icon');

        if (!playIcon || !pauseIcon) return;

        if (isActive) {
            playIcon.style.display = 'none';
            pauseIcon.style.display = 'block';
        } else {
            playIcon.style.display = 'block';
            pauseIcon.style.display = 'none';
        }
    }

    addLog(log) {
        // Ensure data.logs exists
        if (!this.data.logs) {
            this.data.logs = [];
        }
        
        // Add to global logs array
        this.data.logs.push(log);

        // Update latest timestamp
        if (log.timestamp > this.data.latestTimestamp) {
            this.data.latestTimestamp = log.timestamp;
        }

        // Re-render logs
        this.renderLogs();
    }

    renderLogs() {
        const listContainer = this.container.querySelector('#calllog-list');
        if (!listContainer) return;

        // Ensure data.logs exists
        if (!this.data.logs) {
            this.data.logs = [];
        }

        // Filter logs based on search term
        let filtered = this.data.logs;
        if (this.searchTerm) {
            filtered = this.data.logs.filter(log => {
                const number = (log.number || '').toLowerCase();
                const name = (log.name || '').toLowerCase();
                return number.includes(this.searchTerm) || name.includes(this.searchTerm);
            });
        }

        // Clear list
        listContainer.innerHTML = '';

        // Show placeholder if no logs
        if (filtered.length === 0) {
            const placeholder = document.createElement('div');
            placeholder.className = 'list-placeholder';
            placeholder.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                </svg>
                <p>${this.searchTerm ? 'No logs match search' : 'No call logs'}</p>
            `;
            listContainer.appendChild(placeholder);
            
            // Re-add play/pause button
            const playPauseBtn = document.createElement('button');
            playPauseBtn.className = 'play-pause-btn';
            playPauseBtn.id = 'calllog-play-pause-btn';
            playPauseBtn.title = 'Play/Pause';
            playPauseBtn.innerHTML = `
                <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.isMonitoring ? 'display:none;' : ''}">
                    <polygon points="5 3 19 12 5 21 5 3"></polygon>
                </svg>
                <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.isMonitoring ? '' : 'display:none;'}">
                    <rect x="6" y="4" width="4" height="16"></rect>
                    <rect x="14" y="4" width="4" height="16"></rect>
                </svg>
            `;
            playPauseBtn.addEventListener('click', () => this.toggleMonitoring());
            listContainer.appendChild(playPauseBtn);
            return;
        }

        // Sort by timestamp (newest first)
        filtered.sort((a, b) => b.timestamp - a.timestamp);

        // Render logs
        filtered.forEach(log => {
            const item = document.createElement('div');
            item.className = 'calllog-item';
            
            const icon = log.call_type === 'incoming' ? '📥' : 
                        log.call_type === 'outgoing' ? '📤' : '❗';
            const date = new Date(log.timestamp);
            const timeStr = date.toLocaleString();
            const duration = this.formatDuration(log.duration || 0);
            const name = log.name || 'Unknown';

            item.innerHTML = `
                <div class="calllog-item-header">
                    <div class="calllog-item-number">${icon} ${this.escapeHtml(log.number || 'Unknown')}</div>
                    <div class="calllog-item-time">${timeStr}</div>
                </div>
                <div class="calllog-item-type">
                    ${this.escapeHtml(name)} • ${duration} • ${this.formatCallType(log.call_type)}
                </div>
            `;

            // Add click handler to show detail modal
            item.addEventListener('click', () => this.showDetailModal(log));

            listContainer.appendChild(item);
        });

        // Re-add play/pause button
        const playPauseBtn = document.createElement('button');
        playPauseBtn.className = 'play-pause-btn';
        playPauseBtn.id = 'calllog-play-pause-btn';
        playPauseBtn.title = 'Play/Pause';
        playPauseBtn.innerHTML = `
            <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.isMonitoring ? 'display:none;' : ''}">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
            <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.isMonitoring ? '' : 'display:none;'}">
                <rect x="6" y="4" width="4" height="16"></rect>
                <rect x="14" y="4" width="4" height="16"></rect>
            </svg>
        `;
        playPauseBtn.addEventListener('click', () => this.toggleMonitoring());
        listContainer.appendChild(playPauseBtn);
    }

    formatDuration(seconds) {
        if (seconds < 60) {
            return `${seconds}s`;
        }
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${minutes}m ${secs}s`;
    }

    formatCallType(type) {
        switch (type) {
            case 'incoming': return 'Incoming';
            case 'outgoing': return 'Outgoing';
            case 'missed': return 'Missed';
            default: return type || 'Unknown';
        }
    }

    showDetailModal(log) {
        // Hide the list and header
        const listContainer = this.container.querySelector('#calllog-list');
        const header = this.container.querySelector('.calllog-header');
        const playPauseBtn = this.container.querySelector('#calllog-play-pause-btn');
        
        if (listContainer) listContainer.style.display = 'none';
        if (header) header.style.display = 'none';
        
        // Morph play/pause button into detail badge
        if (playPauseBtn) {
            playPauseBtn.classList.add('morphing-to-badge');
        }
        
        // Create detail view
        const detailView = document.createElement('div');
        detailView.className = 'detail-view';
        detailView.id = 'calllog-detail-view';
        
        const date = new Date(log.timestamp);
        const timeStr = date.toLocaleString();
        const duration = this.formatDuration(log.duration || 0);
        const name = log.name || 'Unknown';
        
        // SVG icons for call types
        let iconSvg = '';
        let callTypeLabel = '';
        if (log.call_type === 'incoming') {
            callTypeLabel = 'Incoming';
            iconSvg = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                    <polyline points="16 2 16 8 22 8"></polyline>
                </svg>
            `;
        } else if (log.call_type === 'outgoing') {
            callTypeLabel = 'Outgoing';
            iconSvg = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                    <line x1="22" y1="2" x2="16" y2="8"></line>
                    <polyline points="22 8 22 2 16 2"></polyline>
                </svg>
            `;
        } else {
            callTypeLabel = 'Missed';
            iconSvg = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                    <line x1="23" y1="1" x2="17" y2="7"></line>
                    <line x1="17" y1="1" x2="23" y2="7"></line>
                </svg>
            `;
        }
        
        detailView.innerHTML = `
            <div class="detail-header">
                <button class="detail-back-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M19 12H5M12 19l-7-7 7-7"/>
                    </svg>
                    Back
                </button>
                <h3>Call Details</h3>
            </div>
            <div class="detail-body">
                <div class="detail-info-grid">
                    <div class="detail-info-item">
                        <div class="detail-info-label">Phone Number</div>
                        <div class="detail-info-value">${this.escapeHtml(log.number || 'Unknown')}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Contact Name</div>
                        <div class="detail-info-value">${this.escapeHtml(name)}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Date & Time</div>
                        <div class="detail-info-value">${timeStr}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Duration</div>
                        <div class="detail-info-value">${duration}</div>
                    </div>
                </div>
                <div class="detail-floating-badge">
                    <div class="detail-badge-icon">${iconSvg}</div>
                    <div class="detail-badge-label">${callTypeLabel}</div>
                </div>
            </div>
        `;
        
        // Add to container
        this.container.querySelector('.calllog-content').appendChild(detailView);
        
        // Trigger badge animation after a brief delay
        setTimeout(() => {
            const badge = detailView.querySelector('.detail-floating-badge');
            if (badge) {
                badge.classList.add('badge-visible');
            }
            // Hide the morphing button
            if (playPauseBtn) {
                playPauseBtn.style.opacity = '0';
            }
        }, 50);
        
        // Back button handler
        const backBtn = detailView.querySelector('.detail-back-btn');
        backBtn.addEventListener('click', () => {
            // Reverse animation
            const badge = detailView.querySelector('.detail-floating-badge');
            if (badge) {
                badge.classList.remove('badge-visible');
            }
            
            // Show and restore play/pause button
            if (playPauseBtn) {
                playPauseBtn.style.opacity = '1';
                playPauseBtn.classList.remove('morphing-to-badge');
            }
            
            setTimeout(() => {
                detailView.remove();
                if (listContainer) listContainer.style.display = 'flex';
                if (header) header.style.display = 'flex';
            }, 300);
        });
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showError(message) {
        const listContainer = this.container.querySelector('#calllog-list');
        if (listContainer) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3; color: #ef4444;">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="15" y1="9" x2="9" y2="15"></line>
                        <line x1="9" y1="9" x2="15" y2="15"></line>
                    </svg>
                    <p style="color: #ef4444;">${message}</p>
                </div>
            `;
        }
    }

    destroy() {
        if (this.data.isMonitoring) {
            this.stopMonitoring();
        }
        // Note: We don't clear the global data, so it persists
    }
}

// Export to window for dynamic loading
window.CalllogFeature = CalllogFeature;
