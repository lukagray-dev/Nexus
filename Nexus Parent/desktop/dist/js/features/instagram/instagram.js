class InstagramFeature {
    constructor(container) {
        this.container = container;
        this.init();
    }

    async init() {
        const html = await this.loadTemplate();
        this.container.innerHTML = html;
        await this.loadStyles();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/instagram/instagram.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Instagram template:', error);
            return '<div>Failed to load Instagram feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/instagram/instagram.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Instagram styles:', error);
        }
    }

    destroy() {}
}

window.InstagramFeature = InstagramFeature;
