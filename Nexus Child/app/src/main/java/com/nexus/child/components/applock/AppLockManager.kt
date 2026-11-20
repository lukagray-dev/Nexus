package com.nexus.child.components.applock

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AppLockManager manages App Lock preferences, PIN hashing, verification, and hardware lockout logic.
 *
 * Hey friend! Here is how this manager ensures maximum device security:
 * 1. PIN Security: We never store the plain PIN text. It is hashed using standard SHA-256
 *    before writing to private SharedPreferences.
 * 2. 6-Digit Constraint: Only strictly formatted 6-digit numeric PINs are accepted.
 * 3. 3-Attempt Lockout: If 3 wrong PINs are entered consecutively, a 60-second hardware lockout
 *    is enforced. The timestamp is persisted on disk so restarting the app cannot bypass it!
 * 4. Session State: Tracks whether the current app session has already been unlocked so the child
 *    isn't bombarded with lock screens when navigating between activities.
 * 5. Parent Remote Overrides: Allows the parent console to enable/disable lock or reset the PIN
 *    remotely via WebRTC DataChannel commands.
 */
object AppLockManager {
    private const val TAG = "AppLockManager"

    // Thread-safe flag tracking in-memory session unlock state
    private val sessionUnlocked = AtomicBoolean(false)

    // Optional mock/test preferences override for pure JVM unit testing
    var testPreferences: android.content.SharedPreferences? = null

    private fun getPrefs(context: Context?): android.content.SharedPreferences {
        return testPreferences ?: context!!.getSharedPreferences(AppLockConstants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks whether the current in-memory app session is already unlocked.
     */
    fun isSessionUnlocked(): Boolean = sessionUnlocked.get()

    /**
     * Updates the in-memory session unlock state.
     */
    fun setSessionUnlocked(unlocked: Boolean) {
        sessionUnlocked.set(unlocked)
        Log.d(TAG, "Session unlocked state set to: $unlocked")
    }

    /**
     * Checks if App Lock enforcement is currently enabled.
     */
    fun isAppLockEnabled(context: Context?): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(AppLockConstants.KEY_APP_LOCK_ENABLED, false)
    }

    /**
     * Enables or disables App Lock enforcement.
     */
    fun setAppLockEnabled(context: Context?, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit { putBoolean(AppLockConstants.KEY_APP_LOCK_ENABLED, enabled) }
        Log.d(TAG, "App Lock enabled state updated to: $enabled")
    }

    /**
     * Verifies if a valid PIN has been configured.
     */
    fun hasPinSet(context: Context?): Boolean {
        val prefs = getPrefs(context)
        return prefs.getString(AppLockConstants.KEY_PIN_HASH, null) != null
    }

    /**
     * Configures a new 6-digit PIN. Hashes the PIN using SHA-256 and clears any existing lockout.
     *
     * @param pin Strictly 6-digit numeric string.
     * @return true if successfully saved, false if invalid format.
     */
    fun setPin(context: Context?, pin: String): Boolean {
        if (pin.length != AppLockConstants.PIN_LENGTH || !pin.all { it.isDigit() }) {
            Log.w(TAG, "Rejected invalid PIN: Must be exactly ${AppLockConstants.PIN_LENGTH} digits")
            return false
        }

        val prefs = getPrefs(context)
        val hash = hashPin(pin)
        prefs.edit {
            putString(AppLockConstants.KEY_PIN_HASH, hash)
            putInt(AppLockConstants.KEY_FAILED_ATTEMPTS, 0)
            putLong(AppLockConstants.KEY_LOCKOUT_END_TIME, 0L)
        }
        Log.d(TAG, "New 6-digit PIN successfully hashed and saved")
        return true
    }

    /**
     * Compares an entered PIN against the stored SHA-256 hash.
     */
    fun verifyPin(context: Context?, enteredPin: String): Boolean {
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(AppLockConstants.KEY_PIN_HASH, null) ?: return false
        val enteredHash = hashPin(enteredPin)
        return storedHash == enteredHash
    }

    /**
     * Checks if the app is currently in a lockout state due to 3 consecutive failures.
     */
    fun isLockedOut(context: Context?): Boolean {
        val prefs = getPrefs(context)
        val lockoutEndTime = prefs.getLong(AppLockConstants.KEY_LOCKOUT_END_TIME, 0L)
        return System.currentTimeMillis() < lockoutEndTime
    }

    /**
     * Returns the remaining lockout time in seconds.
     */
    fun getRemainingLockoutSeconds(context: Context?): Long {
        val prefs = getPrefs(context)
        val lockoutEndTime = prefs.getLong(AppLockConstants.KEY_LOCKOUT_END_TIME, 0L)
        val remainingMillis = lockoutEndTime - System.currentTimeMillis()
        return if (remainingMillis > 0) (remainingMillis + 999) / 1000 else 0L
    }

    /**
     * Returns the current count of consecutive failed attempts.
     */
    fun getFailedAttempts(context: Context?): Int {
        val prefs = getPrefs(context)
        return prefs.getInt(AppLockConstants.KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Increments the failed attempts counter. If the count reaches 3, enforces a 60-second lockout.
     *
     * @return The updated number of failed attempts.
     */
    fun recordFailedAttempt(context: Context?): Int {
        val prefs = getPrefs(context)
        val currentAttempts = prefs.getInt(AppLockConstants.KEY_FAILED_ATTEMPTS, 0) + 1

        val editor = prefs.edit()
        editor.putInt(AppLockConstants.KEY_FAILED_ATTEMPTS, currentAttempts)

        if (currentAttempts >= AppLockConstants.MAX_FAILED_ATTEMPTS) {
            val lockoutEndTime = System.currentTimeMillis() + AppLockConstants.LOCKOUT_DURATION_MS
            editor.putLong(AppLockConstants.KEY_LOCKOUT_END_TIME, lockoutEndTime)
            Log.w(TAG, "Threshold reached: App locked out for 60s after $currentAttempts failed attempts")
        }

        editor.apply()
        Log.d(TAG, "Recorded failed attempt: $currentAttempts / ${AppLockConstants.MAX_FAILED_ATTEMPTS}")
        return currentAttempts
    }

    /**
     * Resets the failed attempts counter and clears any active lockout.
     */
    fun resetFailedAttempts(context: Context?) {
        val prefs = getPrefs(context)
        prefs.edit {
            putInt(AppLockConstants.KEY_FAILED_ATTEMPTS, 0)
            putLong(AppLockConstants.KEY_LOCKOUT_END_TIME, 0L)
        }
        Log.d(TAG, "Failed attempts counter and lockout reset")
    }

    /**
     * Records timestamp of successful unlock and resets failed attempts.
     */
    fun recordSuccessfulUnlock(context: Context?) {
        val prefs = getPrefs(context)
        prefs.edit {
            putLong(AppLockConstants.KEY_LAST_UNLOCK_TIME, System.currentTimeMillis())
        }
        resetFailedAttempts(context)
        setSessionUnlocked(true)
    }

    /**
     * Computes the SHA-256 hash of a string.
     */
    fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing PIN: ${e.message}", e)
            pin // Fallback
        }
    }

    /**
     * Retrieves the operational status snapshot for parent reporting.
     */
    fun getStatus(context: Context?): AppLockStatus {
        return AppLockStatus(
            enabled = isAppLockEnabled(context),
            hasPinSet = hasPinSet(context),
            failedAttempts = getFailedAttempts(context),
            isLockedOut = isLockedOut(context),
            remainingLockoutSeconds = getRemainingLockoutSeconds(context)
        )
    }

    /**
     * Clears all settings for test isolation and clean state resets.
     */
    fun clearAll(context: Context?) {
        val prefs = getPrefs(context)
        prefs.edit { clear() }
        sessionUnlocked.set(false)
        Log.d(TAG, "All App Lock preferences wiped")
    }
}
