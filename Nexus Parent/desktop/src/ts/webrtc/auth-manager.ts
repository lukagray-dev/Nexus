/**
 * @file auth-manager.ts
 * @description Anonymous Firebase Authentication lifecycle manager for Nexus Parent Desktop.
 *
 * Hey friend! Why does the desktop parent app use anonymous authentication?
 * In Nexus, we want zero signup friction: the parent launches the app, enters the
 * 12-digit Child ID, and immediately connects.
 *
 * Firebase Realtime Database Security Rules enforce that any client reading or writing
 * to `/child-devices/` or `/signaling/` must possess a valid Firebase Auth token.
 * By signing in anonymously in the background, we obtain a cryptographically signed
 * JWT token from Firebase without requiring passwords or OAuth dialogs.
 */

import { signInAnonymously, onAuthStateChanged, type User } from 'firebase/auth';
import { getFirebaseAuth } from './firebase-config.js';

class DesktopAuthManager {
  private currentUser: User | null = null;
  private pendingAuthPromise: Promise<User> | null = null;
  private isInitialized = false;

  constructor() {
    this.initAuthStateListener();
  }

  /**
   * Initializes the persistent auth state change listener.
   */
  private initAuthStateListener(): void {
    const auth = getFirebaseAuth();
    onAuthStateChanged(auth, (user) => {
      this.currentUser = user;
      this.isInitialized = true;
      if (user) {
        console.log('[AuthManager] Firebase anonymous session active. UID:', user.uid);
      } else {
        console.log('[AuthManager] Firebase session currently signed out.');
      }
    });
  }

  /**
   * Ensures that the desktop client has an active authenticated session.
   * If a session already exists, it is returned immediately.
   * If an authentication request is already in-flight, it joins the existing promise.
   *
   * @returns A promise resolving to the authenticated Firebase User.
   */
  public async ensureAuthenticated(): Promise<User> {
    const auth = getFirebaseAuth();

    // Fast path: already signed in
    if (auth.currentUser) {
      this.currentUser = auth.currentUser;
      return auth.currentUser;
    }

    // Join pending request if one is already running to avoid duplicate calls
    if (this.pendingAuthPromise) {
      return this.pendingAuthPromise;
    }

    this.pendingAuthPromise = (async () => {
      try {
        console.log('[AuthManager] Initiating anonymous sign-in...');
        const userCredential = await signInAnonymously(auth);
        this.currentUser = userCredential.user;
        console.log('[AuthManager] Anonymous authentication successful. UID:', this.currentUser.uid);
        return this.currentUser;
      } catch (error) {
        console.error('[AuthManager] Anonymous authentication failed:', error);
        throw error;
      } finally {
        this.pendingAuthPromise = null;
      }
    })();

    return this.pendingAuthPromise;
  }

  /**
   * Returns true if the authentication state listener has processed its initial event.
   */
  public isAuthReady(): boolean {
    return this.isInitialized;
  }

  /**
   * Returns the current authenticated User, or null if unauthenticated.
   */
  public getUser(): User | null {
    return this.currentUser;
  }

  /**
   * Returns true if there is an active session.
   */
  public isAuthenticated(): boolean {
    return this.currentUser !== null;
  }
}

export const authManager = new DesktopAuthManager();
