class GrokFeature {
    constructor(container) { this.container = container; this.init(); }
    async init() { this.container.innerHTML = await this.loadTemplate(); await this.loadStyles(); }
    async loadTemplate() { try { return await (await fetch('js/features/grok/grok.html')).text(); } catch (e) { return '<div>Failed to load</div>'; } }
    async loadStyles() { const link = document.createElement('link'); link.rel = 'stylesheet'; link.href = 'js/features/grok/grok.css'; document.head.appendChild(link); }
    destroy() {}
}
window.GrokFeature = GrokFeature;
