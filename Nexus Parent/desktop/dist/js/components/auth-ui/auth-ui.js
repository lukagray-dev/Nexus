/**
 * Authentication UI Controller
 * Manages auth UI component loading and initialization
 */

class AuthUI {
    constructor() {
        this.loaded = false;
    }

    /**
     * Load and inject auth UI
     */
    async load() {
        if (this.loaded) return;

        try {
            // Load HTML
            const htmlResponse = await fetch('js/components/auth-ui/auth-ui.html');
            const html = await htmlResponse.text();

            // Load CSS
            const cssLink = document.createElement('link');
            cssLink.rel = 'stylesheet';
            cssLink.href = 'js/components/auth-ui/auth-ui.css';
            document.head.appendChild(cssLink);

            // Inject HTML before connect screen
            const connectScreen = document.getElementById('connect-screen');
            if (connectScreen) {
                connectScreen.insertAdjacentHTML('beforebegin', html);
            }

            this.loaded = true;
            console.log('✅ Auth UI loaded');
        } catch (error) {
            console.error('❌ Failed to load auth UI:', error);
        }
    }
}

// Export
window.AuthUI = AuthUI;
