/**
 * App Lock Monitor Feature Module
 * Displays unlock attempt alerts with selfies, manages PIN, and tracks statistics
 */

// Global storage for App Lock data (persists across feature instances)
if (!window.applockFeatureData) {
    window.applockFeatureData = {
        alerts: [],
        isEnabled: true,
        currentPin: '1234',
        searchKeyword: ''
    };
}

class ApplockFeature {
    constructor(container) {
        this.container = container;
        this.data = window.applockFeatureData;
        this.MAX_ALERTS = 500;
        this.loadFromStorage();
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

        // Listen for app lock alerts
        this.listenForAlerts();

        // Render existing alerts
        this.render();
        this.updateStats();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/applock/applock.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load AppLock template:', error);
            return '<div>Failed to load App Lock feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/applock/applock.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load AppLock styles:', error);
        }
    }

    setupEventListeners() {
        const searchInput = this.container.querySelector('#applock-search');
        const toggleBtn = this.container.querySelector('#applock-toggle-btn');
        const changePinBtn = this.container.querySelector('#applock-change-pin-btn');
        const refreshBtn = this.container.querySelector('#applock-refresh-btn');
        const clearBtn = this.container.querySelector('#applock-clear-btn');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.data.searchKeyword = e.target.value;
                this.render();
            });
        }

        if (toggleBtn) {
            toggleBtn.addEventListener('click', () => this.toggleLock());
            this.updateToggleButton();
        }

        if (changePinBtn) {
            changePinBtn.addEventListener('click', () => this.showChangePinDialog());
        }

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.requestStatus());
        }

        if (clearBtn) {
            clearBtn.addEventListener('click', () => this.clearAll());
        }
    }

    listenForAlerts() {
        // Listen for app lock alerts from connection manager
        window.addEventListener('applock-alert', (event) => {
            console.log('🔐 App lock alert received:', event.detail);
            this.appendAlert(event.detail);
        });

        // Listen for status updates
        window.addEventListener('applock-status', (event) => {
            console.log('� App lock status received:', event.detail);
            const data = event.detail;
            if (data.enabled !== undefined) {
                this.data.isEnabled = data.enabled;
                this.saveToStorage();
                this.updateToggleButton();
            }
        });
    }

    appendAlert(payload) {
        if (!payload) return;

        try {
            const alert = {
                id: payload.id || `alert-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                timestamp: typeof payload.timestamp === 'number' ? payload.timestamp : Date.now(),
                pinCorrect: !!payload.pinCorrect,
                failedAttempts: payload.failedAttempts || 0,
                isLockedOut: !!payload.isLockedOut,
                selfie: payload.selfie || null,
                deviceId: payload.deviceId || null
            };

            // Check for duplicates
            const isDuplicate = this.data.alerts.some(a =>
                Math.abs(a.timestamp - alert.timestamp) < 1000 &&
                a.pinCorrect === alert.pinCorrect
            );

            if (!isDuplicate) {
                this.data.alerts.push(alert);

                // Limit alerts
                if (this.data.alerts.length > this.MAX_ALERTS) {
                    this.data.alerts.splice(0, this.data.alerts.length - this.MAX_ALERTS);
                }

                this.saveToStorage();
                this.render();
                this.updateStats();

                // Show notification for failed attempts
                if (!alert.pinCorrect) {
                    const message = alert.isLockedOut
                        ? `Device locked out after ${alert.failedAttempts} failed attempts!`
                        : `Wrong PIN entered (${alert.failedAttempts} failed attempts)`;
                    this.showStatus(message, 'error');
                }
            }
        } catch (e) {
            console.error('Failed to append app lock alert:', e);
        }
    }

    render() {
        const listContainer = this.container.querySelector('#applock-list');
        if (!listContainer) return;

        const q = (this.data.searchKeyword || '').toLowerCase();
        listContainer.innerHTML = '';

        const items = this.data.alerts
            .slice()
            .sort((a, b) => b.timestamp - a.timestamp)
            .filter(a => {
                if (!q) return true;
                const status = a.pinCorrect ? 'success correct unlocked' : 'failed wrong';
                return status.includes(q) ||
                    new Date(a.timestamp).toLocaleString().toLowerCase().includes(q);
            });

        if (items.length === 0) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                        <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                    </svg>
                    <p>${q ? 'No matching alerts' : 'No unlock attempts yet'}</p>
                </div>
            `;
            return;
        }

        items.forEach(alert => {
            const card = this.createAlertCard(alert);
            listContainer.appendChild(card);
        });
    }

    createAlertCard(alert) {
        const card = document.createElement('div');
        card.className = `alert-card ${alert.pinCorrect ? 'success' : (alert.isLockedOut ? 'lockout' : 'failed')}`;

        let statusIcon, statusText, statusClass;
        if (alert.pinCorrect) {
            statusIcon = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>`;
            statusText = 'Unlocked Successfully';
            statusClass = 'success';
        } else if (alert.isLockedOut) {
            statusIcon = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;
            statusText = 'Device Locked Out';
            statusClass = 'lockout';
        } else {
            statusIcon = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`;
            statusText = 'Wrong PIN';
            statusClass = 'failed';
        }

        let selfieHtml;
        if (alert.selfie) {
            selfieHtml = `
                <div class="alert-selfie" data-alert-id="${alert.id}">
                    <img src="data:image/jpeg;base64,${alert.selfie}" alt="Selfie" />
                    <div class="selfie-overlay">Click to view</div>
                </div>
            `;
        } else {
            selfieHtml = `
                <div class="alert-selfie no-selfie">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="1" y1="1" x2="23" y2="23"></line>
                        <path d="M21 21H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h3m3-3h6l2 3h4a2 2 0 0 1 2 2v9.34m-7.72-2.06a4 4 0 1 1-5.56-5.56"></path>
                    </svg>
                </div>
            `;
        }

        card.innerHTML = `
            <div class="alert-header">
                <div class="alert-status ${statusClass}">
                    ${statusIcon}
                    <span>${statusText}</span>
                </div>
                <div class="alert-time">${new Date(alert.timestamp).toLocaleString()}</div>
            </div>
            <div class="alert-body">
                ${selfieHtml}
                <div class="alert-info">
                    <div class="alert-detail">
                        <strong>Failed Attempts:</strong> ${alert.failedAttempts}
                    </div>
                    <div class="alert-detail">
                        <strong>Status:</strong> ${alert.isLockedOut ? 'Locked Out' : 'Active'}
                    </div>
                </div>
            </div>
        `;

        // Add click handler for selfie if it exists
        if (alert.selfie) {
            const selfieEl = card.querySelector('.alert-selfie');
            if (selfieEl) {
                selfieEl.addEventListener('click', () => this.showSelfieModal(alert.id));
            }
        }

        return card;
    }

    showSelfieModal(alertId) {
        const alert = this.data.alerts.find(a => a.id === alertId);
        if (!alert || !alert.selfie) return;

        // Hide the list, stats, search, and header
        const listContainer = this.container.querySelector('#applock-list');
        const statsBar = this.container.querySelector('#applock-stats');
        const searchContainer = this.container.querySelector('.search-container');
        const header = this.container.querySelector('.applock-header');
        const controls = this.container.querySelector('.applock-controls');
        
        if (listContainer) listContainer.style.display = 'none';
        if (statsBar) statsBar.style.display = 'none';
        if (searchContainer) searchContainer.style.display = 'none';
        if (header) header.style.display = 'none';
        if (controls) controls.style.display = 'none';
        
        // Create detail view
        const detailView = document.createElement('div');
        detailView.className = 'detail-view';
        detailView.id = 'applock-detail-view';
        
        const statusIcon = alert.pinCorrect
            ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>`
            : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`;
        const statusText = alert.pinCorrect ? 'Successful Unlock' : 'Failed Attempt';
        const statusClass = alert.pinCorrect ? 'success' : (alert.isLockedOut ? 'lockout' : 'failed');
        
        detailView.innerHTML = `
            <div class="detail-header">
                <button class="detail-back-btn">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M19 12H5M12 19l-7-7 7-7"/>
                    </svg>
                    Back
                </button>
                <h3>Alert Details</h3>
            </div>
            <div class="detail-body">
                <div class="detail-selfie-container">
                    <img src="data:image/jpeg;base64,${alert.selfie}" alt="Selfie" class="detail-selfie-image" />
                </div>
                <div class="detail-info-grid">
                    <div class="detail-info-item">
                        <div class="detail-info-label">Status</div>
                        <div class="detail-info-value alert-status ${statusClass}">
                            ${statusIcon}
                            <span>${statusText}</span>
                        </div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Date & Time</div>
                        <div class="detail-info-value">${new Date(alert.timestamp).toLocaleString()}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Failed Attempts</div>
                        <div class="detail-info-value">${alert.failedAttempts}</div>
                    </div>
                    <div class="detail-info-item">
                        <div class="detail-info-label">Device Status</div>
                        <div class="detail-info-value">${alert.isLockedOut ? 'Locked Out' : 'Active'}</div>
                    </div>
                </div>
            </div>
        `;
        
        // Add to container
        this.container.querySelector('.applock-content').appendChild(detailView);
        
        // Back button handler
        const backBtn = detailView.querySelector('.detail-back-btn');
        backBtn.addEventListener('click', () => {
            detailView.remove();
            if (listContainer) listContainer.style.display = 'flex';
            if (statsBar) statsBar.style.display = 'grid';
            if (searchContainer) searchContainer.style.display = 'block';
            if (header) header.style.display = 'flex';
            if (controls) controls.style.display = 'grid';
        });
    }

    updateStats() {
        const statsContainer = this.container.querySelector('#applock-stats');
        if (!statsContainer) return;

        const total = this.data.alerts.length;
        const successful = this.data.alerts.filter(a => a.pinCorrect).length;
        const failed = total - successful;
        const lockouts = this.data.alerts.filter(a => a.isLockedOut).length;

        statsContainer.innerHTML = `
            <div class="stat-item">
                <div class="stat-value">${total}</div>
                <div class="stat-label">TOTAL</div>
            </div>
            <div class="stat-item success">
                <div class="stat-value">${successful}</div>
                <div class="stat-label">SUCCESSFUL</div>
            </div>
            <div class="stat-item failed">
                <div class="stat-value">${failed}</div>
                <div class="stat-label">FAILED</div>
            </div>
            <div class="stat-item lockout">
                <div class="stat-value">${lockouts}</div>
                <div class="stat-label">LOCKOUTS</div>
            </div>
        `;
    }

    toggleLock() {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const newState = !this.data.isEnabled;
            const command = JSON.stringify({
                cmd: 'SET_APP_LOCK_ENABLED',
                enabled: newState
            });

            app.connectionManager.sendCommand(command);

            this.data.isEnabled = newState;
            this.saveToStorage();
            this.updateToggleButton();

            this.showStatus(`App lock ${newState ? 'enabled' : 'disabled'}`, 'success');
            console.log(`🔐 App lock ${newState ? 'enabled' : 'disabled'}`);

        } catch (error) {
            console.error('Failed to toggle lock:', error);
            this.showStatus('Failed to toggle lock: ' + error.message, 'error');
        }
    }

    updateToggleButton() {
        const toggleBtn = this.container.querySelector('#applock-toggle-btn');
        if (!toggleBtn) return;

        if (this.data.isEnabled) {
            toggleBtn.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <span>Enabled</span>
            `;
            toggleBtn.classList.add('enabled');
            toggleBtn.classList.remove('disabled');
        } else {
            toggleBtn.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 5-5 5 5 0 0 1 5 5"></path>
                </svg>
                <span>Disabled</span>
            `;
            toggleBtn.classList.add('disabled');
            toggleBtn.classList.remove('enabled');
        }
    }

    showChangePinDialog() {
        const existing = document.getElementById('applock-pin-dialog');
        if (existing) existing.remove();

        const dialog = document.createElement('div');
        dialog.id = 'applock-pin-dialog';
        dialog.className = 'pin-dialog-overlay';
        dialog.innerHTML = `
            <div class="pin-dialog">
                <div class="pin-dialog-header">
                    <div class="pin-dialog-title">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"></path>
                        </svg>
                        <span>Change PIN</span>
                    </div>
                    <button class="pin-dialog-close">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                    </button>
                </div>
                <p class="pin-dialog-description">Enter a new 4-digit PIN for the child's app lock.</p>
                <div class="pin-input-group">
                    <input type="password" id="new-pin" maxlength="4" placeholder="New PIN" class="pin-input" />
                    <input type="password" id="confirm-pin" maxlength="4" placeholder="Confirm PIN" class="pin-input" />
                </div>
                <div id="pin-error" class="pin-error hidden"></div>
                <div class="pin-actions">
                    <button id="pin-cancel" class="pin-btn pin-btn-secondary">Cancel</button>
                    <button id="pin-submit" class="pin-btn pin-btn-primary">Change PIN</button>
                </div>
            </div>
        `;

        document.body.appendChild(dialog);
        dialog.querySelector('#new-pin').focus();

        dialog.onclick = (e) => {
            if (e.target === dialog) dialog.remove();
        };

        dialog.querySelector('.pin-dialog-close').onclick = () => dialog.remove();
        dialog.querySelector('#pin-cancel').onclick = () => dialog.remove();
        dialog.querySelector('#pin-submit').onclick = () => this.submitPinChange(dialog);

        // Only allow numeric input
        dialog.querySelectorAll('.pin-input').forEach(input => {
            input.addEventListener('input', (e) => {
                e.target.value = e.target.value.replace(/[^0-9]/g, '');
            });
            input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    this.submitPinChange(dialog);
                }
            });
        });
    }

    submitPinChange(dialog) {
        const newPin = dialog.querySelector('#new-pin').value;
        const confirmPin = dialog.querySelector('#confirm-pin').value;
        const errorEl = dialog.querySelector('#pin-error');

        if (!/^\d{4}$/.test(newPin)) {
            errorEl.textContent = 'PIN must be exactly 4 digits';
            errorEl.classList.remove('hidden');
            return;
        }

        if (newPin !== confirmPin) {
            errorEl.textContent = 'PINs do not match';
            errorEl.classList.remove('hidden');
            return;
        }

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = JSON.stringify({
                cmd: 'SET_APP_LOCK_PIN',
                pin: newPin
            });

            app.connectionManager.sendCommand(command);

            this.data.currentPin = newPin;
            this.saveToStorage();

            dialog.remove();
            this.showStatus('PIN change command sent to device', 'success');
            console.log('🔐 PIN changed successfully');

        } catch (error) {
            console.error('Failed to change PIN:', error);
            errorEl.textContent = 'Failed to send PIN change. Check connection.';
            errorEl.classList.remove('hidden');
        }
    }

    requestStatus() {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = JSON.stringify({ cmd: 'GET_APP_LOCK_STATUS' });
            app.connectionManager.sendCommand(command);

            this.showStatus('Requesting app lock status...', 'info');
            console.log('🔐 Status requested');

        } catch (error) {
            console.error('Failed to request status:', error);
            this.showStatus('Failed to request status: ' + error.message, 'error');
        }
    }

    clearAll() {
        if (!confirm('Clear all app lock alerts? This cannot be undone.')) {
            return;
        }

        this.data.alerts = [];
        this.data.searchKeyword = '';
        this.saveToStorage();
        this.render();
        this.updateStats();

        this.showStatus('All alerts cleared', 'success');
        console.log('🔐 All alerts cleared');
    }

    loadFromStorage() {
        try {
            const stored = localStorage.getItem('nexus_applock_data');
            if (stored) {
                const parsed = JSON.parse(stored);
                this.data.alerts = parsed.alerts || [];
                this.data.isEnabled = parsed.isEnabled !== false;
                this.data.currentPin = parsed.currentPin || '1234';
                console.log(`✅ Loaded ${this.data.alerts.length} app lock alerts from storage`);
            }
        } catch (e) {
            console.error('Failed to load app lock data from storage:', e);
        }
    }

    saveToStorage() {
        try {
            localStorage.setItem('nexus_applock_data', JSON.stringify({
                alerts: this.data.alerts,
                isEnabled: this.data.isEnabled,
                currentPin: this.data.currentPin
            }));
        } catch (e) {
            console.error('Failed to save app lock data to storage:', e);
        }
    }

    showStatus(message, type) {
        const statusDiv = this.container.querySelector('#applock-status');
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

        if (type === 'success' || type === 'info') {
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 3000);
        }
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.ApplockFeature = ApplockFeature;
