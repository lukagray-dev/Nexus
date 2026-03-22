/**
 * Nexus App - Main Application Controller
 * Orchestrates all managers and components
 */

class NexusApp {
    constructor() {
        // State
        this.currentDeviceId = null;
        this.isConnecting = false;
        
        // Managers
        this.authManager = null;
        this.connectionManager = null;
        this.dialogManager = null;
        this.uiManager = null;
        this.windowManager = null;
        this.componentLoader = null;
        
        // Components
        this.mainSidebar = null;
        this.aiSidebar = null;
        this.settingsSidebar = null;
        this.authUI = null;
        
        // Initialize
        this.init();
    }

    /**
     * Initialize the application
     */
    async init() {
        await this.loadComponents();
        await this.initAuth();
        this.initManagers();
        this.initWindowManager();
        this.initComponents();
        this.setupManagers();
        // Auth system will handle initial screen display
    }

    /**
     * Load UI components dynamically
     */
    async loadComponents() {
        this.componentLoader = new ComponentLoader();
        
        try {
            // Load auth UI first
            this.authUI = new AuthUI();
            await this.authUI.load();
            
            // Load other components
            const sidebarHTML = await this.componentLoader.load('sidebar');
            const aiSidebarHTML = await this.componentLoader.load('ai-sidebar');
            const settingsHTML = await this.componentLoader.load('settings');
            
            document.getElementById('sidebar-container').innerHTML = sidebarHTML;
            document.getElementById('ai-sidebar-container').innerHTML = aiSidebarHTML;
            document.getElementById('settings-sidebar-container').innerHTML = settingsHTML;
            
            console.log('✓ All components loaded and injected');
        } catch (error) {
            console.error('Failed to load components:', error);
        }
    }

    /**
     * Initialize authentication system
     */
    async initAuth() {
        try {
            this.authManager = new AuthManager();
            await this.authManager.init();
            console.log('✓ Authentication system initialized');
        } catch (error) {
            console.error('Failed to initialize auth:', error);
        }
    }

    /**
     * Initialize managers
     */
    initManagers() {
        this.connectionManager = new ConnectionManager(this);
        this.dialogManager = new DialogManager(this);
        this.uiManager = new UIManager(this);
    }

    /**
     * Initialize window manager
     */
    initWindowManager() {
        this.windowManager = new WindowManager();
        const featureWindows = document.getElementById('feature-windows');
        if (featureWindows) {
            this.windowManager.init(featureWindows);
        }
    }

    /**
     * Initialize component instances
     */
    initComponents() {
        if (window.MainSidebar) {
            this.mainSidebar = new MainSidebar(this);
            this.mainSidebar.init();
        }
        
        if (window.AISidebar) {
            this.aiSidebar = new AISidebar(this);
            this.aiSidebar.init();
        }
        
        if (window.SettingsSidebar) {
            this.settingsSidebar = new SettingsSidebar(this);
            this.settingsSidebar.init();
        }
    }

    /**
     * Setup managers (cache elements and event listeners)
     */
    setupManagers() {
        // UI Manager
        this.uiManager.cacheElements();
        this.uiManager.setupEventListeners();
        
        // Dialog Manager
        this.dialogManager.cacheElements();
        this.dialogManager.setupEventListeners();
    }

    /**
     * Handle connection to child device
     */
    async handleConnect() {
        if (this.isConnecting) return;

        const childId = this.uiManager.elements.childIdInput.value.trim();
        
        this.isConnecting = true;
        this.uiManager.setConnectingState(true);

        try {
            // Connect to device
            this.currentDeviceId = await this.connectionManager.connect(childId);
            
            // Wait for connection to establish
            await this.connectionManager.waitForConnection();
            
            // Show dashboard
            this.uiManager.showDashboard(this.currentDeviceId);
            
        } catch (error) {
            console.error('❌ Connection failed:', error);
            this.uiManager.showError(`${error.message || error}`);
            this.uiManager.setConnectingState(false);
        } finally {
            this.isConnecting = false;
        }
    }

    /**
     * Handle disconnection from device
     */
    async handleDisconnect() {
        try {
            await this.connectionManager.disconnect();
            
            // Close all feature windows
            if (this.windowManager) {
                this.windowManager.closeAll();
            }
            
            this.currentDeviceId = null;
            this.uiManager.showConnectScreen();
            
        } catch (error) {
            console.error('❌ Disconnect failed:', error);
        }
    }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.nexusApp = new NexusApp();
});
