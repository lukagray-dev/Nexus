/**
 * Vibrate and Flash Feature Module
 * Send vibration and flash patterns to child device
 */

class VibrateflashFeature {
    constructor(container) {
        this.container = container;
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
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/vibrateflash/vibrateflash.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Vibrateflash template:', error);
            return '<div>Failed to load Vibrate & Flash feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/vibrateflash/vibrateflash.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Vibrateflash styles:', error);
        }
    }

    setupEventListeners() {
        const statusDiv = this.container.querySelector('#vibrateflash-status');

        // Vibration button handlers
        this.container.querySelectorAll('.vibrate-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const pattern = btn.dataset.pattern;
                this.sendVibrateCommand(pattern, statusDiv);
            });
        });

        // Flash button handlers
        this.container.querySelectorAll('.flash-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const pattern = btn.dataset.pattern;
                this.sendFlashCommand(pattern, statusDiv);
            });
        });

        // Combined button handlers
        this.container.querySelectorAll('.combined-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const pattern = btn.dataset.pattern;
                this.sendCombinedCommand(pattern, statusDiv);
            });
        });
    }

    sendVibrateCommand(pattern, statusDiv) {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = JSON.stringify({ cmd: 'VIBRATE', pattern: pattern });
            app.connectionManager.sendCommand(command);
            
            statusDiv.textContent = `Sending vibration pattern: ${pattern}...`;
            statusDiv.style.color = '#60a5fa';
            
            console.log(`📳 Sent vibration command: ${pattern}`);
            
            // Clear status after 3 seconds
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 3000);
        } catch (error) {
            console.error('Failed to send vibrate command:', error);
            statusDiv.textContent = 'Failed to send command';
            statusDiv.style.color = '#ef4444';
        }
    }

    sendFlashCommand(pattern, statusDiv) {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = JSON.stringify({ cmd: 'FLASH', pattern: pattern });
            app.connectionManager.sendCommand(command);
            
            statusDiv.textContent = `Sending flash pattern: ${pattern}...`;
            statusDiv.style.color = '#fbbf24';
            
            console.log(`💡 Sent flash command: ${pattern}`);
            
            // Clear status after 3 seconds
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 3000);
        } catch (error) {
            console.error('Failed to send flash command:', error);
            statusDiv.textContent = 'Failed to send command';
            statusDiv.style.color = '#ef4444';
        }
    }

    sendCombinedCommand(pattern, statusDiv) {
        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            const command = JSON.stringify({ cmd: 'VIBRATE_FLASH', pattern: pattern });
            app.connectionManager.sendCommand(command);
            
            statusDiv.textContent = `Sending combined pattern: ${pattern}...`;
            statusDiv.style.color = '#4ade80';
            
            console.log(`📳💡 Sent combined command: ${pattern}`);
            
            // Clear status after 3 seconds
            setTimeout(() => {
                statusDiv.textContent = '';
            }, 3000);
        } catch (error) {
            console.error('Failed to send combined command:', error);
            statusDiv.textContent = 'Failed to send command';
            statusDiv.style.color = '#ef4444';
        }
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.VibrateflashFeature = VibrateflashFeature;
