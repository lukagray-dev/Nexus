/**
 * Sign-In Handler
 * Manages sign-in functionality using Supabase Auth
 */

class SignInHandler {
    constructor(authManager) {
        this.authManager = authManager;
        this.supabase = authManager.supabase;
        this.elements = {};
        
        this.init();
    }

    /**
     * Initialize sign-in handler
     */
    init() {
        this.cacheElements();
        this.setupEventListeners();
    }

    /**
     * Cache DOM elements
     */
    cacheElements() {
        this.elements.form = document.getElementById('signin-form');
        this.elements.emailInput = document.getElementById('signin-email');
        this.elements.passwordInput = document.getElementById('signin-password');
        this.elements.submitBtn = document.getElementById('signin-submit-btn');
        this.elements.googleBtn = document.getElementById('google-signin-btn');
        this.elements.showSignupBtn = document.getElementById('show-signup-btn');
        this.elements.forgotPasswordBtn = document.getElementById('forgot-password-btn');
        this.elements.errorDiv = document.getElementById('signin-error');
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Email sign-in form
        if (this.elements.form) {
            this.elements.form.addEventListener('submit', (e) => this.handleEmailSignIn(e));
        }

        // Google sign-in
        if (this.elements.googleBtn) {
            this.elements.googleBtn.addEventListener('click', () => this.handleGoogleSignIn());
        }

        // Show sign-up
        if (this.elements.showSignupBtn) {
            this.elements.showSignupBtn.addEventListener('click', () => {
                this.authManager.showSignUp();
            });
        }

        // Forgot password
        if (this.elements.forgotPasswordBtn) {
            this.elements.forgotPasswordBtn.addEventListener('click', () => this.handleForgotPassword());
        }
    }

    /**
     * Handle email/password sign-in
     */
    async handleEmailSignIn(e) {
        e.preventDefault();
        
        const email = this.elements.emailInput.value.trim();
        const password = this.elements.passwordInput.value;

        if (!email || !password) {
            this.showError('Please enter email and password');
            return;
        }

        this.setLoading(true);
        this.hideError();

        try {
            const { data, error } = await this.supabase.auth.signInWithPassword({
                email: email,
                password: password
            });

            if (error) throw error;

            // Check if email is verified
            if (data.user && !data.user.email_confirmed_at) {
                // Sign out the user
                await this.supabase.auth.signOut();
                throw new Error('Email not verified. Please check your email and verify your account before signing in.');
            }

            console.log('✅ Email sign-in successful');
            // Auth state listener will handle navigation
        } catch (error) {
            console.error('❌ Email sign-in failed:', error);
            this.handleSignInError(error);
        } finally {
            this.setLoading(false);
        }
    }

    /**
     * Handle Google sign-in
     */
    async handleGoogleSignIn() {
        this.setLoading(true);
        this.hideError();

        try {
            const { data, error } = await this.supabase.auth.signInWithOAuth({
                provider: 'google'
                // Don't set redirectTo for desktop apps
            });

            if (error) throw error;

            console.log('✅ Google sign-in initiated');
            // OAuth will redirect, so no need to handle navigation here
        } catch (error) {
            console.error('❌ Google sign-in failed:', error);
            this.handleSignInError(error);
            this.setLoading(false);
        }
    }

    /**
     * Handle sign-in errors
     */
    handleSignInError(error) {
        let message = 'Sign-in failed. Please try again.';

        // Supabase error messages
        if (error.message) {
            if (error.message.includes('Invalid login credentials')) {
                message = 'Invalid email or password.';
            } else if (error.message.includes('Email not confirmed') || error.message.includes('Email not verified')) {
                message = 'Please verify your email before signing in. Check your inbox for the verification link.';
            } else if (error.message.includes('User not found')) {
                message = 'No account found with this email.';
            } else if (error.message.includes('Too many requests')) {
                message = 'Too many failed attempts. Please try again later.';
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
            this.elements.submitBtn.textContent = loading ? 'Signing in...' : 'Sign In';
        }
        if (this.elements.googleBtn) {
            this.elements.googleBtn.disabled = loading;
        }
    }

    /**
     * Handle forgot password
     */
    async handleForgotPassword() {
        const email = this.elements.emailInput.value.trim();

        if (!email) {
            this.showError('Please enter your email address first');
            return;
        }

        if (!this.isValidEmail(email)) {
            this.showError('Please enter a valid email address');
            return;
        }

        this.hideError();

        try {
            const { data, error } = await this.supabase.auth.resetPasswordForEmail(email, {
                redirectTo: 'nexus://auth/reset-password'
            });

            if (error) throw error;

            // Supabase always returns success (even if email doesn't exist) for security
            this.showSuccess('If an account exists with this email, you will receive a password reset link.');
            console.log('✅ Password reset requested for:', email);
        } catch (error) {
            console.error('❌ Failed to send reset email:', error);
            
            let message = 'Failed to send password reset email. Please try again.';
            if (error.message) {
                if (error.message.includes('rate limit')) {
                    message = 'Too many requests. Please wait a few minutes.';
                } else if (error.message.includes('Email')) {
                    message = 'Email service is not configured. Please contact support.';
                } else {
                    message = error.message;
                }
            }
            this.showError(message);
        }
    }

    /**
     * Validate email format
     */
    isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
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
}

// Export
window.SignInHandler = SignInHandler;
