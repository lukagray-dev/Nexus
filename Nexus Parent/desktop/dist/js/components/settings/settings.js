/**
 * Settings Sidebar Component
 * Manages the settings sidebar functionality
 */

class SettingsSidebar {
    constructor(app) {
        this.app = app;
        this.elements = {};
    }

    /**
     * Initialize the settings sidebar
     */
    init() {
        this.cacheElements();
        this.setupEventListeners();
    }

    /**
     * Cache DOM elements
     */
    cacheElements() {
        this.elements.settingsSidebar = document.getElementById('settings-sidebar');
        this.elements.settingsCloseBtn = document.getElementById('settings-close-btn');
        this.elements.logoutBtn = document.getElementById('logout-btn');
        this.elements.deleteAccountBtn = document.getElementById('delete-account-btn-settings');
        this.elements.browseVideosBtn = document.getElementById('browse-videos-btn');
        this.elements.browseAudioBtn = document.getElementById('browse-audio-btn');
        this.elements.browseMessagesBtn = document.getElementById('browse-messages-btn');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        if (this.elements.settingsCloseBtn) {
            this.elements.settingsCloseBtn.addEventListener('click', () => this.close());
        }

        if (this.elements.logoutBtn) {
            this.elements.logoutBtn.addEventListener('click', () => this.handleLogout());
        }

        if (this.elements.deleteAccountBtn) {
            this.elements.deleteAccountBtn.addEventListener('click', () => this.handleDeleteAccount());
        }

        if (this.elements.browseVideosBtn) {
            this.elements.browseVideosBtn.addEventListener('click', () => this.handleBrowseFolder('videos'));
        }

        if (this.elements.browseAudioBtn) {
            this.elements.browseAudioBtn.addEventListener('click', () => this.handleBrowseFolder('audio'));
        }

        if (this.elements.browseMessagesBtn) {
            this.elements.browseMessagesBtn.addEventListener('click', () => this.handleBrowseFolder('messages'));
        }
    }

    /**
     * Handle logout
     */
    handleLogout() {
        // Close settings sidebar
        this.close();
        
        // Show disconnect confirmation dialog
        const disconnectDialog = document.getElementById('disconnect-dialog');
        if (disconnectDialog) {
            disconnectDialog.classList.remove('hidden');
        }
    }

    /**
     * Handle delete account
     */
    handleDeleteAccount() {
        // Close settings sidebar
        this.close();
        
        // Show delete account confirmation dialog (first step)
        const deleteDialog = document.getElementById('delete-account-dialog-1');
        if (deleteDialog) {
            deleteDialog.classList.remove('hidden');
        }
    }

    /**
     * Handle browse folder for download locations
     */
    async handleBrowseFolder(type) {
        try {
            // Use Electron's dialog to select folder
            const result = await window.IPCBridge.showOpenDirectoryDialog({
                title: `Select ${type.charAt(0).toUpperCase() + type.slice(1)} Download Location`,
                buttonLabel: 'Select Folder'
            });
            
            if (result && !result.canceled && result.filePaths.length > 0) {
                const selectedPath = result.filePaths[0];
                
                // Update the corresponding input field
                let inputElement;
                if (type === 'videos') {
                    inputElement = this.elements.browseVideosBtn.previousElementSibling;
                } else if (type === 'audio') {
                    inputElement = this.elements.browseAudioBtn.previousElementSibling;
                } else if (type === 'messages') {
                    inputElement = this.elements.browseMessagesBtn.previousElementSibling;
                }
                
                if (inputElement) {
                    inputElement.value = selectedPath;
                    // Save to settings (implement as needed)
                    this.saveDownloadLocation(type, selectedPath);
                }
            }
        } catch (error) {
            console.error('Error selecting folder:', error);
        }
    }

    /**
     * Save download location to settings
     */
    saveDownloadLocation(type, path) {
        // Save to localStorage
        localStorage.setItem(`nexus_download_${type}`, path);
        console.log(`✅ Saved ${type} download location:`, path);
    }

    /**
     * Load download locations from settings
     */
    loadDownloadLocations() {
        // Load from localStorage and update input fields
        const types = ['videos', 'audio', 'messages'];
        
        types.forEach(type => {
            const savedPath = localStorage.getItem(`nexus_download_${type}`);
            if (savedPath) {
                let inputElement;
                if (type === 'videos' && this.elements.browseVideosBtn) {
                    inputElement = this.elements.browseVideosBtn.previousElementSibling;
                } else if (type === 'audio' && this.elements.browseAudioBtn) {
                    inputElement = this.elements.browseAudioBtn.previousElementSibling;
                } else if (type === 'messages' && this.elements.browseMessagesBtn) {
                    inputElement = this.elements.browseMessagesBtn.previousElementSibling;
                }
                
                if (inputElement) {
                    inputElement.value = savedPath;
                }
            }
        });
    }

    /**
     * Open settings sidebar
     */
    open() {
        if (this.elements.settingsSidebar) {
            this.elements.settingsSidebar.classList.remove('hidden');
            setTimeout(() => this.elements.settingsSidebar.classList.add('open'), 10);
            
            // Load saved download locations
            this.loadDownloadLocations();
        }
    }

    /**
     * Close settings sidebar
     */
    close() {
        if (this.elements.settingsSidebar) {
            this.elements.settingsSidebar.classList.remove('open');
            setTimeout(() => this.elements.settingsSidebar.classList.add('hidden'), 300);
        }
    }
}

// Export for use in app.js
window.SettingsSidebar = SettingsSidebar;
