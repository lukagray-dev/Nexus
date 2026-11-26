package com.nexus.child.components.callalert

import android.util.Log

/**
 * CallAlertCommandHandler bridges WebRTC DataChannel lifecycle and configuration commands
 * (`CALL_ALERT_ON`, `CALL_ALERT_OFF`) with [ICallAlertController].
 */
class CallAlertCommandHandler(
    private val controller: ICallAlertController
) {
    companion object {
        private const val TAG = "CallAlertCommandHandler"

        const val CMD_CALL_ALERT_ON = "CALL_ALERT_ON"
        const val CMD_CALL_ALERT_OFF = "CALL_ALERT_OFF"
    }

    /**
     * Handles DataChannel commands toggling active call alerts on or off.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_CALL_ALERT_ON -> {
                Log.d(TAG, "Received CALL_ALERT_ON command")
                controller.setAlertEnabled(true)
                true
            }
            CMD_CALL_ALERT_OFF -> {
                Log.d(TAG, "Received CALL_ALERT_OFF command")
                controller.setAlertEnabled(false)
                true
            }
            else -> false
        }
    }

    /**
     * Starts call-state monitoring lazily when the parent DataChannel opens.
     */
    fun onDataChannelOpened() {
        Log.d(TAG, "DataChannel opened; starting live call-state alert monitoring...")
        controller.startMonitoring()
    }

    /**
     * Stops call-state monitoring and cancels repeating alerts when peer disconnects.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected; stopping live call-state alert monitoring.")
        controller.stopMonitoring()
    }

    /**
     * Releases worker threads and telephony listeners on service teardown.
     */
    fun cleanup() {
        controller.cleanup()
    }
}
