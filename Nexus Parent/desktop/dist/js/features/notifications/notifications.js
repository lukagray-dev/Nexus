/**
 * Notifications Feature Module
 * Displays real-time notifications from child device with command-based control
 */

// Global storage for Notification data (persists across feature instances)
if (!window.notificationFeatureData) {
    window.notificationFeatureData = {
        isActive: false,
        notifications: [],
        filteredNotifications: []
    };
}

class NotificationsFeature {
    constructor(container) {
        this.container = container;
        this.confirmationTimeout = null;
        
        // Use global storage for persistence
        this.data = window.notificationFeatureData;
        
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

        // Listen for notification updates
        this.listenForNotifications();

        // Restore state if needed
        if (this.data.isActive) {
            this.updateButton(true);
            this.renderNotifications();
        }
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/notifications/notifications.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Notifications template:', error);
            return '<div>Failed to load Notifications feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/notifications/notifications.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Notifications styles:', error);
        }
    }

    setupEventListeners() {
        const searchInput = this.container.querySelector('#notifications-search');
        const playPauseBtn = this.container.querySelector('#notifications-play-pause-btn');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.filterNotifications(e.target.value));
        }

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.togglePlayPause());
        }
    }

    listenForNotifications() {
        // Listen for notification updates from connection manager
        window.addEventListener('notification-update', (event) => {
            console.log('🔔 Notification update received:', event.detail);
            this.addNotification(event.detail);
        });

        // Listen for confirmation messages
        window.addEventListener('notification-confirmation', (event) => {
            const message = event.detail.message;
            console.log('🔔 [Notification] Received confirmation event:', message);
            
            if (message === 'NOTIFICATION_STARTED') {
                console.log('🔔 [Notification] Monitoring started confirmed');
                this.clearConfirmationTimeout();
                this.data.isActive = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'NOTIFICATION_STOPPED') {
                console.log('🔔 [Notification] Monitoring stopped confirmed');
                this.clearConfirmationTimeout();
                this.data.isActive = false;
                this.updateButton(false);
                this.hideLoading();
            }
        });
    }

    togglePlayPause() {
        if (this.data.isActive) {
            this.stopMonitoring();
        } else {
            this.startMonitoring();
        }
    }

    startMonitoring() {
        console.log('🔔 Starting notification monitoring');

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

            // Send NOTIFICATION_ON command
            app.connectionManager.sendCommand('NOTIFICATION_ON');

            console.log('✅ Notification monitoring command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Notification] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Notification monitoring start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to start notification monitoring:', error);
            this.hideLoading();
            this.showError('Failed to start notification monitoring: ' + error.message);
        }
    }

    stopMonitoring() {
        console.log('🔔 Stopping notification monitoring');

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

            // Send NOTIFICATION_OFF command
            app.connectionManager.sendCommand('NOTIFICATION_OFF');

            console.log('✅ Notification monitoring stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Notification] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Notification monitoring stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to stop notification monitoring:', error);
            this.hideLoading();
        }
    }

    addNotification(notification) {
        // Add to global storage
        this.data.notifications.unshift(notification);
        
        // Keep only last 500 notifications
        if (this.data.notifications.length > 500) {
            this.data.notifications = this.data.notifications.slice(0, 500);
        }

        // Update filtered list
        const searchInput = this.container.querySelector('#notifications-search');
        if (searchInput && searchInput.value) {
            this.filterNotifications(searchInput.value);
        } else {
            this.data.filteredNotifications = [...this.data.notifications];
        }

        // Re-render
        this.renderNotifications();
    }

    filterNotifications(query) {
        const lowerQuery = query.toLowerCase();
        
        this.data.filteredNotifications = this.data.notifications.filter(notif => {
            const appName = (notif.body?.appName || '').toLowerCase();
            const title = (notif.body?.title || '').toLowerCase();
            const text = (notif.body?.text || '').toLowerCase();
            
            return appName.includes(lowerQuery) || 
                   title.includes(lowerQuery) || 
                   text.includes(lowerQuery);
        });

        this.renderNotifications();
    }

    renderNotifications() {
        const listContainer = this.container.querySelector('#notifications-list');
        if (!listContainer) return;

        // Remove placeholder if exists
        const placeholder = listContainer.querySelector('.list-placeholder');
        if (placeholder) {
            placeholder.remove();
        }

        // Clear existing items (except play/pause button)
        const existingItems = listContainer.querySelectorAll('.notification-item');
        existingItems.forEach(item => item.remove());

        const notifications = this.data.filteredNotifications.length > 0 
            ? this.data.filteredNotifications 
            : this.data.notifications;

        if (notifications.length === 0) {
            // Show placeholder
            const placeholderHtml = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                        <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                    </svg>
                    <p>No notifications</p>
                </div>
            `;
            listContainer.insertAdjacentHTML('afterbegin', placeholderHtml);
            return;
        }

        // Render notification items
        notifications.forEach((notif, index) => {
            const item = this.createNotificationItem(notif, index);
            listContainer.insertBefore(item, listContainer.querySelector('.play-pause-btn'));
        });
    }

    createNotificationItem(notif, index) {
        const div = document.createElement('div');
        div.className = 'notification-item';
        
        const appName = notif.body?.appName || 'Unknown App';
        const title = notif.body?.title || '';
        const text = notif.body?.text || '';
        const time = this.formatTime(notif.ts || Date.now());
        
        div.innerHTML = `
            <div class="notification-item-header">
                <div class="notification-item-app">${this.escapeHtml(appName)}</div>
                <div class="notification-item-time">${time}</div>
            </div>
            <div class="notification-item-title">${this.escapeHtml(title)}</div>
            <div class="notification-item-text">${this.escapeHtml(text)}</div>
        `;

        div.addEventListener('click', () => this.showNotificationDetail(notif));

        return div;
    }

    showNotificationDetail(notif) {
        // TODO: Implement detail view similar to SMS
        console.log('Show notification detail:', notif);
    }

    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
        
        return date.toLocaleDateString();
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
            console.log('✅ [Notification] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#notifications-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#notifications-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.data.isActive) {
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
        const playPauseBtn = this.container.querySelector('#notifications-play-pause-btn');
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

    showError(message) {
        console.error('Notification error:', message);
        // TODO: Show error in UI
    }

    destroy() {
        if (this.data.isActive) {
            this.stopMonitoring();
        }
    }
}

// Export to window for dynamic loading
window.NotificationsFeature = NotificationsFeature;
