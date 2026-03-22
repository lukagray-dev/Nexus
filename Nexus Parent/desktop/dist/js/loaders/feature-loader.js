/**
 * Feature Loader - Dynamically loads feature modules
 * Handles loading HTML, CSS, and JavaScript for each feature
 */

class FeatureLoader {
    constructor() {
        this.loadedFeatures = {};
        this.loadedStyles = new Set();
    }

    /**
     * Load a feature module dynamically
     * @param {string} feature - Feature name (e.g., 'camera', 'screen')
     * @param {Element} container - Container element to inject content into
     * @returns {Promise<Object>} Feature instance
     */
    async loadFeature(feature, container) {
        try {
            // Load CSS if not already loaded
            await this.loadFeatureStyles(feature);

            // Load HTML template
            const html = await this.loadFeatureTemplate(feature);
            container.innerHTML = html;

            // Load and initialize JavaScript module
            const featureModule = await this.loadFeatureModule(feature);
            
            // Create NEW feature instance each time (don't cache instances)
            const featureInstance = new featureModule(container);

            return featureInstance;
        } catch (error) {
            console.error(`Failed to load feature '${feature}':`, error);
            container.innerHTML = `<div style="text-align:center;padding:2rem;color:#888;">Failed to load ${feature} feature</div>`;
            return null;
        }
    }

    /**
     * Load feature CSS stylesheet
     * @param {string} feature - Feature name
     * @returns {Promise<void>}
     */
    async loadFeatureStyles(feature) {
        const styleId = `feature-style-${feature}`;
        
        // Skip if already loaded
        if (this.loadedStyles.has(feature)) {
            return;
        }

        return new Promise((resolve, reject) => {
            const link = document.createElement('link');
            link.id = styleId;
            link.rel = 'stylesheet';
            link.href = `js/features/${feature}/${feature}.css`;
            
            link.onload = () => {
                this.loadedStyles.add(feature);
                resolve();
            };
            
            link.onerror = () => {
                reject(new Error(`Failed to load CSS for feature: ${feature}`));
            };
            
            document.head.appendChild(link);
        });
    }

    /**
     * Load feature HTML template
     * @param {string} feature - Feature name
     * @returns {Promise<string>} HTML content
     */
    async loadFeatureTemplate(feature) {
        try {
            const response = await fetch(`js/features/${feature}/${feature}.html`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            return await response.text();
        } catch (error) {
            throw new Error(`Failed to load template for feature '${feature}': ${error.message}`);
        }
    }

    /**
     * Load feature JavaScript module
     * @param {string} feature - Feature name
     * @returns {Promise<Function>} Feature class constructor
     */
    async loadFeatureModule(feature) {
        // Check if module is already loaded in window
        const className = this.getFeatureClassName(feature);
        if (window[className]) {
            return window[className];
        }

        // Dynamically import the module
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = `js/features/${feature}/${feature}.js`;
            
            script.onload = () => {
                const moduleClass = window[className];
                if (!moduleClass) {
                    reject(new Error(`Feature class '${className}' not found after loading module`));
                } else {
                    resolve(moduleClass);
                }
            };
            
            script.onerror = () => {
                reject(new Error(`Failed to load JavaScript module for feature: ${feature}`));
            };
            
            document.head.appendChild(script);
        });
    }

    /**
     * Get the class name for a feature (e.g., 'camera' -> 'CameraFeature')
     * @param {string} feature - Feature name
     * @returns {string} Class name
     */
    getFeatureClassName(feature) {
        return feature.split('-').map(word => 
            word.charAt(0).toUpperCase() + word.slice(1)
        ).join('') + 'Feature';
    }

    /**
     * Unload a feature and clean up resources
     * @param {string} feature - Feature name
     */
    unloadFeature(feature) {
        if (this.loadedFeatures[feature]) {
            const instance = this.loadedFeatures[feature];
            if (instance.destroy && typeof instance.destroy === 'function') {
                instance.destroy();
            }
            delete this.loadedFeatures[feature];
        }
    }

    /**
     * Unload all features
     */
    unloadAll() {
        Object.keys(this.loadedFeatures).forEach(feature => {
            this.unloadFeature(feature);
        });
    }
}

// Create global instance
window.featureLoader = new FeatureLoader();
console.log("✅ feature-loader.js loaded");
