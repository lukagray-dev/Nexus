package com.nexus.child.components.wellbeing

import android.util.Log
import org.json.JSONObject

/**
 * WellbeingCommandHandler decodes and dispatches Digital Wellbeing commands received
 * over the WebRTC `nexus-control` DataChannel.
 *
 * Supported commands:
 * - `WELLBEING_ON` (or `{"type":"WELLBEING_ON","range":"today|7d"}`)
 * - `WELLBEING_OFF`
 * - `WELLBEING_REFRESH` (or `{"type":"WELLBEING_REFRESH","range":"today|7d"}`)
 * - `WELLBEING_RANGE:<today|7d>` (or `{"type":"WELLBEING_RANGE","range":"today|7d"}`)
 */
class WellbeingCommandHandler(
    private val controller: IWellbeingController,
    private val dataSender: (String) -> Boolean
) {

    companion object {
        private const val TAG = "WellbeingCmdHandler"
        private const val ERR_NO_USAGE_ACCESS =
            "Usage Access permission not granted on child device"
    }

    fun handleCommand(command: String): Boolean {
        val trimmed = command.trim()

        when {
            trimmed == WellbeingConstants.CMD_WELLBEING_ON -> {
                return handleStart(controller.getCurrentRange())
            }

            trimmed == WellbeingConstants.CMD_WELLBEING_OFF -> {
                Log.d(TAG, "Received CMD_WELLBEING_OFF")
                controller.stopMonitoring()
                return true
            }

            trimmed == WellbeingConstants.CMD_WELLBEING_REFRESH -> {
                return handleRefresh(null)
            }

            trimmed.startsWith(WellbeingConstants.CMD_WELLBEING_RANGE_PREFIX) -> {
                val rawRange = trimmed.removePrefix(WellbeingConstants.CMD_WELLBEING_RANGE_PREFIX)
                return handleRangeChange(WellbeingRange.fromWire(rawRange))
            }
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val type = json.optString("type", json.optString("cmd", ""))
                val range = if (json.has("range")) WellbeingRange.fromWire(json.optString("range")) else null

                when (type) {
                    WellbeingConstants.CMD_WELLBEING_ON -> {
                        return handleStart(range ?: controller.getCurrentRange())
                    }
                    WellbeingConstants.CMD_WELLBEING_OFF -> {
                        controller.stopMonitoring()
                        return true
                    }
                    WellbeingConstants.CMD_WELLBEING_REFRESH -> {
                        return handleRefresh(range)
                    }
                    "WELLBEING_RANGE" -> {
                        return handleRangeChange(range ?: WellbeingRange.TODAY)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Digital Wellbeing JSON command: ${e.message}")
            }
        }

        return false
    }

    private fun handleStart(range: WellbeingRange): Boolean {
        if (!controller.hasUsageAccess()) {
            Log.w(TAG, "Rejecting WELLBEING_ON: Usage Access permission missing")
            dataSender("${WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX}$ERR_NO_USAGE_ACCESS")
            return true
        }
        controller.startMonitoring(range)
        return true
    }

    private fun handleRefresh(range: WellbeingRange?): Boolean {
        if (!controller.hasUsageAccess()) {
            dataSender("${WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX}$ERR_NO_USAGE_ACCESS")
            return true
        }
        controller.refreshNow(range)
        return true
    }

    private fun handleRangeChange(range: WellbeingRange): Boolean {
        controller.setRange(range)
        if (controller.isMonitoringActive()) {
            if (!controller.hasUsageAccess()) {
                dataSender("${WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX}$ERR_NO_USAGE_ACCESS")
                return true
            }
            controller.refreshNow(range)
        }
        return true
    }

    /**
     * Stops active polling cleanly when the desktop parent disconnects.
     */
    fun onPeerDisconnected() {
        if (controller.isMonitoringActive()) {
            Log.d(TAG, "Peer disconnected; stopping Digital Wellbeing polling")
            controller.stopMonitoring()
        }
    }

    fun cleanup() {
        controller.cleanup()
    }
}
