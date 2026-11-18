package com.nexus.child.components.sms

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * SmsCommandHandler coordinates DataChannel commands and telemetry payloads
 * for SMS text message monitoring between Child and Parent.
 *
 * Hey friend! Here is the command handshake protocol:
 * 1. Parent clicks Play in SMS panel -> sends "SMS_ON" over the DataChannel.
 * 2. We verify runtime permission READ_SMS.
 *    - If denied: we send "SMS_ERROR: SMS permission not granted on child device".
 * 3. We register the ContentObserver and send "SMS_STARTED" confirmation.
 * 4. We fetch the latest 100 SMS messages and dispatch them in safe chunks of 50
 *    to prevent DataChannel buffer overflows (`bufferedAmountLow`).
 * 5. When a new SMS is received or sent, our ContentObserver fires and dispatches
 *    a real-time "SMS_NEW" JSON packet to the parent console.
 * 6. Parent clicks Pause -> sends "SMS_OFF". We unregister the observer and send "SMS_STOPPED".
 * 7. If peer disconnects -> onPeerDisconnected() shuts down listeners immediately.
 */
class SmsCommandHandler(
    private val smsController: ISmsController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "SmsCommandHandler"

        // Commands
        const val CMD_SMS_ON = "SMS_ON"
        const val CMD_SMS_OFF = "SMS_OFF"
        const val ALIAS_SMS_START = "SMS_START"
        const val ALIAS_START_SMS = "START_SMS"
        const val ALIAS_SMS_STOP = "SMS_STOP"
        const val ALIAS_STOP_SMS = "STOP_SMS"

        // Responses
        const val RESP_SMS_STARTED = "SMS_STARTED"
        const val RESP_SMS_STOPPED = "SMS_STOPPED"
        const val RESP_SMS_ERROR_PREFIX = "SMS_ERROR: "

        // Telemetry Types
        const val TYPE_SMS_HISTORY = "SMS_HISTORY"
        const val TYPE_SMS_NEW = "SMS_NEW"

        private const val CHUNK_SIZE = 50
    }

    /**
     * Inspects incoming DataChannel strings and executes the matching SMS operation.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_SMS_ON, ALIAS_SMS_START, ALIAS_START_SMS -> {
                handleSmsOn()
                true
            }
            CMD_SMS_OFF, ALIAS_SMS_STOP, ALIAS_STOP_SMS -> {
                handleSmsOff()
                true
            }
            else -> false
        }
    }

    /**
     * Activates SMS monitoring, sends confirmation, and dispatches the latest 100 messages.
     */
    private fun handleSmsOn() {
        Log.d(TAG, "Processing SMS_ON command...")

        // Step 1: Permission check
        if (!smsController.hasSmsPermission()) {
            val errorMsg = "${RESP_SMS_ERROR_PREFIX}SMS permission not granted on child device"
            Log.w(TAG, errorMsg)
            dataSender(errorMsg)
            return
        }

        // Step 2: Register real-time observer
        val started = smsController.startSmsMonitoring { newSms ->
            sendNewSmsRecord(newSms)
        }

        if (started) {
            Log.d(TAG, "SMS monitoring started. Sending confirmation: $RESP_SMS_STARTED")
            dataSender(RESP_SMS_STARTED)

            // Step 3: Fetch latest 100 messages and dispatch in chunked packets
            try {
                val messages = smsController.fetchLatestSms(limit = 100)
                if (messages.isEmpty()) {
                    dataSender(buildSmsHistoryChunkJson(emptyList(), 0, 1, 0))
                } else {
                    val chunks = messages.chunked(CHUNK_SIZE)
                    chunks.forEachIndexed { index, chunk ->
                        val chunkJson = buildSmsHistoryChunkJson(chunk, index, chunks.size, messages.size)
                        dataSender(chunkJson)
                    }
                }
                Log.d(TAG, "Dispatched ${messages.size} historical SMS messages to parent.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch and transmit SMS history: ${e.message}", e)
            }
        } else {
            val errorMsg = "${RESP_SMS_ERROR_PREFIX}Failed to register SMS observer"
            Log.e(TAG, errorMsg)
            dataSender(errorMsg)
        }
    }

    /**
     * Halts SMS monitoring and sends confirmation.
     */
    private fun handleSmsOff() {
        Log.d(TAG, "Processing SMS_OFF command...")
        smsController.stopSmsMonitoring()
        Log.d(TAG, "SMS monitoring stopped. Sending confirmation: $RESP_SMS_STOPPED")
        dataSender(RESP_SMS_STOPPED)
    }

    /**
     * Transmits a single newly recorded SMS over the DataChannel.
     */
    private fun sendNewSmsRecord(record: SmsRecord) {
        try {
            val json = buildNewSmsJson(record)
            val dispatched = dataSender(json)
            if (dispatched) {
                Log.d(TAG, "Live SMS record dispatched: from=${record.senderName} (${record.address})")
            } else {
                Log.w(TAG, "Failed to send live SMS update: DataChannel send returned false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize live SMS record: ${e.message}", e)
        }
    }

    /**
     * Fail-safe invoked when the WebRTC peer disconnects.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Shutting down SMS monitoring...")
        smsController.stopSmsMonitoring()
    }

    fun cleanup() {
        smsController.cleanup()
    }

    /**
     * Builds the JSON envelope containing a chunk of historical SMS messages.
     */
    fun buildSmsHistoryChunkJson(
        messages: List<SmsRecord>,
        chunkIndex: Int,
        totalChunks: Int,
        totalCount: Int
    ): String {
        val messagesJson = messages.joinToString(prefix = "[", postfix = "]") { serializeSmsRecord(it) }
        return "{\"type\":\"$TYPE_SMS_HISTORY\"," +
                "\"chunkIndex\":$chunkIndex," +
                "\"totalChunks\":$totalChunks," +
                "\"totalCount\":$totalCount," +
                "\"count\":${messages.size}," +
                "\"messages\":$messagesJson}"
    }

    /**
     * Builds the JSON envelope for a single real-time SMS event.
     */
    fun buildNewSmsJson(record: SmsRecord): String {
        return "{\"type\":\"$TYPE_SMS_NEW\",\"message\":${serializeSmsRecord(record)}}"
    }

    /**
     * Serializes an individual SmsRecord into a clean JSON string.
     * Uses direct string formatting to avoid stubbed JSONObject issues in JVM unit tests
     * and reduce runtime allocations.
     */
    fun serializeSmsRecord(record: SmsRecord): String {
        return "{\"id\":\"${escapeJson(record.id)}\"," +
                "\"address\":\"${escapeJson(record.address)}\"," +
                "\"senderName\":\"${escapeJson(record.senderName)}\"," +
                "\"body\":\"${escapeJson(record.body)}\"," +
                "\"smsType\":\"${escapeJson(record.smsType)}\"," +
                "\"timestamp\":${record.timestamp}," +
                "\"dateSent\":${record.dateSent}," +
                "\"read\":${record.read}," +
                "\"simId\":\"${escapeJson(record.simId)}\"}"
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
