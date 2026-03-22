/**
 * SMS Feature Module
 * Displays SMS messages from child device with search functionality
 */

// Global storage for SMS data (persists across feature instances)
if (!window.smsFeatureData) {
    window.smsFeatureData = {
        messages: [],
        latestTimestamp: 0,
        isMonitoring: false
    };
}

class SmsFeature {
    constructor(container) {
        this.container = container;
        this.searchTerm = '';
        this.confirmationTimeout = null;
        
        // Use global storage for persistence
        this.data = window.smsFeatureData;
        
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

        // Listen for SMS messages
        this.listenForMessages();

        // Restore previous messages if any
        this.renderMessages();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/sms/sms.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load SMS template:', error);
            return '<div>Failed to load SMS feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/sms/sms.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load SMS styles:', error);
        }
    }

    setupEventListeners() {
        const playPauseBtn = this.container.querySelector('#sms-play-pause-btn');
        const searchInput = this.container.querySelector('#sms-search');

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.toggleMonitoring());
        }

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.searchTerm = e.target.value.toLowerCase();
                this.renderMessages();
            });
        }
    }

    listenForMessages() {
        // Listen for SMS messages from connection manager
        window.addEventListener('sms-message', (event) => {
            console.log('📨 SMS message received:', event.detail);
            this.addMessage(event.detail);
        });

        // Listen for confirmation messages
        window.addEventListener('sms-confirmation', (event) => {
            const message = event.detail.message;
            console.log('📨 [SMS] Received confirmation event:', message);
            
            if (message === 'SMS_STARTED') {
                console.log('📨 [SMS] Monitoring started confirmed');
                this.clearConfirmationTimeout();
                this.data.isMonitoring = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'SMS_STOPPED') {
                console.log('📨 [SMS] Monitoring stopped confirmed');
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
        console.log('📨 Starting SMS monitoring');

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

            // Send SMS_ON command with timestamp
            const command = {
                cmd: 'SMS_ON',
                since: this.data.latestTimestamp
            };
            app.connectionManager.sendCommand(JSON.stringify(command));

            console.log('✅ SMS monitoring command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [SMS] Confirmation timeout');
                    this.hideLoading();
                    this.showError('SMS monitoring start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to start SMS monitoring:', error);
            this.hideLoading();
            this.showError('Failed to start SMS monitoring: ' + error.message);
        }
    }

    stopMonitoring() {
        console.log('📨 Stopping SMS monitoring');

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

            // Send SMS_OFF command
            app.connectionManager.sendCommand('SMS_OFF');

            console.log('✅ SMS monitoring stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [SMS] Confirmation timeout');
                    this.hideLoading();
                    this.showError('SMS monitoring stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to stop SMS monitoring:', error);
            this.hideLoading();
        }
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [SMS] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#sms-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#sms-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#sms-play-pause-btn');
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

    addMessage(message) {
        // Ensure data.messages exists
        if (!this.data.messages) {
            this.data.messages = [];
        }
        
        // Add to global messages array
        this.data.messages.push(message);

        // Update latest timestamp
        if (message.timestamp > this.data.latestTimestamp) {
            this.data.latestTimestamp = message.timestamp;
        }

        // Re-render messages
        this.renderMessages();
    }

    renderMessages() {
        const listContainer = this.container.querySelector('#sms-list');
        if (!listContainer) return;

        // Ensure data.messages exists
        if (!this.data.messages) {
            this.data.messages = [];
        }

        // Filter messages based on search term
        let filtered = this.data.messages;
        if (this.searchTerm) {
            filtered = this.data.messages.filter(msg => {
                const address = (msg.address || '').toLowerCase();
                const body = (msg.body || '').toLowerCase();
                return address.includes(this.searchTerm) || body.includes(this.searchTerm);
            });
        }

        // Clear list
        listContainer.innerHTML = '';

        // Show placeholder if no messages
        if (filtered.length === 0) {
            const placeholder = document.createElement('div');
            placeholder.className = 'list-placeholder';
            placeholder.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                </svg>
                <p>${this.searchTerm ? 'No messages match search' : 'No SMS messages'}</p>
            `;
            listContainer.appendChild(placeholder);
            
            // Re-add play/pause button
            const playPauseBtn = document.createElement('button');
            playPauseBtn.className = 'play-pause-btn';
            playPauseBtn.id = 'sms-play-pause-btn';
            playPauseBtn.title = 'Play/Pause';
            playPauseBtn.innerHTML = `
                <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.data.isMonitoring ? 'display:none;' : ''}">
                    <polygon points="5 3 19 12 5 21 5 3"></polygon>
                </svg>
                <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.data.isMonitoring ? '' : 'display:none;'}">
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

        // Render messages
        filtered.forEach(msg => {
            const item = document.createElement('div');
            item.className = 'sms-item';
            
            const icon = msg.sms_type === 'inbox' ? '📥' : '📤';
            const date = new Date(msg.timestamp);
            const timeStr = date.toLocaleString();

            item.innerHTML = `
                <div class="sms-item-header">
                    <div class="sms-item-sender">${icon} ${this.escapeHtml(msg.address || 'Unknown')}</div>
                    <div class="sms-item-time">${timeStr}</div>
                </div>
                <div class="sms-item-text">${this.escapeHtml(msg.body || '')}</div>
            `;

            // Add click handler to show detail modal
            item.addEventListener('click', () => this.showDetailModal(msg));

            listContainer.appendChild(item);
        });

        // Re-add play/pause button
        const playPauseBtn = document.createElement('button');
        playPauseBtn.className = 'play-pause-btn';
        playPauseBtn.id = 'sms-play-pause-btn';
        playPauseBtn.title = 'Play/Pause';
        playPauseBtn.innerHTML = `
            <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.data.isMonitoring ? 'display:none;' : ''}">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
            <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="${this.data.isMonitoring ? '' : 'display:none;'}">
                <rect x="6" y="4" width="4" height="16"></rect>
                <rect x="14" y="4" width="4" height="16"></rect>
            </svg>
        `;
        playPauseBtn.addEventListener('click', () => this.toggleMonitoring());
        listContainer.appendChild(playPauseBtn);
    }

    showDetailModal(message) {
        // Hide the list and header
        const listContainer = this.container.querySelector('#sms-list');
        const header = this.container.querySelector('.sms-header');
        const playPauseBtn = this.container.querySelector('#sms-play-pause-btn');
        
        if (listContainer) listContainer.style.display = 'none';
        if (header) header.style.display = 'none';
        
        // Morph play/pause button into detail badge
        if (playPauseBtn) {
            playPauseBtn.classList.add('morphing-to-badge');
        }
        
        // Create detail view
        const detailView = document.createElement('div');
        detailView.className = 'detail-view';
        detailView.id = 'sms-detail-view';
        
        const isReceived = message.sms_type === 'inbox';
        const date = new Date(message.timestamp);
        const timeStr = date.toLocaleString();
        
        // SVG icons for received/sent
        const iconSvg = isReceived ? `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                <polyline points="9 10 12 13 16 9"></polyline>
            </svg>
        ` : `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                <line x1="9" y1="10" x2="15" y2="10"></line>
                <line x1="12" y1="10" x2="15" y2="13"></line>
                <line x1="12" y1="10" x2="15" y2="7"></line>
            </svg>
        `;
        
        detailView.innerHTML = `
            <div class="detail-header">
                <button class="detail-back-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M19 12H5M12 19l-7-7 7-7"/>
                    </svg>
                    Back
                </button>
                <h3>Message Details</h3>
            </div>
            <div class="detail-body">
                <div class="detail-info-grid">
                    <div class="detail-info-item">
                        <div class="detail-info-label">${isReceived ? 'From' : 'To'}</div>
                        <div class="detail-info-value">${this.escapeHtml(message.address || 'Unknown')}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Date & Time</div>
                        <div class="detail-info-value">${timeStr}</div>
                    </div>
                </div>
                <div class="detail-message-section">
                    <div class="detail-message-label">Message</div>
                    <div class="detail-message-content">${this.escapeHtml(message.body || '')}</div>
                </div>
                <div class="detail-floating-badge">
                    <div class="detail-badge-icon">${iconSvg}</div>
                    <div class="detail-badge-label">${isReceived ? 'Received' : 'Sent'}</div>
                </div>
            </div>
        `;
        
        // Add to container
        this.container.querySelector('.sms-content').appendChild(detailView);
        
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
        const listContainer = this.container.querySelector('#sms-list');
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
window.SmsFeature = SmsFeature;
