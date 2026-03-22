package nexus.android.child.components.notification

import android.content.Context
import android.util.Log
import nexus.android.child.commands.NotificationHandler
import org.webrtc.DataChannel

/**
 * NotificationController manages command-based notification sharing.
 * It controls when NotificationListener should send notifications to the parent.
 */
class NotificationController(
    private val context: Context,
    private val dataChannel: DataChannel
) : NotificationHandler {

    @Volatile
    private var isSharing: Boolean = false

    override fun startNotificationSharing() {
        if (isSharing) {
            Log.d(TAG, "⚠️ Notification sharing already active")
            return
        }

        Log.d(TAG, "🚀 Starting notification sharing...")
        
        // Attach DataChannel to NotificationListener
        NotificationListener.attachDataChannel(dataChannel)
        
        // Enable sharing
        NotificationListener.setSharing(true)
        
        isSharing = true
        Log.d(TAG, "✅ Notification sharing started successfully")
    }

    override fun stopNotificationSharing() {
        if (!isSharing) {
            Log.d(TAG, "⚠️ Notification sharing already stopped")
            return
        }

        Log.d(TAG, "🛑 Stopping notification sharing...")
        
        // Disable sharing
        NotificationListener.setSharing(false)
        
        // Detach DataChannel
        NotificationListener.attachDataChannel(null)
        
        isSharing = false
        Log.d(TAG, "✅ Notification sharing stopped successfully")
    }

    fun cleanup() {
        if (isSharing) {
            stopNotificationSharing()
        }
    }

    companion object {
        private const val TAG = "NotificationController"
    }
}
