package com.nexus.child.components.calllog

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * CallLogCommandHandler coordinates DataChannel commands and telemetry payloads
 * for telephony call monitoring between Child and Parent.
 *
 * Hey friend! Here is the command handshake protocol:
 * 1. Parent clicks Play -> sends "CALLLOG_ON" over the DataChannel.
 * 2. We check runtime permission READ_CALL_LOG.
 *    - If denied: we send "CALLLOG_ERROR: Call Log permission not granted on child device".
 * 3. We register the ContentObserver and send "CALLLOG_STARTED" confirmation.
 * 4. We immediately fetch the latest 100 call history records from Android and send them
 *    as a single "CALLLOG_HISTORY" JSON payload.
 * 5. As the phone receives, dials, or misses new calls, our observer fires and transmits
 *    individual "CALLLOG_NEW" JSON packets in real time.
 * 6. Parent clicks Pause -> sends "CALLLOG_OFF". We unregister the observer and send "CALLLOG_STOPPED".
 * 7. If peer disconnects -> onPeerDisconnected() guarantees observers are shut down.
 */
class CallLogCommandHandler(
    private val callLogController: ICallLogController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "CallLogCommandHandler"

        // Commands
        const val CMD_CALLLOG_ON = "CALLLOG_ON"
        const val CMD_CALLLOG_OFF = "CALLLOG_OFF"
        const val ALIAS_CALLLOG_START = "CALLLOG_START"
        const val ALIAS_START_CALLLOG = "START_CALLLOG"
        const val ALIAS_CALLLOG_STOP = "CALLLOG_STOP"
        const val ALIAS_STOP_CALLLOG = "STOP_CALLLOG"

        // Responses
        const val RESP_CALLLOG_STARTED = "CALLLOG_STARTED"
        const val RESP_CALLLOG_STOPPED = "CALLLOG_STOPPED"
        const val RESP_CALLLOG_ERROR_PREFIX = "CALLLOG_ERROR: "

        // Telemetry Types
        const val TYPE_CALLLOG_HISTORY = "CALLLOG_HISTORY"
        const val TYPE_CALLLOG_NEW = "CALLLOG_NEW"
    }

    /**
     * Inspects incoming DataChannel strings and executes the matching call log operation.
     *
     * @param command Raw message received over DataChannel
     * @return true if command was handled by CallLog; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_CALLLOG_ON, ALIAS_CALLLOG_START, ALIAS_START_CALLLOG -> {
                handleCallLogOn()
                true
            }
            CMD_CALLLOG_OFF, ALIAS_CALLLOG_STOP, ALIAS_STOP_CALLLOG -> {
                handleCallLogOff()
                true
            }
            else -> false
        }
    }

    /**
     * Activates CallLog monitoring, confirms to parent, and dispatches the initial 100 records.
     */
    private fun handleCallLogOn() {
        Log.d(TAG, "Processing CALLLOG_ON command...")

        // Step 1: Permission check
        if (!callLogController.hasCallLogPermission()) {
            val errorMsg = "${RESP_CALLLOG_ERROR_PREFIX}Call Log permission not granted on child device"
            Log.w(TAG, errorMsg)
            dataSender(errorMsg)
            return
        }

        // Step 2: Register real-time observer
        val started = callLogController.startCallLogMonitoring { newCall ->
            sendNewCallRecord(newCall)
        }

        if (started) {
            Log.d(TAG, "CallLog monitoring started. Sending confirmation: $RESP_CALLLOG_STARTED")
            dataSender(RESP_CALLLOG_STARTED)

            // Step 3: Fetch latest 100 historical calls and dispatch
            try {
                val records = callLogController.fetchLatestCallLogs(limit = 100)
                val historyJson = buildCallHistoryJson(records)
                dataSender(historyJson)
                Log.d(TAG, "Dispatched ${records.size} historical call records to parent.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch and transmit initial call history: ${e.message}", e)
            }
        } else {
            val errorMsg = "${RESP_CALLLOG_ERROR_PREFIX}Failed to register CallLog observer"
            Log.e(TAG, errorMsg)
            dataSender(errorMsg)
        }
    }

    /**
     * Halts CallLog monitoring and sends confirmation.
     */
    private fun handleCallLogOff() {
        Log.d(TAG, "Processing CALLLOG_OFF command...")
        callLogController.stopCallLogMonitoring()
        Log.d(TAG, "CallLog monitoring stopped. Sending confirmation: $RESP_CALLLOG_STOPPED")
        dataSender(RESP_CALLLOG_STOPPED)
    }

    /**
     * Transmits a single newly recorded call over the DataChannel.
     */
    private fun sendNewCallRecord(record: CallRecord) {
        try {
            val json = buildNewCallJson(record)
            val dispatched = dataSender(json)
            if (dispatched) {
                Log.d(TAG, "Live call record dispatched: ${record.name} (${record.number})")
            } else {
                Log.w(TAG, "Failed to send live call update: DataChannel send returned false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize live call record: ${e.message}", e)
        }
    }

    /**
     * Fail-safe invoked when the WebRTC peer disconnects.
     * Prevents lingering background queries and battery usage.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Shutting down CallLog monitoring to preserve battery...")
        callLogController.stopCallLogMonitoring()
    }

    /**
     * Shuts down all underlying thread and observer resources.
     */
    fun cleanup() {
        callLogController.cleanup()
    }

    /**
     * Builds the JSON envelope containing the batch of historical call records.
     */
    fun buildCallHistoryJson(records: List<CallRecord>): String {
        val recordsJson = records.joinToString(prefix = "[", postfix = "]") { serializeCallRecord(it) }
        return "{\"type\":\"$TYPE_CALLLOG_HISTORY\",\"count\":${records.size},\"records\":$recordsJson}"
    }

    /**
     * Builds the JSON envelope for a single real-time call event.
     */
    fun buildNewCallJson(record: CallRecord): String {
        return "{\"type\":\"$TYPE_CALLLOG_NEW\",\"record\":${serializeCallRecord(record)}}"
    }

    /**
     * Serializes an individual CallRecord into a clean JSON string.
     * Uses direct string formatting to avoid stubbed JSONObject issues in JVM unit tests
     * and reduce runtime heap allocations.
     */
    fun serializeCallRecord(record: CallRecord): String {
        val featsJson = record.features.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
        return "{\"id\":\"${escapeJson(record.id)}\"," +
                "\"number\":\"${escapeJson(record.number)}\"," +
                "\"name\":\"${escapeJson(record.name)}\"," +
                "\"numberType\":\"${escapeJson(record.numberType)}\"," +
                "\"callType\":\"${escapeJson(record.callType)}\"," +
                "\"duration\":${record.duration}," +
                "\"timestamp\":${record.timestamp}," +
                "\"location\":\"${escapeJson(record.location)}\"," +
                "\"simId\":\"${escapeJson(record.simId)}\"," +
                "\"features\":$featsJson," +
                "\"isRead\":${record.isRead}}"
    }

    /**
     * Escapes control characters and quotes for compliant JSON string literals.
     */
    fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}
