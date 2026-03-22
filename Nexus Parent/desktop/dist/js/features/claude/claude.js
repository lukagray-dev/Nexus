class ClaudeFeature {
    constructor(container) { this.container = container; this.init(); }
    async init() { this.container.innerHTML = await this.loadTemplate(); await this.loadStyles(); }
    async loadTemplate() { try { return await (await fetch('js/features/claude/claude.html')).text(); } catch (e) { return '<div>Failed to load</div>'; } }
    async loadStyles() { const link = document.createElement('link'); link.rel = 'stylesheet'; link.href = 'js/features/claude/claude.css'; document.head.appendChild(link); }
    destroy() {}
}
window.ClaudeFeature = ClaudeFeature;
