package com.nexus.parent.webrtc

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
 * AuthManager.kt - Anonymous Firebase Authentication lifecycle manager for Nexus Parent Android.
 *
 * Hey friend! Nexus has zero login/signup friction, yet Firebase Realtime Database Security Rules
 * require every connecting peer to hold a valid Firebase Auth JWT token before reading or writing
 * `/child-devices/` or `/signaling/`.
 *
 * This singleton programmatically initializes FirebaseApp from root `.env` BuildConfig fields
 * (no rigid `google-services.json` required) and performs a mutex-guarded anonymous sign-in.
 */
object AuthManager {

    private const val TAG = "ParentAuthManager"
    private val authMutex = Mutex()

    fun ensureFirebaseInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            require(AppConfig.Firebase.API_KEY.isNotBlank() && AppConfig.Firebase.DATABASE_URL.isNotBlank()) {
                "Firebase configuration missing. Ensure root .env contains apiKey, databaseURL, projectId, and appId."
            }
            val options = FirebaseOptions.Builder()
                .setApiKey(AppConfig.Firebase.API_KEY)
                .setApplicationId(AppConfig.Firebase.APPLICATION_ID)
                .setDatabaseUrl(AppConfig.Firebase.DATABASE_URL)
                .setProjectId(AppConfig.Firebase.PROJECT_ID)
                .setStorageBucket(AppConfig.Firebase.STORAGE_BUCKET)
                .build()

            FirebaseApp.initializeApp(context.applicationContext, options)
            Log.d(TAG, "FirebaseApp initialized programmatically.")
        }
    }

    /**
     * Ensures an active Firebase anonymous session exists, joining any in-flight sign-in.
     */
    suspend fun ensureAuthenticated(context: Context): FirebaseUser = withContext(Dispatchers.IO) {
        authMutex.withLock {
            ensureFirebaseInitialized(context)
            val auth = FirebaseAuth.getInstance()
            val existing = auth.currentUser
            if (existing != null) {
                return@withContext existing
            }

            Log.d(TAG, "Initiating anonymous Firebase authentication...")
            val result = auth.signInAnonymously().await()
            val user = result.user
                ?: throw IllegalStateException("Firebase anonymous sign-in returned a null user.")
            Log.d(TAG, "Anonymous authentication successful. UID: ${user.uid}")
            user
        }
    }
}
