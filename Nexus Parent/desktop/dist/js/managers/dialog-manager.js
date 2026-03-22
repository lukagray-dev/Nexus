/**
 * Dialog Manager
 * Manages all dialog interactions (disconnect, delete account, etc.)
 */

class DialogManager {
    constructor(app) {
        this.app = app;
        this.elements = {};
    }

    /**
     * Cache dialog elements
     */
    cacheElements() {
        // Disconnect dialog
        this.elements.disconnectDialog = document.getElementById('disconnect-dialog');
        this.elements.disconnectCancelBtn = document.getElementById('disconnect-cancel-btn');
        this.elements.disconnectConfirmBtn = document.getElementById('disconnect-confirm-btn');
        
        // Delete account dialogs
        this.elements.deleteDialog1 = document.getElementById('delete-account-dialog-1');
        this.elements.deletePassword1 = document.getElementById('delete-password-1');
        this.elements.deleteCancelBtn1 = document.getElementById('delete-cancel-btn-1');
        this.elements.deleteNextBtn1 = document.getElementById('delete-next-btn-1');
        
        this.elements.deleteDialog2 = document.getElementById('delete-account-dialog-2');
        this.elements.deletePassword2 = document.getElementById('delete-password-2');
        this.elements.deleteCancelBtn2 = document.getElementById('delete-cancel-btn-2');
        this.elements.deleteConfirmBtn2 = document.getElementById('delete-confirm-btn-2');
    }

    /**
     * Setup dialog event listeners
     */
    setupEventListeners() {
        // Disconnect dialog buttons
        if (this.elements.disconnectCancelBtn) {
            this.elements.disconnectCancelBtn.addEventListener('click', () => this.closeDisconnectDialog());
        }
        if (this.elements.disconnectConfirmBtn) {
            this.elements.disconnectConfirmBtn.addEventListener('click', () => this.handleDisconnectConfirm());
        }

        // Delete account dialog 1 buttons
        if (this.elements.deleteCancelBtn1) {
            this.elements.deleteCancelBtn1.addEventListener('click', () => this.closeDeleteAccountDialog1());
        }
        if (this.elements.deleteNextBtn1) {
            this.elements.deleteNextBtn1.addEventListener('click', () => this.showDeleteAccountDialog2());
        }

        // Delete account dialog 2 buttons
        if (this.elements.deleteCancelBtn2) {
            this.elements.deleteCancelBtn2.addEventListener('click', () => this.closeDeleteAccountDialog2());
        }
        if (this.elements.deleteConfirmBtn2) {
            this.elements.deleteConfirmBtn2.addEventListener('click', () => this.handleDeleteAccount());
        }
    }

    /**
     * Disconnect dialog methods
     */
    showDisconnectDialog() {
        if (this.elements.disconnectDialog) {
            this.elements.disconnectDialog.classList.remove('hidden');
        }
    }

    closeDisconnectDialog() {
        if (this.elements.disconnectDialog) {
            this.elements.disconnectDialog.classList.add('hidden');
        }
    }

    async handleDisconnectConfirm() {
        this.closeDisconnectDialog();
        await this.app.handleDisconnect();
    }

    /**
     * Delete account dialog methods
     */
    showDeleteAccountDialog1() {
        if (this.elements.deleteDialog1) {
            this.elements.deleteDialog1.classList.remove('hidden');
            this.elements.deletePassword1.value = '';
            this.elements.deletePassword1.focus();
        }
    }

    closeDeleteAccountDialog1() {
        if (this.elements.deleteDialog1) {
            this.elements.deleteDialog1.classList.add('hidden');
        }
    }

    showDeleteAccountDialog2() {
        if (this.elements.deletePassword1.value.length === 0) {
            alert('Please enter your password');
            return;
        }
        
        if (this.elements.deleteDialog1) {
            this.elements.deleteDialog1.classList.add('hidden');
        }
        
        if (this.elements.deleteDialog2) {
            this.elements.deleteDialog2.classList.remove('hidden');
            this.elements.deletePassword2.value = '';
            this.elements.deletePassword2.focus();
        }
    }

    closeDeleteAccountDialog2() {
        if (this.elements.deleteDialog2) {
            this.elements.deleteDialog2.classList.add('hidden');
        }
    }

    handleDeleteAccount() {
        if (this.elements.deletePassword2.value.length === 0) {
            alert('Please enter your password');
            return;
        }
        
        console.log('Account deletion confirmed (placeholder)');
        alert('Account deletion is a placeholder. Real implementation will be added when authentication is implemented.');
        
        if (this.elements.deleteDialog2) {
            this.elements.deleteDialog2.classList.add('hidden');
        }
    }
}

// Export for use in app
window.DialogManager = DialogManager;
