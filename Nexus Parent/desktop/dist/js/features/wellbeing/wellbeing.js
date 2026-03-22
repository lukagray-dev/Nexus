/**
 * Wellbeing (Usage Monitoring) Feature Module
 * Displays app usage statistics, screen time, and device usage patterns
 */

class WellbeingFeature {
    constructor(container) {
        this.container = container;
        this.wellbeingData = null;
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

        // Listen for wellbeing data
        this.listenForWellbeingData();

        // Don't request data automatically - user will click button
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/wellbeing/wellbeing.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Wellbeing template:', error);
            return '<div>Failed to load Wellbeing feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/wellbeing/wellbeing.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Wellbeing styles:', error);
        }
    }

    setupEventListeners() {
        const requestBtn = this.container.querySelector('#wellbeing-request-btn');
        
        if (requestBtn) {
            requestBtn.addEventListener('click', () => this.requestWellbeingData());
        }
    }

    listenForWellbeingData() {
        // Listen for wellbeing data from connection manager
        window.addEventListener('wellbeing-data', (event) => {
            console.log('📊 Wellbeing data received:', event.detail);
            this.handleWellbeingData(event.detail);
        });
    }

    requestWellbeingData() {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const requestBtn = this.container.querySelector('#wellbeing-request-btn');
            if (requestBtn) {
                requestBtn.disabled = true;
                requestBtn.innerHTML = `
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
                    <span>Requesting...</span>
                `;
            }

            // Send command to request wellbeing data
            app.connectionManager.sendCommand('GET_WELLBEING_DATA');
            
            this.showStatus('Requesting usage data...', 'info');
            console.log('📊 Wellbeing data requested');

            // Timeout after 30 seconds
            setTimeout(() => {
                if (requestBtn && requestBtn.disabled) {
                    this.resetRequestButton();
                    this.showStatus('Request timeout', 'error');
                }
            }, 30000);

        } catch (error) {
            console.error('Failed to request wellbeing data:', error);
            this.showStatus('Failed to request data: ' + error.message, 'error');
            this.resetRequestButton();
        }
    }

    handleWellbeingData(data) {
        this.wellbeingData = data;
        this.resetRequestButton();

        if (data.error) {
            this.showStatus(`Error: ${data.reason || data.error}`, 'error');
            return;
        }

        // Update summary cards
        this.updateSummary(data);

        // Update categories
        this.updateCategories(data.categories || []);

        // Update apps list
        this.updateAppsList(data.apps || []);

        this.showStatus('Data updated successfully', 'success');
    }

    updateSummary(data) {
        // Total screen time
        const screenTimeEl = this.container.querySelector('#total-screen-time');
        if (screenTimeEl) {
            screenTimeEl.textContent = this.formatDuration(data.total_screen_time_ms || 0);
        }

        // Device unlocks
        const unlocksEl = this.container.querySelector('#device-unlocks');
        if (unlocksEl) {
            unlocksEl.textContent = data.device_unlocks || 0;
        }

        // Apps count
        const appsCountEl = this.container.querySelector('#apps-count');
        if (appsCountEl) {
            const apps = data.apps || [];
            appsCountEl.textContent = apps.length;
        }
    }

    updateCategories(categories) {
        const listContainer = this.container.querySelector('#categories-list');
        if (!listContainer) return;

        // Remove placeholder
        const placeholder = listContainer.querySelector('.list-placeholder');
        if (placeholder) placeholder.remove();

        // Clear existing items
        listContainer.innerHTML = '';

        if (categories.length === 0) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <circle cx="12" cy="12" r="10"></circle>
                        <polyline points="12 6 12 12 16 14"></polyline>
                    </svg>
                    <p>No category data</p>
                </div>
            `;
            return;
        }

        // Find max time for percentage calculation
        const maxTime = Math.max(...categories.map(c => c.screen_time_ms || 0));

        categories.forEach(category => {
            const percentage = maxTime > 0 ? (category.screen_time_ms / maxTime) * 100 : 0;
            const item = document.createElement('div');
            item.className = 'category-item';
            item.innerHTML = `
                <div class="category-icon">${this.getCategoryIcon(category.category)}</div>
                <div class="category-info">
                    <div class="category-name">${this.escapeHtml(category.category)}</div>
                    <div class="category-time">${this.formatDuration(category.screen_time_ms)}</div>
                </div>
                <div class="category-bar">
                    <div class="category-bar-fill" style="width: ${percentage}%"></div>
                </div>
            `;
            listContainer.appendChild(item);
        });
    }

    updateAppsList(apps) {
        const listContainer = this.container.querySelector('#apps-list');
        if (!listContainer) return;

        // Remove placeholder
        const placeholder = listContainer.querySelector('.list-placeholder');
        if (placeholder) placeholder.remove();

        // Clear existing items
        listContainer.innerHTML = '';

        if (apps.length === 0) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                        <circle cx="8.5" cy="8.5" r="1.5"></circle>
                        <polyline points="21 15 16 10 5 21"></polyline>
                    </svg>
                    <p>No apps data</p>
                </div>
            `;
            return;
        }

        // Show top 20 apps
        const topApps = apps.slice(0, 20);

        topApps.forEach(app => {
            const item = document.createElement('div');
            item.className = 'app-item';
            
            const iconHtml = app.icon_base64 
                ? `<img src="data:image/png;base64,${app.icon_base64}" alt="${app.app_name}" />`
                : `<div class="app-icon-placeholder">📱</div>`;
            
            item.innerHTML = `
                <div class="app-icon">${iconHtml}</div>
                <div class="app-info">
                    <div class="app-name">${this.escapeHtml(app.app_name)}</div>
                    <div class="app-package">${this.escapeHtml(app.package_name)}</div>
                </div>
                <div class="app-time">${this.formatDuration(app.screen_time_ms)}</div>
            `;
            listContainer.appendChild(item);
        });
    }

    getCategoryIcon(category) {
        const icons = {
            'Social': '💬',
            'Entertainment': '🎬',
            'Games': '🎮',
            'Productivity': '💼',
            'Communication': '📧',
            'Shopping': '🛒',
            'News': '📰',
            'Health': '❤️',
            'Education': '📚',
            'Other': '📱'
        };
        return icons[category] || '📱';
    }

    formatDuration(ms) {
        if (!ms || ms === 0) return '0m';
        
        const hours = Math.floor(ms / (1000 * 60 * 60));
        const minutes = Math.floor((ms % (1000 * 60 * 60)) / (1000 * 60));
        
        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        return `${minutes}m`;
    }

    resetRequestButton() {
        const requestBtn = this.container.querySelector('#wellbeing-request-btn');
        if (requestBtn) {
            requestBtn.disabled = false;
            requestBtn.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
                </svg>
                <span>Request Usage Data</span>
            `;
        }
    }

    showStatus(message, type) {
        const statusDiv = this.container.querySelector('#wellbeing-status');
        if (!statusDiv) return;

        statusDiv.textContent = message;
        
        switch (type) {
            case 'success':
                statusDiv.style.color = '#4ade80';
                break;
            case 'error':
                statusDiv.style.color = '#ef4444';
                break;
            case 'info':
            default:
                statusDiv.style.color = '#60a5fa';
                break;
        }

        if (type === 'success') {
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 3000);
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.WellbeingFeature = WellbeingFeature;
