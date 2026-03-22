/**
 * Sign-Up Handler
 * Manages sign-up functionality using Supabase Auth
 * Email verification is handled automatically by Supabase
 */

class SignUpHandler {
    constructor(authManager) {
        this.authManager = authManager;
        this.supabase = authManager.supabase;
        this.elements = {};
        
        this.init();
    }

    /**
     * Initialize sign-up handler
     */
    init() {
        this.cacheElements();
        this.setupEventListeners();
    }

    /**
     * Cache DOM elements
     */
    cacheElements() {
        this.elements.form = document.getElementById('signup-form');
        this.elements.nameInput = document.getElementById('signup-name');
        this.elements.emailInput = document.getElementById('signup-email');
        this.elements.passwordInput = document.getElementById('signup-password');
        this.elements.confirmInput = document.getElementById('signup-confirm');
        this.elements.submitBtn = document.getElementById('signup-submit-btn');
        this.elements.showSigninBtn = document.getElementById('show-signin-btn');
        this.elements.errorDiv = document.getElementById('signup-error');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Sign-up form
        if (this.elements.form) {
            this.elements.form.addEventListener('submit', (e) => this.handleSignUp(e));
        }

        // Show sign-in
        if (this.elements.showSigninBtn) {
            this.elements.showSigninBtn.addEventListener('click', () => {
                this.authManager.showSignIn();
            });
        }
    }

    /**
     * Handle sign-up form submission
     */
    async handleSignUp(e) {
        e.preventDefault();
        
        const name = this.elements.nameInput.value.trim();
        const email = this.elements.emailInput.value.trim();
        const password = this.elements.passwordInput.value;
        const confirm = this.elements.confirmInput.value;

        // Validation
        if (!name || !email || !password || !confirm) {
            this.showError('Please fill in all fields');
            return;
        }

        if (!this.isValidEmail(email)) {
            this.showError('Please enter a valid email address');
            return;
        }

        if (password.length < 6) {
            this.showError('Password must be at least 6 characters');
            return;
        }

        if (password !== confirm) {
            this.showError('Passwords do not match');
            return;
        }

        this.setLoading(true);
        this.hideError();

        try {
            // Sign up with Supabase Auth
            const { data, error } = await this.supabase.auth.signUp({
                email: email,
                password: password,
                options: {
                    data: {
                        name: name
                    },
                    emailRedirectTo: 'nexus://auth/callback'
                }
            });

            if (error) throw error;

            // Check if user already exists (Supabase returns success but with existing user)
            if (data.user && data.user.identities && data.user.identities.length === 0) {
                // User already exists
                throw new Error('User already registered');
            }

            console.log('✅ Sign-up successful');
            
            // Store credentials for auto sign-in after verification
            this.authManager.setPendingVerification(email, password);
            
            // Don't save profile during signup - user isn't authenticated yet
            // Profile will be created automatically on first sign-in after verification

            // Show verification waiting screen
            this.authManager.showVerificationWaiting(email);

        } catch (error) {
            console.error('❌ Sign-up failed:', error);
            this.handleSignUpError(error);
        } finally {
            this.setLoading(false);
        }
    }

    /**
     * Validate email format
     */
    isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    /**
     * Handle sign-up errors
     */
    handleSignUpError(error) {
        let message = 'Sign-up failed. Please try again.';

        // Supabase error messages
        if (error.message) {
            if (error.message.includes('already registered') || error.message.includes('User already registered')) {
                message = 'An account with this email already exists. Please sign in instead.';
                // Optionally switch to sign-in page after 2 seconds
                setTimeout(() => {
                    this.authManager.showSignIn();
                }, 2000);
            } else if (error.message.includes('invalid email')) {
                message = 'Invalid email address.';
            } else if (error.message.includes('weak password') || error.message.includes('Password')) {
                message = 'Password is too weak. Use at least 6 characters.';
            } else {
                message = error.message;
            }
        }

        this.showError(message);
    }

    /**
     * Show error message
     */
    showError(message) {
        if (this.elements.errorDiv) {
            this.elements.errorDiv.textContent = message;
            this.elements.errorDiv.style.display = 'block';
            this.elements.errorDiv.style.color = '#f87171';
            this.elements.errorDiv.style.background = 'rgba(239,68,68,0.1)';
            this.elements.errorDiv.style.borderColor = 'rgba(239,68,68,0.3)';
        }
    }

    /**
     * Show success message
     */
    showSuccess(message) {
        if (this.elements.errorDiv) {
            this.elements.errorDiv.textContent = message;
            this.elements.errorDiv.style.display = 'block';
            this.elements.errorDiv.style.color = '#4ade80';
            this.elements.errorDiv.style.background = 'rgba(34,197,94,0.1)';
            this.elements.errorDiv.style.borderColor = 'rgba(34,197,94,0.3)';
        }
    }

    /**
     * Hide error message
     */
    hideError() {
        if (this.elements.errorDiv) {
            this.elements.errorDiv.style.display = 'none';
        }
    }

    /**
     * Set loading state
     */
    setLoading(loading) {
        if (this.elements.submitBtn) {
            this.elements.submitBtn.disabled = loading;
            this.elements.submitBtn.textContent = loading ? 'Creating account...' : 'Sign Up';
        }
    }
}

// Export
window.SignUpHandler = SignUpHandler;
