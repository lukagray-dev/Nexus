/**
 * UI Manager
 * Handles UI state, screen transitions, and UI-related interactions
 */

class UIManager {
    constructor(app) {
        this.app = app;
        this.elements = {};
        this.dustAnimation = null;
    }

    /**
     * Cache UI elements
     */
    cacheElements() {
        // Screen elements
        this.elements.connectScreen = document.getElementById('connect-screen');
        this.elements.dashboardScreen = document.getElementById('dashboard-screen');
        this.elements.childIdInput = document.getElementById('child-id-input');
        this.elements.connectBtn = document.getElementById('connect-btn');
        this.elements.connectFeedback = document.getElementById('connect-feedback');
        
        // Top bar elements
        this.elements.aiBtn = document.getElementById('ai-btn');
        this.elements.profileBtn = document.getElementById('profile-btn');
        this.elements.profileDropdown = document.getElementById('profile-dropdown');
        this.elements.disconnectBtn = document.getElementById('disconnect-btn');
        this.elements.deviceIdDisplay = document.getElementById('device-id-display');
        
        // Subscription dropdown elements
        this.elements.subscriptionToggleBtn = document.getElementById('subscription-toggle-btn');
        this.elements.subscriptionDropdown = document.getElementById('subscription-dropdown');
        
        // Settings and account buttons
        this.elements.settingsBtn = document.getElementById('settings-btn');
        this.elements.reportIssueBtn = document.getElementById('report-issue-btn');
        this.elements.deleteAccountBtn = document.getElementById('delete-account-btn');
    }

    /**
     * Setup UI event listeners
     */
    setupEventListeners() {
        // Connect button
        if (this.elements.connectBtn) {
            this.elements.connectBtn.addEventListener('click', () => this.app.handleConnect());
        }

        // Enter key on input
        if (this.elements.childIdInput) {
            this.elements.childIdInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    this.app.handleConnect();
                }
            });

            // Format input (numbers only, max 12 digits)
            this.elements.childIdInput.addEventListener('input', (e) => {
                let value = e.target.value.replace(/\D/g, '');
                if (value.length > 12) {
                    value = value.substring(0, 12);
                }
                e.target.value = value;
                
                // Clear feedback on input
                if (this.elements.connectFeedback) {
                    this.elements.connectFeedback.textContent = '';
                }
            });

            // Auto-focus
            setTimeout(() => this.elements.childIdInput.focus(), 200);
        }

        // Disconnect button
        if (this.elements.disconnectBtn) {
            this.elements.disconnectBtn.addEventListener('click', () => this.app.dialogManager.showDisconnectDialog());
        }

        // AI Assistant button
        if (this.elements.aiBtn && this.app.aiSidebar) {
            this.elements.aiBtn.addEventListener('click', () => this.app.aiSidebar.toggle());
        }

        // Profile dropdown toggle
        if (this.elements.profileBtn && this.elements.profileDropdown) {
            this.elements.profileBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.elements.profileDropdown.classList.toggle('hidden');
            });

            // Close dropdown when clicking outside
            document.addEventListener('click', (e) => {
                if (this.elements.profileDropdown && !this.elements.profileDropdown.classList.contains('hidden')) {
                    if (!this.elements.profileDropdown.contains(e.target) && e.target !== this.elements.profileBtn) {
                        this.elements.profileDropdown.classList.add('hidden');
                    }
                }
            });
        }

        // Subscription dropdown toggle
        if (this.elements.subscriptionToggleBtn && this.elements.subscriptionDropdown) {
            this.elements.subscriptionToggleBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.elements.subscriptionDropdown.classList.toggle('hidden');
                this.elements.subscriptionToggleBtn.classList.toggle('open');
            });

            // Close dropdown when clicking outside
            document.addEventListener('click', (e) => {
                if (this.elements.subscriptionDropdown && !this.elements.subscriptionDropdown.classList.contains('hidden')) {
                    if (!this.elements.subscriptionDropdown.contains(e.target) && e.target !== this.elements.subscriptionToggleBtn) {
                        this.elements.subscriptionDropdown.classList.add('hidden');
                        this.elements.subscriptionToggleBtn.classList.remove('open');
                    }
                }
            });
        }

        // Settings button
        if (this.elements.settingsBtn && this.app.settingsSidebar) {
            this.elements.settingsBtn.addEventListener('click', () => {
                this.app.settingsSidebar.open();
                // Close profile dropdown
                if (this.elements.profileDropdown) {
                    this.elements.profileDropdown.classList.add('hidden');
                }
            });
        }

        // Report Issue button
        if (this.elements.reportIssueBtn) {
            this.elements.reportIssueBtn.addEventListener('click', () => {
                this.handleReportIssue();
                // Close profile dropdown
                if (this.elements.profileDropdown) {
                    this.elements.profileDropdown.classList.add('hidden');
                }
            });
        }

        // Delete account button
        if (this.elements.deleteAccountBtn) {
            this.elements.deleteAccountBtn.addEventListener('click', () => {
                this.app.dialogManager.showDeleteAccountDialog1();
                // Close profile dropdown
                if (this.elements.profileDropdown) {
                    this.elements.profileDropdown.classList.add('hidden');
                }
            });
        }
    }

    /**
     * Handle report issue
     */
    handleReportIssue() {
        // Open default email client with pre-filled subject and body
        const email = 'support@nexusparent.com';
        const subject = encodeURIComponent('Nexus Parent - Issue Report');
        const body = encodeURIComponent(`
Please describe the issue you're experiencing:

---

App Version: 1.0.0
Platform: Desktop (Windows)
Date: ${new Date().toLocaleString()}

---

Issue Description:
[Please describe your issue here]

Steps to Reproduce:
1. 
2. 
3. 

Expected Behavior:
[What you expected to happen]

Actual Behavior:
[What actually happened]

Additional Information:
[Any other relevant details]
        `.trim());

        const mailtoLink = `mailto:${email}?subject=${subject}&body=${body}`;
        
        // Open in default email client
        require('electron').shell.openExternal(mailtoLink);
        
        console.log('📧 Opening email client for issue report');
    }

    /**
     * Show error message with shake animation
     */
    showError(message) {
        if (this.elements.connectFeedback) {
            this.elements.connectFeedback.textContent = message;
        }
        
        // Shake animation
        if (this.elements.childIdInput) {
            this.elements.childIdInput.animate(
                [
                    { transform: 'translateX(0)' },
                    { transform: 'translateX(-6px)' },
                    { transform: 'translateX(6px)' },
                    { transform: 'translateX(-6px)' },
                    { transform: 'translateX(0)' }
                ],
                { duration: 300 }
            );
        }
    }

    /**
     * Set connecting state (loading)
     */
    setConnectingState(isConnecting) {
        if (this.elements.connectBtn) {
            this.elements.connectBtn.disabled = isConnecting;
            this.elements.connectBtn.textContent = isConnecting ? 'Connecting...' : 'Connect';
        }
        if (this.elements.childIdInput) {
            this.elements.childIdInput.disabled = isConnecting;
        }
    }

    /**
     * Show connect screen
     */
    showConnectScreen() {
        if (this.elements.connectScreen) {
            this.elements.connectScreen.classList.remove('hidden');
        }
        if (this.elements.dashboardScreen) {
            this.elements.dashboardScreen.classList.add('hidden');
        }
        
        // Reset form
        if (this.elements.childIdInput) {
            this.elements.childIdInput.value = '';
            this.elements.childIdInput.disabled = false;
            setTimeout(() => this.elements.childIdInput.focus(), 200);
        }
        if (this.elements.connectBtn) {
            this.elements.connectBtn.disabled = false;
            this.elements.connectBtn.textContent = 'Connect';
        }
        if (this.elements.connectFeedback) {
            this.elements.connectFeedback.textContent = '';
        }
    }

    /**
     * Show dashboard screen
     */
    showDashboard(deviceId) {
        if (this.elements.connectScreen) {
            this.elements.connectScreen.classList.add('hidden');
        }
        if (this.elements.dashboardScreen) {
            this.elements.dashboardScreen.classList.remove('hidden');
        }
        
        // Initialize dust animation if not already done
        if (!this.dustAnimation) {
            // Wait for DOM to fully render and canvas to be visible
            setTimeout(() => {
                const dustCanvas = document.getElementById('dust-canvas');
                if (dustCanvas) {
                    this.dustAnimation = new DustAnimation(dustCanvas);
                }
            }, 200);
        }
        
        // Update device ID display
        if (this.elements.deviceIdDisplay && deviceId) {
            this.elements.deviceIdDisplay.textContent = `Device: ${deviceId}`;
        }
        
        // Close profile dropdown if open
        if (this.elements.profileDropdown) {
            this.elements.profileDropdown.classList.add('hidden');
        }
        
        this.setConnectingState(false);
    }
}

// Export for use in app
window.UIManager = UIManager;
