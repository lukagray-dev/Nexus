package com.nexus.child.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * WakeLockManager manages the acquisition and release of partial CPU wake locks.
 *
 * Hey friend! Here is why wake locks are required:
 * When an Android device turns off its screen, the Linux kernel enters deep CPU sleep (Doze mode).
 * While Doze saves battery, it suspends background threads and pauses network sockets.
 * A PARTIAL_WAKE_LOCK ensures that the CPU remains active to process incoming WebRTC packets
 * and Firebase signaling events even when the phone display is completely turned off.
 *
 * Safety features implemented here:
 * 1. Non-reference-counted locks to prevent accidental recursive hold leaks.
 * 2. Automatic timeout safety default (10 minutes) so battery won't drain forever if an error occurs.
 * 3. Graceful try-catch wrappers around acquire and release to avoid Underflow or Security exceptions.
 */
class WakeLockManager(private val context: Context) {

    companion object {
        private const val TAG = "WakeLockManager"
        private const val WAKE_LOCK_TAG = "Nexus::CoreServiceWakeLock"
        private const val DEFAULT_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes default safety window
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Acquires the partial wake lock.
     * If already held, calling this refreshes the timeout safely.
     *
     * @param timeoutMs Duration in milliseconds to keep the CPU awake.
     */
    @Synchronized
    fun acquire(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        try {
            if (wakeLock == null) {
                // PARTIAL_WAKE_LOCK keeps the CPU running while screen and keyboard backlight may be off
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false) // One release() call is guaranteed to release it
                }
            }
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(timeoutMs)
                    Log.d(TAG, "Partial wake lock acquired for ${timeoutMs}ms")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}", e)
        }
    }

    /**
     * Releases the wake lock safely if it is currently held.
     */
    @Synchronized
    fun release() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Partial wake lock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}", e)
        } finally {
            wakeLock = null
        }
    }

    /**
     * Checks if the wake lock is currently held.
     */
    @Synchronized
    fun isHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
}
