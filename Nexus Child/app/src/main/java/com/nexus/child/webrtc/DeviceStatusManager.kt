package com.nexus.child.webrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DeviceStatusManager maintains device presence and heartbeat telemetry in Firebase.
 *
 * Hey friend! Here is how robust presence works across mobile network handovers and Doze mode:
 * 1. Mobile networks constantly experience transient TCP drops (cell tower handovers, WiFi-to-LTE switches,
 *    and aggressive 60-second carrier NAT timeouts).
 * 2. When the socket drops, Firebase backend fires the registered onDisconnect() hook, marking the child "offline".
 * 3. When the socket reconnects automatically in the background, we listen to the special `/.info/connected` path.
 * 4. The moment `connected == true`, we immediately re-arm the server-side onDisconnect hooks and re-assert
 *    our current status (e.g. "online-waiting" or "connected") so the device never stays "offline" in Firebase!
 * 5. Every 30 seconds, we publish a live timestamp heartbeat to `lastSeen` and verify status alignment.
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

    private val dbInstance = FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)

    private val deviceRef = dbInstance
        .reference
        .child("child-devices")
        .child(deviceId)

    private val connectedRef = dbInstance
        .reference
        .child(".info/connected")

    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentDesiredStatus: String = STATUS_ONLINE_WAITING

    private var connectedListener: ValueEventListener? = null

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isRunning.get()) {
                updateLastSeen()
                // Periodic re-assertion guarantees status stays consistent even if silent socket reset occurred
                deviceRef.child("status").setValue(currentDesiredStatus)
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Starts presence management:
     * - Attaches a persistent listener to `/.info/connected` to survive network drops.
     * - Configures Firebase onDisconnect handlers for instant offline detection when killed.
     * - Sets the status to online-waiting.
     * - Schedules the recurring 30s heartbeat.
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "DeviceStatusManager is already active for device: $deviceId")
            return
        }

        Log.d(TAG, "Starting presence reporting and heartbeat for device: $deviceId")

        // 1. Mark device as online-waiting
        currentDesiredStatus = STATUS_ONLINE_WAITING
        setStatus(STATUS_ONLINE_WAITING)
        updateLastSeen()

        // 2. Attach persistent .info/connected listener to re-arm onDisconnect and restore presence on every reconnect
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase .info/connected state changed: $isConnected (desiredStatus=$currentDesiredStatus)")

                if (isConnected && isRunning.get()) {
                    // Re-arm onDisconnect handlers on the Firebase server for the freshly opened socket
                    deviceRef.child("status").onDisconnect().setValue(STATUS_OFFLINE)
                    deviceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                    // Re-assert our current operational status immediately
                    Log.d(TAG, "Socket connected. Re-asserting status: $currentDesiredStatus")
                    deviceRef.child("status").setValue(currentDesiredStatus)
                    updateLastSeen()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, ".info/connected listener cancelled: ${error.message}")
            }
        }
        connectedRef.addValueEventListener(connectedListener as ValueEventListener)

        // 3. Begin recurring heartbeat
        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * Updates the status string under /child-devices/{deviceId}/status and caches currentDesiredStatus.
     *
     * @param status One of STATUS_ONLINE_WAITING, STATUS_CONNECTING, STATUS_CONNECTED, etc.
     */
    fun setStatus(status: String) {
        currentDesiredStatus = status
        Log.d(TAG, "Reporting device status change: $status")
        deviceRef.child("status").setValue(status).addOnFailureListener { error ->
            Log.e(TAG, "Failed to update status ($status): ${error.message}")
        }
    }

    /**
     * Returns the currently asserted device status.
     */
    fun getCurrentStatus(): String = currentDesiredStatus

    /**
     * Refreshes the lastSeen timestamp using Firebase server-side time.
     */
    fun updateLastSeen() {
        deviceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP).addOnFailureListener { error ->
            Log.e(TAG, "Failed to update lastSeen heartbeat: ${error.message}")
        }
    }

    /**
     * Stops heartbeat updates, tears down .info/connected listener, and marks device as offline.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping presence reporting for device: $deviceId")
        mainHandler.removeCallbacks(heartbeatRunnable)

        connectedListener?.let {
            connectedRef.removeEventListener(it)
            connectedListener = null
        }

        // Cancel onDisconnect handlers to avoid conflicting with explicit offline write
        deviceRef.child("status").onDisconnect().cancel()
        deviceRef.child("lastSeen").onDisconnect().cancel()

        currentDesiredStatus = STATUS_OFFLINE
        setStatus(STATUS_OFFLINE)
    }
}
