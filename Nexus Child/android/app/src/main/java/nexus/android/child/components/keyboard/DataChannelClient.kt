package nexus.android.child.components.keyboard

/**
 * DataChannel client interface for KeyboardMonitor.
 */
interface DataChannelClient {
    /**
     * Send a UTF-8 JSON string over the existing WebRTC DataChannel.
     * Return true if accepted for send; false if channel not ready.
     */
    fun send(jsonPayload: String): Boolean
}
