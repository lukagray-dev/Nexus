/**
 * Main Sidebar Component
 * Manages the main sidebar functionality including collapse/expand and resize
 */

class MainSidebar {
    constructor(app) {
        this.app = app;
        this.elements = {};
    }

    /**
     * Initialize the sidebar
     */
    init() {
        this.cacheElements();
        this.setupEventListeners();
        this.setupResize();
    }

    /**
     * Cache DOM elements
     */
    cacheElements() {
        this.elements.sidebar = document.getElementById('sidebar');
        this.elements.sidebarLogoBtn = document.getElementById('sidebar-logo-btn');
        this.elements.sidebarToggleBtn = document.getElementById('sidebar-toggle-btn');
        this.elements.resizeHandle = document.getElementById('sidebar-resize-handle');
        this.elements.sidebarBtns = document.querySelectorAll('.sidebar-btn');
        this.elements.stealthModeBtn = document.getElementById('stealth-mode-btn');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Toggle button
        if (this.elements.sidebarToggleBtn && this.elements.sidebar) {
            this.elements.sidebarToggleBtn.addEventListener('click', () => this.toggle());
        }

        // Logo button (expand when collapsed)
        if (this.elements.sidebarLogoBtn && this.elements.sidebar) {
            this.elements.sidebarLogoBtn.addEventListener('click', () => {
                if (this.elements.sidebar.classList.contains('collapsed')) {
                    this.expand();
                }
            });
        }

        // Stealth mode button
        if (this.elements.stealthModeBtn) {
            this.elements.stealthModeBtn.addEventListener('click', () => this.handleStealthMode());
        }

        // Sidebar feature buttons
        this.elements.sidebarBtns.forEach(btn => {
            // Skip stealth mode button
            if (btn.id === 'stealth-mode-btn') return;
            
            btn.addEventListener('click', () => {
                const feature = btn.getAttribute('data-feature');
                if (feature && this.app.windowManager) {
                    this.app.windowManager.createWindow(feature);
                    // Update active state
                    this.elements.sidebarBtns.forEach(b => {
                        if (b.id !== 'stealth-mode-btn') {
                            b.classList.remove('active');
                        }
                    });
                    btn.classList.add('active');
                }
            });
        });
    }

    /**
     * Setup sidebar resize functionality
     */
    setupResize() {
        const sidebar = this.elements.sidebar;
        const resizeHandle = this.elements.resizeHandle;
        const topInfo = document.querySelector('.top-info');
        if (!sidebar || !resizeHandle) return;

        let isResizing = false;

        resizeHandle.addEventListener('mousedown', () => {
            // Don't allow resizing when collapsed
            if (sidebar.classList.contains('collapsed')) return;
            
            isResizing = true;
            document.body.style.cursor = 'ew-resize';

            document.onmousemove = (e) => {
                if (!isResizing) return;
                let newWidth = e.clientX;
                if (newWidth < 120) newWidth = 120;
                if (newWidth > 400) newWidth = 400;
                sidebar.style.width = newWidth + 'px';
                
                // Update top-info position to match sidebar width
                if (topInfo) {
                    topInfo.style.left = newWidth + 'px';
                }
            };

            document.onmouseup = () => {
                isResizing = false;
                document.body.style.cursor = '';
                document.onmousemove = null;
                document.onmouseup = null;
            };
        });
    }

    /**
     * Toggle sidebar collapsed state
     */
    toggle() {
        if (this.elements.sidebar) {
            this.elements.sidebar.classList.toggle('collapsed');
            
            // Adjust main area padding when collapsed
            const mainArea = document.querySelector('.main-area');
            if (mainArea) {
                if (this.elements.sidebar.classList.contains('collapsed')) {
                    mainArea.style.paddingLeft = '0';
                } else {
                    mainArea.style.paddingLeft = '';
                }
            }
        }
    }

    /**
     * Expand sidebar (remove collapsed state)
     */
    expand() {
        if (this.elements.sidebar) {
            this.elements.sidebar.classList.remove('collapsed');
            
            // Reset main area padding
            const mainArea = document.querySelector('.main-area');
            if (mainArea) {
                mainArea.style.paddingLeft = '';
            }
        }
    }

    /**
     * Handle stealth mode toggle
     */
    async handleStealthMode() {
        const btn = this.elements.stealthModeBtn;
        if (!btn) return;

        // Check if already in stealth mode
        const isActive = btn.classList.contains('active');

        // Show confirmation dialog
        const confirmed = await this.showStealthConfirmation(isActive);
        if (!confirmed) {
            console.log('🚫 Stealth mode action cancelled by user');
            return;
        }

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Show loading state
            btn.disabled = true;
            const originalText = btn.textContent;
            btn.textContent = 'Processing...';

            if (isActive) {
                // Deactivate stealth mode
                console.log('🔓 Deactivating stealth mode');
                app.connectionManager.sendCommand('STEALTH_OFF');
                
                // Wait for confirmation
                await this.waitForStealthConfirmation('STEALTH_OFF_ACK');
                
                btn.classList.remove('active');
                btn.textContent = 'Stealth Mode';
                console.log('✅ Stealth mode deactivated');
            } else {
                // Activate stealth mode
                console.log('🔒 Activating stealth mode');
                app.connectionManager.sendCommand('STEALTH_ON');
                
                // Wait for confirmation
                await this.waitForStealthConfirmation('STEALTH_ON_ACK');
                
                btn.classList.add('active');
                btn.textContent = 'Stealth Active';
                console.log('✅ Stealth mode activated');
            }

            btn.disabled = false;

        } catch (error) {
            console.error('❌ Stealth mode error:', error);
            btn.disabled = false;
            btn.textContent = isActive ? 'Stealth Active' : 'Stealth Mode';
            alert('Failed to toggle stealth mode: ' + error.message);
        }
    }

    /**
     * Show stealth mode confirmation dialog
     */
    showStealthConfirmation(isDeactivating) {
        return new Promise((resolve) => {
            // Create modal overlay
            const overlay = document.createElement('div');
            overlay.className = 'modal-overlay';
            overlay.style.zIndex = '9999';

            // Create modal dialog
            const dialog = document.createElement('div');
            dialog.className = 'modal-dialog stealth-modal';
            
            // Modal content based on action
            if (isDeactivating) {
                dialog.innerHTML = `
                    <div class="modal-header">
                        <h3>Deactivate Stealth Mode</h3>
                    </div>
                    <div class="modal-body">
                        <p>Are you sure you want to deactivate stealth mode?</p>
                        <p class="info-text">The app icon will become visible in the child device's app drawer.</p>
                    </div>
                    <div class="modal-footer">
                        <button class="modal-btn cancel">Cancel</button>
                        <button class="modal-btn confirm">Deactivate</button>
                    </div>
                `;
            } else {
                dialog.innerHTML = `
                    <div class="modal-header">
                        <h3>Activate Stealth Mode</h3>
                    </div>
                    <div class="modal-body">
                        <p class="warning-text">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 20px; height: 20px; vertical-align: middle; margin-right: 8px;">
                                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                                <line x1="12" y1="9" x2="12" y2="13"></line>
                                <line x1="12" y1="17" x2="12.01" y2="17"></line>
                            </svg>
                            This will hide the app icon from the child device's app drawer.
                        </p>
                        <p class="info-text">The app will only be accessible from this parent dashboard. You can deactivate stealth mode anytime.</p>
                    </div>
                    <div class="modal-footer">
                        <button class="modal-btn cancel">Cancel</button>
                        <button class="modal-btn delete">Activate Stealth</button>
                    </div>
                `;
            }

            overlay.appendChild(dialog);
            document.body.appendChild(overlay);

            // Animate in
            requestAnimationFrame(() => {
                overlay.style.opacity = '0';
                overlay.style.transition = 'opacity 0.3s cubic-bezier(.2,.9,.2,1)';
                requestAnimationFrame(() => {
                    overlay.style.opacity = '1';
                });
            });

            // Handle button clicks
            const cancelBtn = dialog.querySelector('.cancel');
            const confirmBtn = dialog.querySelector('.confirm, .delete');

            const closeModal = (confirmed) => {
                overlay.style.opacity = '0';
                setTimeout(() => {
                    document.body.removeChild(overlay);
                    resolve(confirmed);
                }, 300);
            };

            cancelBtn.addEventListener('click', () => closeModal(false));
            confirmBtn.addEventListener('click', () => closeModal(true));
            
            // Close on overlay click
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    closeModal(false);
                }
            });

            // Close on Escape key
            const escapeHandler = (e) => {
                if (e.key === 'Escape') {
                    document.removeEventListener('keydown', escapeHandler);
                    closeModal(false);
                }
            };
            document.addEventListener('keydown', escapeHandler);
        });
    }

    /**
     * Wait for stealth mode confirmation from child device
     */
    waitForStealthConfirmation(expectedMessage) {
        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                window.removeEventListener('stealth-confirmation', handler);
                reject(new Error('Stealth mode confirmation timeout'));
            }, 30000); // 30 seconds timeout

            const handler = (event) => {
                if (event.detail.message === expectedMessage) {
                    clearTimeout(timeout);
                    window.removeEventListener('stealth-confirmation', handler);
                    resolve();
                }
            };

            window.addEventListener('stealth-confirmation', handler);
        });
    }
}

// Export for use in app.js
window.MainSidebar = MainSidebar;
