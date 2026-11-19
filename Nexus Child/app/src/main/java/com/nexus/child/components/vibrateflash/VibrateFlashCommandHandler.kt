package com.nexus.child.components.vibrateflash

import android.util.Log

/**
 * VibrateFlashCommandHandler coordinates DataChannel actuation commands and confirmations
 * between the Desktop Parent and the Android Child hardware.
 *
 * Hey friend! Here is how the handshake works:
 * 1. Parent clicks a vibration pattern -> dispatches "VIBRATE:short" over DataChannel.
 * 2. We parse the pattern and call vibrateController.vibrate("short").
 * 3. If the device vibrates successfully, we return "VIBRATE_SUCCESS: short".
 *    If the motor is missing or threw an error, we return "VIBRATE_ERROR: <reason>".
 * 4. For flashlight, the parent dispatches "FLASH:strobe".
 *    We set torch mode, run the strobe coroutine, and respond with "FLASH_SUCCESS: strobe".
 * 5. For combined, parent dispatches "VIBRATE_FLASH:alert".
 *    We execute both, returning "VIBRATE_FLASH_SUCCESS: alert".
 * 6. If the peer disconnects while a strobe or pulse is running, onPeerDisconnected()
 *    is immediately triggered to shut off the torch and vibrator, preventing battery drain!
 */
class VibrateFlashCommandHandler(
    private val controller: IVibrateFlashController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "VibrateFlashCmdHandler"

        // Command Prefixes
        const val CMD_VIBRATE_PREFIX = "VIBRATE:"
        const val CMD_FLASH_PREFIX = "FLASH:"
        const val CMD_VIBRATE_FLASH_PREFIX = "VIBRATE_FLASH:"
        const val CMD_STOP_ALL = "STOP_VIBRATE_FLASH"

        // Responses
        const val RESP_VIBRATE_SUCCESS_PREFIX = "VIBRATE_SUCCESS: "
        const val RESP_VIBRATE_ERROR_PREFIX = "VIBRATE_ERROR: "
        const val RESP_FLASH_SUCCESS_PREFIX = "FLASH_SUCCESS: "
        const val RESP_FLASH_ERROR_PREFIX = "FLASH_ERROR: "
        const val RESP_VIBRATE_FLASH_SUCCESS_PREFIX = "VIBRATE_FLASH_SUCCESS: "
        const val RESP_VIBRATE_FLASH_ERROR_PREFIX = "VIBRATE_FLASH_ERROR: "
    }

    /**
     * Inspects incoming DataChannel strings and executes matching vibration or flash actions.
     *
     * @param command Raw message received over DataChannel
     * @return true if handled by this component; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        val trimmed = command.trim()

        // Handle string command protocol: "VIBRATE:<pattern>"
        if (trimmed.startsWith(CMD_VIBRATE_PREFIX)) {
            val pattern = trimmed.substring(CMD_VIBRATE_PREFIX.length).trim()
            handleVibrate(pattern)
            return true
        }

        // Handle string command protocol: "FLASH:<action>"
        if (trimmed.startsWith(CMD_FLASH_PREFIX)) {
            val action = trimmed.substring(CMD_FLASH_PREFIX.length).trim()
            handleFlash(action)
            return true
        }

        // Handle string command protocol: "VIBRATE_FLASH:<pattern>"
        if (trimmed.startsWith(CMD_VIBRATE_FLASH_PREFIX)) {
            val pattern = trimmed.substring(CMD_VIBRATE_FLASH_PREFIX.length).trim()
            handleVibrateAndFlash(pattern)
            return true
        }

        // Stop all ongoing hardware actions
        if (trimmed == CMD_STOP_ALL || trimmed == "VIBRATE_STOP" || trimmed == "FLASH_STOP") {
            controller.stopAll()
            return true
        }

        // Handle JSON fallback: {"type":"VIBRATE", "pattern":"short"}
        if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) {
            return tryParseJsonCommand(trimmed)
        }

        return false
    }

    private fun handleVibrate(pattern: String) {
        Log.d(TAG, "Executing vibration command for pattern: $pattern")
        if (!controller.hasVibrator()) {
            val err = "${RESP_VIBRATE_ERROR_PREFIX}Vibration motor not available on child device"
            Log.w(TAG, err)
            dataSender(err)
            return
        }

        val success = controller.vibrate(pattern)
        if (success) {
            val resp = "${RESP_VIBRATE_SUCCESS_PREFIX}$pattern"
            Log.d(TAG, "Vibration succeeded. Dispatched: $resp")
            dataSender(resp)
        } else {
            val err = "${RESP_VIBRATE_ERROR_PREFIX}Failed to execute vibration pattern '$pattern'"
            Log.e(TAG, err)
            dataSender(err)
        }
    }

    private fun handleFlash(action: String) {
        Log.d(TAG, "Executing flashlight command for action: $action")
        if (!controller.hasFlash()) {
            val err = "${RESP_FLASH_ERROR_PREFIX}Camera flashlight not available on child device"
            Log.w(TAG, err)
            dataSender(err)
            return
        }

        val success = controller.flash(action)
        if (success) {
            val resp = "${RESP_FLASH_SUCCESS_PREFIX}$action"
            Log.d(TAG, "Flashlight action succeeded. Dispatched: $resp")
            dataSender(resp)
        } else {
            val err = "${RESP_FLASH_ERROR_PREFIX}Failed to execute flashlight action '$action'"
            Log.e(TAG, err)
            dataSender(err)
        }
    }

    private fun handleVibrateAndFlash(pattern: String) {
        Log.d(TAG, "Executing combined vibrate & flash for pattern: $pattern")
        val success = controller.vibrateAndFlash(pattern)
        if (success) {
            val resp = "${RESP_VIBRATE_FLASH_SUCCESS_PREFIX}$pattern"
            Log.d(TAG, "Combined pattern succeeded. Dispatched: $resp")
            dataSender(resp)
        } else {
            val err = "${RESP_VIBRATE_FLASH_ERROR_PREFIX}Failed executing combined pattern '$pattern'"
            Log.e(TAG, err)
            dataSender(err)
        }
    }

    private fun tryParseJsonCommand(jsonText: String): Boolean {
        val typeMatch = "\"type\"\\s*:\\s*\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(jsonText)
        val type = typeMatch?.groupValues?.get(1) ?: return false

        val patternMatch = "\"pattern\"\\s*:\\s*\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(jsonText)
        val pattern = patternMatch?.groupValues?.get(1) ?: "short"

        val actionMatch = "\"action\"\\s*:\\s*\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(jsonText)
        val action = actionMatch?.groupValues?.get(1) ?: pattern

        return when (type.uppercase()) {
            "VIBRATE" -> {
                handleVibrate(pattern)
                true
            }
            "FLASH" -> {
                handleFlash(action)
                true
            }
            "VIBRATE_FLASH" -> {
                handleVibrateAndFlash(pattern)
                true
            }
            else -> false
        }
    }

    /**
     * Fail-safe invoked when the WebRTC peer disconnects.
     * Guarantees torch and vibration loops cease immediately.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Stopping all active vibration and flashlight hardware...")
        controller.stopAll()
    }

    /**
     * Cleans up all controller resources.
     */
    fun cleanup() {
        controller.cleanup()
    }
}
