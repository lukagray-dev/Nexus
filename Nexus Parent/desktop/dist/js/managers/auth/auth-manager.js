/**
 * Authentication Manager
 * Coordinates authentication flow using Supabase Auth
 * Firebase is used only for signaling/real-time features
 */

class AuthManager {
    constructor() {
        this.supabase = null;
        this.firebaseDB = null;
        this.currentUser = null;
        this.authStateListener = null;
        this.pendingVerification = null; // Store email/password for auto sign-in
        
        // Handler modules
        this.signInHandler = null;
        this.signUpHandler = null;
        this.userInfoHandler = null;
        
        console.log('🔐 AuthManager initialized');
    }

    /**
     * Initialize authentication system
     */
    async init() {
        console.log('🔐 Starting authentication initialization...');
        
        // Initialize Supabase for auth
        await this.initializeSupabase();
        
        // Initialize Firebase for signaling only
        await this.initializeFirebase();
        
        // Wait for handlers to be loaded
        await this.waitForHandlers();
        
        // Initialize handler modules
        this.initHandlers();
        
        // Set up auth state listener
        this.setupAuthStateListener();
        
        // Set up deep link listener for email verification
        this.setupDeepLinkListener();
        
        // Set up password reset listener
        this.setupPasswordResetListener();
        
        console.log('✅ Authentication system ready');
    }

    /**
     * Initialize Supabase (for authentication)
     */
    async initializeSupabase() {
        if (!window.supabaseClient) {
            throw new Error('Supabase client not initialized');
        }
        
        this.supabase = window.supabaseClient;
        console.log('✅ Supabase initialized for authentication');
    }

    /**
     * Initialize Firebase (for signaling only)
     */
    async initializeFirebase() {
        // Wait for Firebase to be loaded
        if (!window.firebase) {
            throw new Error('Firebase SDK not loaded');
        }

        // Check if Firebase is already initialized
        if (window.firebase.apps && window.firebase.apps.length > 0) {
            this.firebaseDB = firebase.database();
            console.log('✅ Using existing Firebase instance for signaling');
            return;
        }

        // Initialize Firebase
        if (window.firebaseConfig) {
            try {
                firebase.initializeApp(window.firebaseConfig);
                this.firebaseDB = firebase.database();
                console.log('✅ Firebase initialized for signaling');
            } catch (error) {
                console.error('❌ Firebase initialization failed:', error);
                throw error;
            }
        } else {
            throw new Error('Firebase config not loaded');
        }
    }

    /**
     * Wait for handler classes to be loaded
     */
    waitForHandlers() {
        return new Promise((resolve) => {
            const checkHandlers = () => {
                if (window.SignInHandler && window.SignUpHandler && window.UserInfoHandler) {
                    resolve();
                } else {
                    setTimeout(checkHandlers, 50);
                }
            };
            checkHandlers();
        });
    }

    /**
     * Initialize handler modules
     */
    initHandlers() {
        this.signInHandler = new window.SignInHandler(this);
        this.signUpHandler = new window.SignUpHandler(this);
        this.userInfoHandler = new window.UserInfoHandler(this);
        console.log('✅ Auth handlers initialized');
    }

    /**
     * Setup Supabase auth state listener
     */
    setupAuthStateListener() {
        // Listen for auth state changes
        this.supabase.auth.onAuthStateChange(async (event, session) => {
            console.log('👤 Auth state changed:', event, session?.user?.email || 'No user');
            await this.handleAuthStateChange(session);
        });
        
        // Check current session
        this.supabase.auth.getSession().then(({ data: { session } }) => {
            if (session) {
                this.handleAuthStateChange(session);
            }
        });
    }

    /**
     * Handle authentication state changes
     */
    async handleAuthStateChange(session) {
        // If we're in password reset flow, don't navigate away
        if (this.isResettingPassword) {
            console.log('⚠️ Password reset in progress, skipping auth state navigation');
            return;
        }
        
        if (session && session.user) {
            // Verify the session is still valid by checking with Supabase
            try {
                const { data: { user }, error } = await this.supabase.auth.getUser();
                
                if (error || !user) {
                    // Session is invalid (user deleted or token expired)
                    console.log('⚠️ Invalid session detected, signing out...');
                    await this.supabase.auth.signOut();
                    this.currentUser = null;
                    this.showSignIn();
                    return;
                }
                
                // Check if email is verified
                if (!user.email_confirmed_at) {
                    console.log('⚠️ Email not verified yet, staying on auth screen');
                    // Don't proceed - user needs to verify email first
                    return;
                }
                
                this.currentUser = user;
                
                // Load user data
                if (this.userInfoHandler) {
                    await this.userInfoHandler.loadUserData(user);
                }
                
                // Show user info screen
                this.showUserInfo();
            } catch (error) {
                console.error('❌ Session validation failed:', error);
                await this.supabase.auth.signOut();
                this.currentUser = null;
                this.showSignIn();
            }
        } else {
            this.currentUser = null;
            this.showSignIn();
        }
    }

    /**
     * Show sign-in screen
     */
    showSignIn() {
        const authScreen = document.getElementById('auth-screen');
        const connectScreen = document.getElementById('connect-screen');
        const dashboardScreen = document.getElementById('dashboard-screen');
        
        if (authScreen) authScreen.classList.remove('hidden');
        if (connectScreen) connectScreen.classList.add('hidden');
        if (dashboardScreen) dashboardScreen.classList.add('hidden');
        
        // Show signin form
        this.showAuthView('signin');
    }

    /**
     * Show sign-up screen
     */
    showSignUp() {
        this.showAuthView('signup');
    }

    /**
     * Show user info screen
     */
    showUserInfo() {
        const authScreen = document.getElementById('auth-screen');
        const connectScreen = document.getElementById('connect-screen');
        const dashboardScreen = document.getElementById('dashboard-screen');
        
        // Make sure auth screen is visible and others are hidden
        if (authScreen) authScreen.classList.remove('hidden');
        if (connectScreen) connectScreen.classList.add('hidden');
        if (dashboardScreen) dashboardScreen.classList.add('hidden');
        
        this.showAuthView('userinfo');
    }

    /**
     * Show verification waiting screen
     */
    showVerificationWaiting(email) {
        const emailDisplay = document.getElementById('verification-email');
        if (emailDisplay) {
            emailDisplay.textContent = email;
        }
        
        // Setup cancel button to go to sign-in
        const cancelBtn = document.getElementById('cancel-verification-btn');
        if (cancelBtn) {
            cancelBtn.textContent = 'Go to Sign In';
            cancelBtn.onclick = () => {
                this.showSignIn();
            };
        }
        
        this.showAuthView('verification');
    }

    /**
     * Show specific auth view
     */
    showAuthView(view) {
        const views = ['signin', 'signup', 'userinfo', 'verification', 'reset-password'];
        views.forEach(v => {
            const el = document.getElementById(`auth-${v}`);
            if (el) {
                if (v === view) {
                    el.classList.remove('hidden');
                    el.classList.add('fade-in');
                } else {
                    el.classList.add('hidden');
                    el.classList.remove('fade-in');
                }
            }
        });
    }

    /**
     * Proceed to connection screen after authentication
     */
    proceedToConnect() {
        const authScreen = document.getElementById('auth-screen');
        const connectScreen = document.getElementById('connect-screen');
        
        if (authScreen) authScreen.classList.add('hidden');
        if (connectScreen) {
            connectScreen.classList.remove('hidden');
            // Focus the child ID input when showing connect screen
            const childIdInput = document.getElementById('child-id-input');
            if (childIdInput) {
                setTimeout(() => childIdInput.focus(), 100);
            }
        }
        
        // Setup back button if not already done
        const backBtn = document.getElementById('connect-back-btn');
        if (backBtn && !backBtn.hasAttribute('data-listener')) {
            backBtn.setAttribute('data-listener', 'true');
            backBtn.addEventListener('click', () => this.showUserInfo());
        }
    }

    /**
     * Sign out current user
     */
    async signOut() {
        try {
            await this.supabase.auth.signOut();
            console.log('✅ User signed out');
        } catch (error) {
            console.error('❌ Sign out failed:', error);
            throw error;
        }
    }

    /**
     * Get current user
     */
    getCurrentUser() {
        return this.currentUser;
    }

    /**
     * Check if user is authenticated
     */
    isAuthenticated() {
        return !!this.currentUser;
    }

    /**
     * Cleanup
     */
    cleanup() {
        // Supabase handles cleanup automatically
    }

    /**
     * Setup deep link listener for email verification
     */
    setupDeepLinkListener() {
        if (window.require) {
            const { ipcRenderer } = window.require('electron');
            
            ipcRenderer.on('auth-callback', async (event, { token, type }) => {
                console.log('🔗 Deep link received, attempting auto sign-in...');
                
                // Check if we have pending verification credentials
                if (!this.pendingVerification) {
                    console.log('⚠️ No pending verification found, showing sign-in');
                    alert('Email verified! Please sign in with your email and password.');
                    this.showSignIn();
                    return;
                }
                
                try {
                    const { email, password } = this.pendingVerification;
                    
                    // Wait a moment for Supabase to process the verification
                    await new Promise(resolve => setTimeout(resolve, 2000));
                    
                    // Try to sign in with the stored credentials
                    const { data, error } = await this.supabase.auth.signInWithPassword({
                        email: email,
                        password: password
                    });
                    
                    if (error) throw error;
                    
                    console.log('✅ Auto sign-in successful!');
                    // Clear pending verification
                    this.pendingVerification = null;
                    // Auth state listener will handle showing user info
                    
                } catch (error) {
                    console.error('⚠️ Auto sign-in failed:', error);
                    // Fallback: Show sign-in page
                    this.pendingVerification = null;
                    alert('Email verified! Please sign in with your email and password.');
                    this.showSignIn();
                }
            });
        }
    }

    /**
     * Store credentials for auto sign-in after verification
     */
    setPendingVerification(email, password) {
        this.pendingVerification = { email, password };
    }

    /**
     * Setup password reset listener
     */
    setupPasswordResetListener() {
        if (window.require) {
            const { ipcRenderer } = window.require('electron');
            
            ipcRenderer.on('password-reset-callback', async (event, { token, type, accessToken, refreshToken }) => {
                console.log('🔗 Password reset callback received');
                console.log('Token:', token, 'Type:', type, 'Access Token:', accessToken);
                
                try {
                    // Set a flag to prevent auth state from navigating away
                    this.isResettingPassword = true;
                    
                    // If we have access and refresh tokens, set the session
                    if (accessToken && refreshToken) {
                        const { error } = await this.supabase.auth.setSession({
                            access_token: accessToken,
                            refresh_token: refreshToken
                        });
                        
                        if (error) throw error;
                        console.log('✅ Session set for password reset');
                    }
                    
                    // Show password reset form (after session is set)
                    this.showPasswordResetForm();
                    
                } catch (error) {
                    console.error('❌ Failed to set session:', error);
                    this.isResettingPassword = false;
                    alert('Password reset link is invalid or expired. Please request a new one.');
                    this.showSignIn();
                }
            });
        }
        
        // Setup reset password form
        const resetForm = document.getElementById('reset-password-form');
        const cancelBtn = document.getElementById('cancel-reset-btn');
        
        if (resetForm) {
            resetForm.addEventListener('submit', (e) => this.handlePasswordReset(e));
        }
        
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => {
                this.isResettingPassword = false;
                this.showSignIn();
            });
        }
    }

    /**
     * Show password reset form
     */
    showPasswordResetForm() {
        this.showAuthView('reset-password');
    }

    /**
     * Handle password reset form submission
     */
    async handlePasswordReset(e) {
        e.preventDefault();
        
        const newPassword = document.getElementById('reset-new-password').value;
        const confirmPassword = document.getElementById('reset-confirm-password').value;
        const errorDiv = document.getElementById('reset-password-error');
        const submitBtn = document.getElementById('reset-password-submit-btn');
        
        // Validation
        if (newPassword.length < 6) {
            errorDiv.textContent = 'Password must be at least 6 characters';
            errorDiv.style.display = 'block';
            return;
        }
        
        if (newPassword !== confirmPassword) {
            errorDiv.textContent = 'Passwords do not match';
            errorDiv.style.display = 'block';
            return;
        }
        
        errorDiv.style.display = 'none';
        submitBtn.disabled = true;
        submitBtn.textContent = 'Updating...';
        
        try {
            // Update password
            const { error } = await this.supabase.auth.updateUser({
                password: newPassword
            });
            
            if (error) throw error;
            
            console.log('✅ Password updated successfully');
            alert('Password updated successfully! You can now sign in with your new password.');
            
            // Clear the reset flag
            this.isResettingPassword = false;
            
            // Sign out to clear the recovery session
            await this.supabase.auth.signOut();
            
            // Clear form and show sign-in
            document.getElementById('reset-password-form').reset();
            this.showSignIn();
            
        } catch (error) {
            console.error('❌ Password update failed:', error);
            errorDiv.textContent = error.message || 'Failed to update password. Please try again.';
            errorDiv.style.display = 'block';
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Update Password';
        }
    }
}

// Export
window.AuthManager = AuthManager;
