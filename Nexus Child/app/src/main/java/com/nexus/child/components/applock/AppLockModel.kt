package com.nexus.child.components.applock

import org.json.JSONObject
import java.util.UUID

/**
 * AppLockModel defines the core constants, schemas, and data structures for App Lock security.
 *
 * Hey friend! Here is how this data model works:
 * 1. AppLockConstants: Centralizes all system thresholds (6-digit PIN length, 3-attempt failure
 *    limit, 60-second hardware lockout duration) and WebRTC DataChannel message verbs.
 * 2. AppLockRecord: Captures each physical unlock event on the child device, including timestamp,
 *    correctness, running failed attempt count, lockout state, and optional Base64 front-camera selfie.
 * 3. AppLockStatus: Represents the operational state of the lock system (enabled, PIN configured,
 *    current failure count, remaining lockout seconds).
 */
object AppLockConstants {
    // Security thresholds
    const val PIN_LENGTH = 6
    const val MAX_FAILED_ATTEMPTS = 3
    const val LOCKOUT_DURATION_MS = 60_000L // 1 minute lockout

    // Storage keys
    const val PREFS_NAME = "nexus_app_lock_prefs"
    const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    const val KEY_LOCKOUT_END_TIME = "lockout_end_time"
    const val KEY_LAST_UNLOCK_TIME = "last_unlock_time"

    // Protocol command and response verbs across WebRTC DataChannel
    const val CMD_APPLOCK_ON = "APPLOCK_ON"
    const val CMD_APPLOCK_OFF = "APPLOCK_OFF"
    const val RESP_APPLOCK_STARTED = "APPLOCK_STARTED"
    const val RESP_APPLOCK_STOPPED = "APPLOCK_STOPPED"
    const val RESP_APPLOCK_ERROR_PREFIX = "APPLOCK_ERROR: "

    const val TYPE_APPLOCK_SNAPSHOT = "APPLOCK_SNAPSHOT"
    const val TYPE_APPLOCK_NEW = "APPLOCK_NEW"

    const val CMD_APPLOCK_TOGGLE = "APPLOCK_TOGGLE"
    const val CMD_APPLOCK_SET_PIN = "APPLOCK_SET_PIN"
    const val CMD_APPLOCK_CLEAR = "APPLOCK_CLEAR"
    const val CMD_APPLOCK_GET_STATUS = "APPLOCK_GET_STATUS"

    const val RESP_APPLOCK_STATUS = "APPLOCK_STATUS"
    const val RESP_APPLOCK_PIN_CHANGED = "APPLOCK_PIN_CHANGED"
}

/**
 * Immutable record representing an individual unlock attempt on the child device.
 */
data class AppLockRecord(
    val id: String = "attempt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val timestamp: Long = System.currentTimeMillis(),
    val pinCorrect: Boolean,
    val failedAttempts: Int,
    val isLockedOut: Boolean,
    val selfieBase64: String? = null
) {
    /**
     * Converts this record into a JSON object for storage or WebRTC transmission.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("pinCorrect", pinCorrect)
            put("failedAttempts", failedAttempts)
            put("isLockedOut", isLockedOut)
            selfieBase64?.let { put("selfie", it) }
        }
    }

    companion object {
        /**
         * Reconstructs an AppLockRecord instance from a raw JSON object.
         */
        fun fromJson(json: JSONObject): AppLockRecord {
            return AppLockRecord(
                id = json.optString("id", "attempt_${System.currentTimeMillis()}"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                pinCorrect = json.optBoolean("pinCorrect", false),
                failedAttempts = json.optInt("failedAttempts", 0),
                isLockedOut = json.optBoolean("isLockedOut", false),
                selfieBase64 = if (json.has("selfie") && !json.isNull("selfie")) json.getString("selfie") else null
            )
        }
    }
}

/**
 * Operational status payload sent to the desktop parent console.
 */
data class AppLockStatus(
    val enabled: Boolean,
    val hasPinSet: Boolean,
    val failedAttempts: Int,
    val isLockedOut: Boolean,
    val remainingLockoutSeconds: Long
) {
    /**
     * Serializes this status report into a JSON object for remote monitoring.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", AppLockConstants.RESP_APPLOCK_STATUS)
            put("enabled", enabled)
            put("hasPinSet", hasPinSet)
            put("failedAttempts", failedAttempts)
            put("isLockedOut", isLockedOut)
            put("remainingLockoutSeconds", remainingLockoutSeconds)
        }
    }
}
