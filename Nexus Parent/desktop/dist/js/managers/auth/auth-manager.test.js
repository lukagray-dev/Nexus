import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/managers/auth/auth-manager';

describe('AuthManager', () => {
  let authManager;

  beforeEach(() => {
    document.body.innerHTML = `
      <div id="auth-screen"></div>
      <div id="connect-screen" class="hidden"></div>
      <div id="dashboard-screen" class="hidden"></div>
      
      <div id="auth-signin"></div>
      <div id="auth-signup"></div>
      <div id="auth-userinfo"></div>
      <div id="auth-verification"></div>
      <div id="auth-reset-password"></div>
      
      <span id="verification-email"></span>
      <button id="cancel-verification-btn"></button>
      
      <input id="reset-new-password" value="secret" />
      <input id="reset-confirm-password" value="secret" />
      <div id="reset-password-error"></div>
      <button id="reset-password-submit-btn"></button>
      <form id="reset-password-form"></form>
      <button id="cancel-reset-btn"></button>
      
      <input id="child-id-input" />
      <button id="connect-back-btn"></button>
    `;

    authManager = new window.AuthManager();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('initializes fully including waiting for handlers', async () => {
    // Already defined in setup.js mocks
    expect(window.SignInHandler).toBeDefined();
    
    const initSupabaseSpy = vi.spyOn(authManager, 'initializeSupabase');
    const initFirebaseSpy = vi.spyOn(authManager, 'initializeFirebase');
    const initHandlersSpy = vi.spyOn(authManager, 'initHandlers');
    const setupAuthListSpy = vi.spyOn(authManager, 'setupAuthStateListener');
    
    await authManager.init();
    
    expect(initSupabaseSpy).toHaveBeenCalled();
    expect(initFirebaseSpy).toHaveBeenCalled();
    expect(initHandlersSpy).toHaveBeenCalled();
    expect(setupAuthListSpy).toHaveBeenCalled();
  });

  it('handleAuthStateChange routes safely to user info on valid session', async () => {
    await authManager.init();
    
    const session = { user: { email: 'test@test.com', email_confirmed_at: '2023-01-01' } };
    window.supabaseClient.auth.getUser.mockResolvedValue({ data: { user: session.user }, error: null });
    
    const uiSpy = vi.spyOn(authManager, 'showUserInfo');
    
    await authManager.handleAuthStateChange(session);
    
    expect(authManager.currentUser).toEqual(session.user);
    expect(uiSpy).toHaveBeenCalled();
    expect(authManager.userInfoHandler.loadUserData).toHaveBeenCalledWith(session.user);
  });

  it('handleAuthStateChange navigates to login if session is invalid via getUser verification', async () => {
    await authManager.init();
    
    const session = { user: { email: 'test@test.com' } };
    // Simulate invalid token locally vs remotely
    window.supabaseClient.auth.getUser.mockResolvedValue({ data: { user: null }, error: new Error('Invalid') });
    
    const uiSpy = vi.spyOn(authManager, 'showSignIn');
    
    await authManager.handleAuthStateChange(session);
    
    expect(window.supabaseClient.auth.signOut).toHaveBeenCalled();
    expect(authManager.currentUser).toBeNull();
    expect(uiSpy).toHaveBeenCalled();
  });

  it('shows auth views properly', () => {
    authManager.showAuthView('signin');
    
    expect(document.getElementById('auth-signin').classList.contains('hidden')).toBe(false);
    expect(document.getElementById('auth-signup').classList.contains('hidden')).toBe(true);
    
    authManager.showAuthView('signup');
    expect(document.getElementById('auth-signup').classList.contains('hidden')).toBe(false);
    expect(document.getElementById('auth-signin').classList.contains('hidden')).toBe(true);
  });

  it('handles password resets manually successfully', async () => {
    await authManager.init();
    
    const evt = { preventDefault: vi.fn() };
    await authManager.handlePasswordReset(evt);
    
    // Check update
    expect(window.supabaseClient.auth.updateUser).toHaveBeenCalledWith({ password: 'secret' });
    
    // Check if sign out called afterwards
    expect(window.supabaseClient.auth.signOut).toHaveBeenCalled();
    // Error div not shown
    expect(document.getElementById('reset-password-error').style.display).toBe('none');
  });

});
