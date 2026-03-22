class WhatsappFeature {
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
            const response = await fetch('js/features/whatsapp/whatsapp.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load WhatsApp template:', error);
            return '<div>Failed to load WhatsApp feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/whatsapp/whatsapp.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load WhatsApp styles:', error);
        }
    }

    destroy() {
        // Cleanup if needed
    }
}

window.WhatsappFeature = WhatsappFeature;
