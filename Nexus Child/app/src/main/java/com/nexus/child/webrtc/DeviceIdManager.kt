package com.nexus.child.webrtc

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.math.min
import kotlin.random.asKotlinRandom

/**
 * DeviceIdManager handles the generation, validation, and local persistence
 * of the unique 12-digit Child ID.
 *
 * Hey friend! Here is how device pairing works in Nexus:
 * When a child installs the app for the very first time, we need a clean, human-friendly
 * 12-digit identifier (like 4821 9402 1104) that the parent can type on their desktop.
 * To make sure two phones never collide, we:
 * 1. Cryptographically generate 12 random digits using SecureRandom.
 * 2. Query Firebase Realtime Database at /child-devices/{candidate} to confirm nobody owns it.
 * 3. Atomically register device metadata (phone model, Android version, initial status).
 * 4. Persist the ID in Android SharedPreferences so the phone keeps the exact same ID forever.
 */
object DeviceIdManager {

    private const val TAG = "DeviceIdManager"
    private const val PREFS_NAME = "nexus_child_prefs"
    private const val KEY_DEVICE_ID = "nexus_device_id"

    @Volatile
    private var cachedId: String? = null
    private val lock = Any()

    /**
     * Thread-safe cryptographic random generator wrapped for Kotlin idiomatic calls.
     */
    private val secureRandom = SecureRandom().asKotlinRandom()

    /**
     * Retrieves the existing Device ID from memory or SharedPreferences.
     * If no ID has ever been generated, it creates a new unique one against Firebase.
     *
     * @param context Application context used for SharedPreferences access.
     * @return The 12-digit raw numeric device ID string.
     */
    suspend fun getOrGenerateDeviceId(context: Context): String = withContext(Dispatchers.IO) {
        // Fast path: return in-memory cached ID if already initialized
        cachedId?.let { return@withContext it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Check persistent local storage
        val existingId = prefs.getString(KEY_DEVICE_ID, null)
        if (!existingId.isNullOrBlank()) {
            Log.d(TAG, "Loaded existing Device ID from SharedPreferences: $existingId")
            synchronized(lock) {
                cachedId = existingId
            }
            return@withContext existingId
        }

        // Slow path: generate a new unique 12-digit ID against Firebase
        Log.d(TAG, "No existing Device ID found. Generating a new unique 12-digit ID...")
        val newId = generateUniqueIdAgainstFirebase(prefs)

        synchronized(lock) {
            cachedId = newId
        }
        return@withContext newId
    }

    /**
     * Generates candidate 12-digit IDs in a loop and verifies uniqueness against
     * the /child-devices/ Firebase node. Once a collision-free ID is confirmed,
     * writes the initial metadata and saves it to local SharedPreferences.
     */
    private suspend fun generateUniqueIdAgainstFirebase(
        prefs: android.content.SharedPreferences
    ): String {
        val database = FirebaseDatabase
            .getInstance(AppConfig.Firebase.DATABASE_URL)
            .reference
            .child("child-devices")

        var attempt = 0
        while (true) {
            attempt++
            val candidate = generate12DigitString()
            Log.d(TAG, "Attempt $attempt: Testing candidate Device ID: $candidate")

            try {
                // Query Firebase RTDB to check if this candidate already exists
                val snapshot = database.child(candidate).get().await()

                if (!snapshot.exists()) {
                    Log.d(TAG, "Candidate $candidate is unique. Registering metadata...")

                    // Only publish essential presence metadata to Firebase
                    val metadata = mapOf(
                        "createdAt" to ServerValue.TIMESTAMP,
                        "lastSeen" to ServerValue.TIMESTAMP,
                        "status" to "online-waiting"
                    )

                    // Atomically save to local SharedPreferences before publishing
                    prefs.edit {
                        putString(KEY_DEVICE_ID, candidate)
                    }

                    // Write initial node to Firebase Realtime Database
                    database.child(candidate).setValue(metadata).await()
                    Log.d(TAG, "Device ID successfully registered: $candidate")
                    return candidate
                } else {
                    Log.w(TAG, "Candidate $candidate already exists in Firebase. Retrying...")
                }
            } catch (e: Exception) {
                val backoffMs = calculateBackoff(attempt)
                Log.e(TAG, "Firebase query error during ID validation: ${e.message}. Retrying in ${backoffMs}ms", e)
                delay(backoffMs)
            }
        }
    }

    /**
     * Produces a string of 12 random decimal digits (0-9).
     * Leading zeros are fully supported so all 1,000,000,000,000 combinations are valid.
     */
    private fun generate12DigitString(): String {
        return buildString(12) {
            repeat(12) {
                append(secureRandom.nextInt(0, 10))
            }
        }
    }

    /**
     * Calculates exponential backoff with random jitter to prevent server thundering herd.
     */
    private fun calculateBackoff(attempt: Int): Long {
        val base = min(60_000L, 1000L * (1 shl min(10, attempt)))
        val jitter = secureRandom.nextLong(0, 3000L)
        return base + jitter
    }

    /**
     * Formats a raw 12-digit string into the standard visual format: "XXXX XXXX XXXX".
     * E.g. "482194021104" -> "4821 9402 1104"
     */
    fun format(rawId: String): String {
        val digitsOnly = rawId.filter { it.isDigit() }
        return if (digitsOnly.length == 12) {
            "${digitsOnly.substring(0, 4)} ${digitsOnly.substring(4, 8)} ${digitsOnly.substring(8, 12)}"
        } else {
            rawId
        }
    }
}
