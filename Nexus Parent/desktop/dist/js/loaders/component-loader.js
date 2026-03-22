/**
 * Component Loader
 * Dynamically loads UI components (HTML, CSS, JS) for sidebar, settings, and AI assistant
 */

class ComponentLoader {
    constructor() {
        this.components = {
            sidebar: {
                html: 'js/components/sidebar/sidebar.html',
                css: 'js/components/sidebar/sidebar.css',
                js: 'js/components/sidebar/sidebar.js'
            },
            settings: {
                html: 'js/components/settings/settings.html',
                css: 'js/components/settings/settings.css',
                js: 'js/components/settings/settings.js'
            },
            'ai-sidebar': {
                html: 'js/components/ai-sidebar/ai-sidebar.html',
                css: 'js/components/ai-sidebar/ai-sidebar.css',
                js: 'js/components/ai-sidebar/ai-sidebar.js'
            }
        };
        this.loadedComponents = new Set();
        this.htmlCache = {}; // Cache loaded HTML
    }

    /**
     * Load all components
     */
    async loadAll() {
        const componentNames = Object.keys(this.components);
        await Promise.all(componentNames.map(name => this.load(name)));
    }

    /**
     * Load a specific component
     */
    async load(componentName) {
        // If already loaded, return cached HTML
        if (this.loadedComponents.has(componentName)) {
            return this.htmlCache[componentName];
        }

        const component = this.components[componentName];
        if (!component) {
            console.error(`Component not found: ${componentName}`);
            return;
        }

        try {
            // Load CSS
            await this.loadCSS(component.css);
            
            // Load HTML
            const html = await this.loadHTML(component.html);
            
            // Load JS
            await this.loadJS(component.js);
            
            // Cache HTML and mark as loaded
            this.htmlCache[componentName] = html;
            this.loadedComponents.add(componentName);
            console.log(`✓ Component loaded: ${componentName}`);
            
            return html;
        } catch (error) {
            console.error(`Failed to load component ${componentName}:`, error);
            throw error;
        }
    }

    /**
     * Load CSS file
     */
    async loadCSS(url) {
        return new Promise((resolve, reject) => {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = url;
            link.onload = () => resolve();
            link.onerror = () => reject(new Error(`Failed to load CSS: ${url}`));
            document.head.appendChild(link);
        });
    }

    /**
     * Load HTML file
     */
    async loadHTML(url) {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Failed to load HTML: ${url}`);
        }
        return await response.text();
    }

    /**
     * Load JS file
     */
    async loadJS(url) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = url;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error(`Failed to load JS: ${url}`));
            document.body.appendChild(script);
        });
    }
}

// Export for global use
window.ComponentLoader = ComponentLoader;
