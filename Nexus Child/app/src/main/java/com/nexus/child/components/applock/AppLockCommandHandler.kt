package com.nexus.child.components.applock

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * AppLockCommandHandler decodes and executes remote App Lock commands received over WebRTC DataChannel.
 *
 * Hey friend! Here is how this command handler functions as the parent's high command router:
 * 1. Stream Control: Responds to "APPLOCK_ON" and "APPLOCK_OFF" to start/stop the live attempt feed.
 * 2. Remote Lock Override: Intercepts "APPLOCK_TOGGLE" packets to enable or disable App Lock
 *    remotely from the parent desktop dashboard.
 * 3. Remote PIN Configuration: Intercepts "APPLOCK_SET_PIN" packets, validates the 6-digit constraint,
 *    and updates the stored SHA-256 hash.
 * 4. Audit Clearing: Intercepts "APPLOCK_CLEAR" to purge buffered unlock records and selfie pictures.
 * 5. Status Telemetry: Returns full operational telemetry via "APPLOCK_STATUS".
 */
class AppLockCommandHandler(
    private val context: Context? = null,
    private val controller: IAppLockController,
    private val dataSender: (String) -> Boolean
) {

    companion object {
        private const val TAG = "AppLockCommandHandler"
    }

    /**
     * Inspects an incoming DataChannel message and executes the matching App Lock operation.
     *
     * @param command Raw message string from WebRTC.
     * @return true if command was handled by this handler, false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        val trimmed = command.trim()

        // 1. Text Protocol Commands
        when (trimmed) {
            AppLockConstants.CMD_APPLOCK_ON -> {
                Log.d(TAG, "Received CMD_APPLOCK_ON")
                controller.startMonitoring()
                return true
            }

            AppLockConstants.CMD_APPLOCK_OFF -> {
                Log.d(TAG, "Received CMD_APPLOCK_OFF")
                controller.stopMonitoring()
                return true
            }

            AppLockConstants.CMD_APPLOCK_CLEAR -> {
                Log.d(TAG, "Received CMD_APPLOCK_CLEAR")
                controller.clearRecords()
                dataSender("APPLOCK_CLEARED_ACK")
                return true
            }

            AppLockConstants.CMD_APPLOCK_GET_STATUS -> {
                Log.d(TAG, "Received CMD_APPLOCK_GET_STATUS")
                dataSender(controller.getStatus().toJson().toString())
                return true
            }
        }

        // 2. JSON Protocol Commands
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val type = json.optString("type", json.optString("cmd", ""))

                when (type) {
                    AppLockConstants.CMD_APPLOCK_TOGGLE -> {
                        val enabled = json.getBoolean("enabled")
                        Log.d(TAG, "Executing remote APPLOCK_TOGGLE: enabled=$enabled")
                        AppLockManager.setAppLockEnabled(context, enabled)
                        dataSender(controller.getStatus().toJson().toString())
                        return true
                    }

                    AppLockConstants.CMD_APPLOCK_SET_PIN -> {
                        val newPin = json.getString("pin")
                        Log.d(TAG, "Executing remote APPLOCK_SET_PIN")
                        val success = AppLockManager.setPin(context, newPin)
                        if (success) {
                            dataSender("${AppLockConstants.RESP_APPLOCK_PIN_CHANGED}:SUCCESS")
                            dataSender(controller.getStatus().toJson().toString())
                        } else {
                            dataSender("${AppLockConstants.RESP_APPLOCK_PIN_CHANGED}:ERROR:PIN must be exactly 6 numeric digits")
                        }
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing AppLock JSON command: ${e.message}")
            }
        }

        return false
    }

    /**
     * Resets monitoring state when WebRTC disconnects.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected; stopping App Lock live monitoring")
        controller.stopMonitoring()
    }

    /**
     * Cleans up handler and controller resources.
     */
    fun cleanup() {
        controller.cleanup()
    }
}
