package nexus.android.child.signaling

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import nexus.android.child.configuration.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DeviceStatusManager"

/**
 * Manages device presence and status reporting to Firebase.
 * 
 * Path: child-devices/{deviceId}
 * Fields: status, lastSeen, createdAt
 */
class DeviceStatusManager(private val deviceId: String) {

    private val db = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference.child("child-devices").child(deviceId)
        
    private val isRunning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isRunning.get()) {
                updateLastSeen()
                handler.postDelayed(this, 30_000) // 30 seconds heartbeat
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return // Already running

        Log.d(TAG, "🚀 Starting device status reporting")
        
        // createdAt is best-effort; server timestamp avoids clock skew
        db.child("createdAt").setValue(ServerValue.TIMESTAMP)
        
        // Set status to online-waiting
        setStatus(ONLINE_WAITING)
        
        // Start heartbeat
        handler.post(heartbeatRunnable)
        
        // Set onDisconnect handler to mark offline if app crashes/disconnects
        db.child("status").onDisconnect().setValue(OFFLINE)
        db.child("lastSeen").onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        
        Log.d(TAG, "🛑 Stopping device status reporting")
        handler.removeCallbacks(heartbeatRunnable)
        setStatus(OFFLINE)
    }

    fun setStatus(status: String) {
        Log.d(TAG, "📡 Setting status: $status")
        db.child("status").setValue(status)
    }

    private fun updateLastSeen() {
        db.child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }
    
    companion object {
        const val ONLINE = "online"
        const val ONLINE_WAITING = "online-waiting"
        const val ONLINE_BACKGROUND_WAITING = "online-background-waiting"
        const val CONNECTING = "connecting"
        const val CONNECTED = "connected"
        const val DISCONNECTED = "disconnected"
        const val OFFLINE = "offline"
    }
}
