package com.nexus.child.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * AuthManager manages anonymous authentication with Firebase.
 *
 * Hey friend! Why anonymous auth?
 * We want the child app to be zero-touch: no login screens, no email entry,
 * and no password prompts. When the child app boots, it signs in anonymously
 * behind the scenes so that Firebase Security Rules can verify the device
 * has a legitimate authenticated token before allowing Realtime Database writes.
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private val mutex = Mutex()

    /**
     * Initializes FirebaseApp programmatically if it has not been initialized yet.
     * This avoids any requirement for a rigid google-services.json file and ensures
     * deterministic configuration across all build environments.
     */
    fun ensureFirebaseInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "Initializing FirebaseApp programmatically...")
            val options = FirebaseOptions.Builder()
                .setApiKey(AppConfig.Firebase.API_KEY)
                .setApplicationId(AppConfig.Firebase.APPLICATION_ID)
                .setDatabaseUrl(AppConfig.Firebase.DATABASE_URL)
                .setProjectId(AppConfig.Firebase.PROJECT_ID)
                .setStorageBucket(AppConfig.Firebase.STORAGE_BUCKET)
                .build()

            FirebaseApp.initializeApp(context.applicationContext, options)
            Log.d(TAG, "FirebaseApp programmatically initialized.")
        }
    }

    /**
     * Ensures that a valid Firebase anonymous user session exists.
     * If a cached user already exists, it is returned immediately.
     * Otherwise, a new anonymous account is created via Firebase Auth.
     *
     * @param context Application context used for initializing FirebaseApp if needed.
     * @return The authenticated FirebaseUser object.
     */
    suspend fun ensureAuthenticated(context: Context): FirebaseUser = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureFirebaseInitialized(context)

            val auth = FirebaseAuth.getInstance()
            val existingUser = auth.currentUser

            if (existingUser != null) {
                Log.d(TAG, "Existing authenticated user found: ${existingUser.uid}")
                return@withContext existingUser
            }

            Log.d(TAG, "No existing user session. Signing in anonymously...")
            try {
                val result = auth.signInAnonymously().await()
                val user = result.user ?: throw IllegalStateException("Firebase signInAnonymously returned null user")
                Log.d(TAG, "Anonymous authentication successful. UID: ${user.uid}")
                return@withContext user
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous authentication failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Returns true if there is an active Firebase authenticated session.
     */
    fun isAuthenticated(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Returns the active user's UID, or null if unauthenticated.
     */
    fun getCurrentUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}
