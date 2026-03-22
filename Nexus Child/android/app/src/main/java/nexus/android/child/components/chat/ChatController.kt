package nexus.android.child.components.chat

import android.content.Context
import android.util.Log
import nexus.android.child.commands.ChatHandler
import org.webrtc.DataChannel
import java.nio.ByteBuffer

/**
 * ChatController manages command-based chat monitoring.
 * It controls when ChatMonitor should send chat data to the parent.
 */
class ChatController(
    private val context: Context,
    private val dataChannel: DataChannel
) : ChatHandler {

    @Volatile
    private var isMonitoring: Boolean = false

    private val dataChannelClient = object : DataChannelClient {
        override fun send(jsonPayload: String): Boolean {
            return try {
                if (dataChannel.state() != DataChannel.State.OPEN) {
                    Log.w(TAG, "DataChannel not open, cannot send chat data")
                    return false
                }
                
                val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
                val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
                val sent = dataChannel.send(buffer)
                
                if (sent) {
                    Log.d(TAG, "Chat data sent successfully (${bytes.size} bytes)")
                } else {
                    Log.w(TAG, "Failed to send chat data")
                }
                
                sent
            } catch (e: Exception) {
                Log.e(TAG, "Error sending chat data", e)
                false
            }
        }
    }

    override fun startChatMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "⚠️ Chat monitoring already active")
            return
        }

        Log.d(TAG, "🚀 Starting chat monitoring...")
        
        // Attach DataChannel to ChatMonitor
        ChatMonitor.instance.setDataChannelClient(dataChannelClient)
        
        // Enable monitoring
        setMonitoring(true)
        
        isMonitoring = true
        Log.d(TAG, "✅ Chat monitoring started successfully")
    }

    override fun stopChatMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "⚠️ Chat monitoring already stopped")
            return
        }

        Log.d(TAG, "🛑 Stopping chat monitoring...")
        
        // Disable monitoring
        setMonitoring(false)
        
        // Detach DataChannel
        ChatMonitor.instance.setDataChannelClient(null)
        
        isMonitoring = false
        Log.d(TAG, "✅ Chat monitoring stopped successfully")
    }

    fun cleanup() {
        if (isMonitoring) {
            stopChatMonitoring()
        }
    }

    companion object {
        private const val TAG = "ChatController"
        
        @Volatile
        private var isMonitoringEnabled: Boolean = false
        
        /**
         * Check if monitoring is enabled (used by ChatMonitor to decide whether to send data)
         */
        fun isMonitoringEnabled(): Boolean = isMonitoringEnabled
        
        /**
         * Set monitoring state
         */
        private fun setMonitoring(enabled: Boolean) {
            isMonitoringEnabled = enabled
            Log.d(TAG, "Chat monitoring ${if (enabled) "enabled" else "disabled"}")
        }
    }
}
