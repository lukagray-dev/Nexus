/**
 * User Info Handler
 * Manages user profile display and data
 */

class UserInfoHandler {
    constructor(authManager) {
        this.authManager = authManager;
        this.supabase = authManager.supabase;
        this.elements = {};
        this.userData = null;
        
        this.init();
    }

    /**
     * Initialize user info handler
     */
    init() {
        this.cacheElements();
        this.setupEventListeners();
    }

    /**
     * Cache DOM elements
     */
    cacheElements() {
        this.elements.nameDisplay = document.getElementById('user-name-display');
        this.elements.emailDisplay = document.getElementById('user-email-display');
        this.elements.continueBtn = document.getElementById('continue-btn');
        this.elements.logoutBtn = document.getElementById('logout-btn');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Continue to connect screen
        if (this.elements.continueBtn) {
            this.elements.continueBtn.addEventListener('click', () => {
                this.authManager.proceedToConnect();
            });
        }

        // Logout
        if (this.elements.logoutBtn) {
            this.elements.logoutBtn.addEventListener('click', () => this.handleLogout());
        }
    }

    /**
     * Load user data from Supabase database
     */
    async loadUserData(user) {
        if (!user) return;

        try {
            // Use upsert to create or update user profile
            const userProfile = {
                id: user.id,
                name: user.user_metadata?.name || user.email?.split('@')[0] || 'User',
                email: user.email,
                subscription_plan: 'free',
                subscription_status: 'active'
            };

            const { data, error } = await this.supabase
                .from('users')
                .upsert(userProfile, { onConflict: 'id' })
                .select()
                .single();

            if (error) throw error;
            
            this.userData = data;

            // Update display
            this.updateUserInfoDisplay(user);
            
            console.log('✅ User data loaded from Supabase');
        } catch (error) {
            console.error('❌ Failed to load user data:', error);
            // Fallback to user metadata if database fails
            this.userData = {
                name: user.user_metadata?.name || user.email?.split('@')[0] || 'User',
                email: user.email
            };
            this.updateUserInfoDisplay(user);
        }
    }

    /**
     * Update user info display
     */
    updateUserInfoDisplay(user) {
        if (this.elements.nameDisplay && this.userData) {
            this.elements.nameDisplay.textContent = this.userData.name || user.user_metadata?.name || 'User';
        }

        if (this.elements.emailDisplay && user) {
            this.elements.emailDisplay.textContent = user.email || '—';
        }
    }

    /**
     * Get user data
     */
    getUserData() {
        return this.userData;
    }

    /**
     * Update user profile in Supabase
     */
    async updateUserProfile(updates) {
        const user = this.authManager.getCurrentUser();
        if (!user) return;

        try {
            const { error } = await this.supabase
                .from('users')
                .update(updates)
                .eq('id', user.id);
            
            if (error) throw error;
            
            this.userData = { ...this.userData, ...updates };
            console.log('✅ User profile updated in Supabase');
            return true;
        } catch (error) {
            console.error('❌ Failed to update profile:', error);
            throw error;
        }
    }

    /**
     * Handle logout
     */
    async handleLogout() {
        try {
            await this.authManager.signOut();
        } catch (error) {
            console.error('❌ Logout failed:', error);
            alert('Failed to logout. Please try again.');
        }
    }
}

// Export
window.UserInfoHandler = UserInfoHandler;
