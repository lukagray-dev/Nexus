package nexus.android.parent.auth

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nexus.android.parent.configuration.SupabaseConfig

/**
 * Authentication Manager
 * Coordinates authentication flow using Supabase Auth
 * Firebase is used only for signaling/real-time features
 */
class AuthManager(private val context: Context) {

    private val supabase = SupabaseConfig.client
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Initialize authentication system
     */
    fun init() {
        Log.d(TAG, "AuthManager initialized")
    }

    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            session != null && session.user != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking auth state", e)
            false
        }
    }

    /**
     * Get current user
     */
    suspend fun getCurrentUser(): UserInfo? {
        return try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }

    /**
     * Sign out current user
     */
    suspend fun signOut() {
        try {
            supabase.auth.signOut()
            Log.d(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }

    /**
     * Get appropriate start activity based on auth state
     */
    suspend fun getStartActivity(): Class<*> {
        return if (isAuthenticated()) {
            UserInfoActivity::class.java
        } else {
            SignInActivity::class.java
        }
    }

    companion object {
        private const val TAG = "AuthManager"
    }
}
