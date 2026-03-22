package nexus.android.child.applock

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import nexus.android.child.configuration.AppConfig

/**
 * Manages app lock state, PIN verification, and lockout logic.
 * PIN can be modified remotely from ParentElectronApp via Firebase.
 */
object AppLockManager {
    private const val TAG = "AppLockManager"
    private const val PREFS_NAME = "app_lock_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_END_TIME = "lockout_end_time"
    private const val KEY_LAST_UNLOCK_TIME = "last_unlock_time"
    
    // Lockout configuration
    const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    private const val LOCKOUT_INCREMENT_MS = 2 * 60 * 1000L // Add 2 min per additional failure
    
    // Default PIN (should be changed by parent)
    private const val DEFAULT_PIN = "1234"
    
    private var firebaseListener: ValueEventListener? = null
    private var deviceId: String? = null

    /**
     * Initialize the app lock manager and start listening for remote PIN changes.
     */
    fun initialize(context: Context, deviceId: String) {
        this.deviceId = deviceId
        
        // Set default PIN if not set
        if (!hasPinSet(context)) {
            setPin(context, DEFAULT_PIN)
            setAppLockEnabled(context, true)
        }
        
        // Start listening for remote PIN changes from Firebase
        startFirebaseListener(context, deviceId)
    }

    /**
     * Check if app lock is enabled.
     */
    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    /**
     * Enable or disable app lock.
     */
    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) }
        Log.d(TAG, "App lock enabled: $enabled")
    }

    /**
     * Check if a PIN has been set.
     */
    fun hasPinSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * Set a new PIN (stores hash for security).
     */
    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = hashPin(pin)
        prefs.edit { putString(KEY_PIN_HASH, hash) }
        Log.d(TAG, "PIN updated")
    }

    /**
     * Verify if the entered PIN is correct.
     */
    fun verifyPin(context: Context, enteredPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val enteredHash = hashPin(enteredPin)
        return storedHash == enteredHash
    }

    /**
     * Check if the app is currently locked out due to too many failed attempts.
     */
    fun isLockedOut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIME, 0L)
        return System.currentTimeMillis() < lockoutEndTime
    }

    /**
     * Get remaining lockout time in milliseconds.
     */
    fun getRemainingLockoutTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIME, 0L)
        val remaining = lockoutEndTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /**
     * Get the number of failed attempts.
     */
    fun getFailedAttempts(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Record a failed PIN attempt and apply lockout if necessary.
     */
    fun recordFailedAttempt(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        
        prefs.edit { putInt(KEY_FAILED_ATTEMPTS, currentAttempts) }
        
        if (currentAttempts >= MAX_FAILED_ATTEMPTS) {
            // Calculate lockout duration (increases with more failures)
            val extraAttempts = currentAttempts - MAX_FAILED_ATTEMPTS
            val lockoutDuration = LOCKOUT_DURATION_MS + (extraAttempts * LOCKOUT_INCREMENT_MS)
            val lockoutEndTime = System.currentTimeMillis() + lockoutDuration
            
            prefs.edit { putLong(KEY_LOCKOUT_END_TIME, lockoutEndTime) }
            Log.w(TAG, "App locked out for ${lockoutDuration / 1000}s after $currentAttempts failed attempts")
        }
        
        Log.d(TAG, "Failed attempt recorded: $currentAttempts total")
    }

    /**
     * Reset failed attempts counter (called on successful unlock).
     */
    fun resetFailedAttempts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_FAILED_ATTEMPTS, 0)
            putLong(KEY_LOCKOUT_END_TIME, 0L)
        }
        Log.d(TAG, "Failed attempts reset")
    }

    /**
     * Record successful unlock time.
     */
    fun recordUnlockTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis()) }
        resetFailedAttempts(context)
    }

    /**
     * Get last successful unlock time.
     */
    fun getLastUnlockTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UNLOCK_TIME, 0L)
    }

    /**
     * Simple hash function for PIN (in production, use proper crypto).
     */
    private fun hashPin(pin: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(pin.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing PIN", e)
            pin // Fallback to plain text (not recommended)
        }
    }

    /**
     * Start listening for remote PIN changes from Firebase.
     */
    private fun startFirebaseListener(context: Context, deviceId: String) {
        try {
            val database = FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)
            val appLockRef = database.reference.child("devices").child(deviceId).child("appLock")
            
            firebaseListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // Check for PIN update
                        val newPin = snapshot.child("pin").getValue(String::class.java)
                        if (!newPin.isNullOrBlank()) {
                            setPin(context, newPin)
                            Log.d(TAG, "PIN updated from Firebase")
                            // Clear the PIN from Firebase after reading
                            snapshot.child("pin").ref.removeValue()
                        }
                        
                        // Check for enabled state update
                        val enabled = snapshot.child("enabled").getValue(Boolean::class.java)
                        if (enabled != null) {
                            setAppLockEnabled(context, enabled)
                            Log.d(TAG, "App lock enabled state updated from Firebase: $enabled")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing Firebase data", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                }
            }
            
            appLockRef.addValueEventListener(firebaseListener!!)
            Log.d(TAG, "Firebase listener started for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Firebase listener", e)
        }
    }

    /**
     * Stop Firebase listener (call on cleanup).
     */
    fun stopFirebaseListener() {
        try {
            deviceId?.let { id ->
                firebaseListener?.let { listener ->
                    val database = FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)
                    val appLockRef = database.reference.child("devices").child(id).child("appLock")
                    appLockRef.removeEventListener(listener)
                }
            }
            firebaseListener = null
            Log.d(TAG, "Firebase listener stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Firebase listener", e)
        }
    }

    /**
     * Clear all app lock settings (for testing/reset).
     */
    fun clearAllSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
        Log.d(TAG, "All app lock settings cleared")
    }
}
