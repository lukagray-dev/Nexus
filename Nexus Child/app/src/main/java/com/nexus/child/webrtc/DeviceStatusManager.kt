package com.nexus.child.webrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DeviceStatusManager maintains device presence and heartbeat telemetry in Firebase.
 *
 * Hey friend! When the parent console looks at a child device, how does it know
 * whether the child's phone is currently awake and online?
 * 1. We write status = "online-waiting" as soon as the app starts.
 * 2. We register a Firebase onDisconnect() hook. If the phone loses connection,
 *    battery dies, or process gets killed, Firebase servers automatically update
 *    status = "offline" and lastSeen = timestamp without requiring the phone to be alive.
 * 3. Every 30 seconds, we update lastSeen = ServerValue.TIMESTAMP as a live heartbeat.
 */
class DeviceStatusManager(private val deviceId: String) {

    companion object {
        private const val TAG = "DeviceStatusManager"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        // Standard status codes recognized by Nexus Parent Console
        const val STATUS_ONLINE_WAITING = "online-waiting"
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_DISCONNECTED = "disconnected"
        const val STATUS_OFFLINE = "offline"
    }

    private val deviceRef = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference
        .child("child-devices")
        .child(deviceId)

    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isRunning.get()) {
                updateLastSeen()
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Starts presence management:
     * - Configures Firebase onDisconnect handlers for instant offline detection.
     * - Sets the status to online-waiting.
     * - Schedules the recurring 30s heartbeat.
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "DeviceStatusManager is already active for device: $deviceId")
            return
        }

        Log.d(TAG, "Starting presence reporting and heartbeat for device: $deviceId")

        // Configure onDisconnect handlers on the Firebase server
        deviceRef.child("status").onDisconnect().setValue(STATUS_OFFLINE)
        deviceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

        // Mark device as online-waiting
        setStatus(STATUS_ONLINE_WAITING)
        updateLastSeen()

        // Begin recurring heartbeat
        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * Updates the status string under /child-devices/{deviceId}/status.
     *
     * @param status One of STATUS_ONLINE_WAITING, STATUS_CONNECTING, STATUS_CONNECTED, etc.
     */
    fun setStatus(status: String) {
        Log.d(TAG, "Reporting device status change: $status")
        deviceRef.child("status").setValue(status).addOnFailureListener { error ->
            Log.e(TAG, "Failed to update status ($status): ${error.message}")
        }
    }

    /**
     * Refreshes the lastSeen timestamp using Firebase server-side time.
     */
    fun updateLastSeen() {
        deviceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP).addOnFailureListener { error ->
            Log.e(TAG, "Failed to update lastSeen heartbeat: ${error.message}")
        }
    }

    /**
     * Stops heartbeat updates and marks device as offline if cleanly shutting down.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping presence reporting for device: $deviceId")
        mainHandler.removeCallbacks(heartbeatRunnable)
        setStatus(STATUS_OFFLINE)
    }
}
