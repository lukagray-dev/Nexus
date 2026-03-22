package nexus.android.child.applock

import android.util.Log
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.ByteBuffer

/**
 * Singleton to send app lock alerts via the WebRTC data channel.
 * This replaces Firebase for app lock alert transmission.
 */
object AppLockAlertSender {
    private const val TAG = "AppLockAlertSender"
    
    @Volatile
    private var dataChannel: DataChannel? = null
    
    /**
     * Attach the data channel for sending alerts.
     * Called from BackgroundService when data channel is created.
     */
    fun attachDataChannel(channel: DataChannel?) {
        dataChannel = channel
        Log.d(TAG, "DataChannel attached: ${channel != null}")
    }
    
    /**
     * Detach the data channel.
     * Called when connection is closed.
     */
    fun detachDataChannel() {
        dataChannel = null
        Log.d(TAG, "DataChannel detached")
    }
    
    /**
     * Send an app lock alert to the parent.
     * 
     * @param timestamp When the attempt occurred
     * @param pinCorrect Whether the PIN was correct
     * @param failedAttempts Number of failed attempts
     * @param isLockedOut Whether the device is locked out
     * @param selfieBase64 Optional base64-encoded selfie image
     * @param deviceId Optional device identifier
     * @return true if sent successfully, false otherwise
     */
    fun sendAlert(
        timestamp: Long,
        pinCorrect: Boolean,
        failedAttempts: Int,
        isLockedOut: Boolean,
        selfieBase64: String? = null,
        deviceId: String? = null
    ): Boolean {
        val channel = dataChannel
        
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "Cannot send alert: DataChannel not available or not open")
            return false
        }
        
        return try {
            val alertJson = JSONObject().apply {
                put("type", "APPLOCK_ALERT")
                put("timestamp", timestamp)
                put("pinCorrect", pinCorrect)
                put("failedAttempts", failedAttempts)
                put("isLockedOut", isLockedOut)
                deviceId?.let { put("deviceId", it) }
                selfieBase64?.let { put("selfie", it) }
            }
            
            val bytes = alertJson.toString().toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            val sent = channel.send(buffer)
            
            if (sent) {
                Log.d(TAG, "App lock alert sent successfully (pinCorrect=$pinCorrect, failedAttempts=$failedAttempts)")
            } else {
                Log.e(TAG, "Failed to send app lock alert")
            }
            
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending app lock alert", e)
            false
        }
    }
    
    /**
     * Check if the data channel is available and open.
     */
    fun isAvailable(): Boolean {
        return dataChannel?.state() == DataChannel.State.OPEN
    }
}
