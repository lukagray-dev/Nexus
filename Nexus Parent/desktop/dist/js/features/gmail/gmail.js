/**
 * Gmail Feature Module
 * Handles Gmail message monitoring
 */

class GmailFeature {
    constructor(container) {
        this.container = container;
        this.emails = [];
        this.isActive = false;
        this.setupEventListeners();
    }

    setupEventListeners() {
        const searchInput = this.container.querySelector('#gmail-search');
        const playPauseBtn = this.container.querySelector('#gmail-play-pause-btn');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.filterEmails(e.target.value));
        }

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.togglePlayPause());
        }
    }

    togglePlayPause() {
        const playIcon = this.container.querySelector('.play-icon');
        const pauseIcon = this.container.querySelector('.pause-icon');

        this.isActive = !this.isActive;

        if (this.isActive) {
            console.log('Gmail monitoring active');
            if (playIcon) playIcon.style.display = 'none';
            if (pauseIcon) pauseIcon.style.display = 'block';
        } else {
            console.log('Gmail monitoring paused');
            if (playIcon) playIcon.style.display = 'block';
            if (pauseIcon) pauseIcon.style.display = 'none';
        }
    }

    filterEmails(query) {
        console.log('Filtering Gmail messages:', query);
        // TODO: Implement email filtering
    }

    destroy() {
        // Cleanup
    }
}

// Export to window for dynamic loading
window.GmailFeature = GmailFeature;
