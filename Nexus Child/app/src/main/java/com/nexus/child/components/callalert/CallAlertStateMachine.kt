package com.nexus.child.components.callalert

/**
 * Pure JVM-testable state machine and JSON serializer for live cellular call alerts.
 *
 * Hey friend! Android's TelephonyManager exposes 3 call states:
 * - 0 (IDLE): No call is active.
 * - 1 (RINGING): An incoming call is ringing.
 * - 2 (OFFHOOK): A call is dialing, active, or on hold.
 *
 * By tracking transitions across those states:
 * - IDLE -> RINGING          => incoming ringing call
 * - RINGING -> OFFHOOK       => incoming call answered (active)
 * - IDLE -> OFFHOOK          => outgoing call placed/active
 * - RINGING|OFFHOOK -> IDLE  => call ended (idle)
 *
 * Because some Android builds fire ACTION_PHONE_STATE_CHANGED twice (once without the
 * phone number and once with EXTRA_INCOMING_NUMBER), this state machine deduplicates
 * repeat transitions while allowing number/contact enrichment to pass through cleanly.
 */
class CallAlertStateMachine {

    companion object {
        const val CALL_STATE_IDLE = 0
        const val CALL_STATE_RINGING = 1
        const val CALL_STATE_OFFHOOK = 2

        const val TYPE_CALL_STATE = "CALL_STATE"
        const val STATE_RINGING = "ringing"
        const val STATE_ACTIVE = "active"
        const val STATE_IDLE = "idle"

        const val DIRECTION_INCOMING = "incoming"
        const val DIRECTION_OUTGOING = "outgoing"
        const val DIRECTION_UNKNOWN = "unknown"
    }

    private var lastOsState: Int = CALL_STATE_IDLE
    private var hasActiveAlert: Boolean = false
    private var currentDirection: String = DIRECTION_UNKNOWN
    private var currentNumber: String = ""
    private var currentContactName: String = ""

    /**
     * Evaluates an OS call-state change and returns a [CallAlertEvent] if the parent
     * should be notified, or null if the update is a redundant duplicate.
     */
    @Synchronized
    fun onOsCallStateChanged(
        osState: Int,
        rawNumber: String? = null,
        resolvedContactName: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): CallAlertEvent? {
        val cleanNumber = rawNumber?.trim().orEmpty()
        val cleanName = resolvedContactName?.trim().orEmpty()

        return when (osState) {
            CALL_STATE_RINGING -> {
                if (lastOsState == CALL_STATE_RINGING && hasActiveAlert) {
                    val numberUpdated = cleanNumber.isNotEmpty() && currentNumber != cleanNumber
                    val nameUpdated = cleanName.isNotEmpty() && currentContactName != cleanName
                    if (!numberUpdated && !nameUpdated) {
                        return null
                    }
                    if (numberUpdated) currentNumber = cleanNumber
                    if (nameUpdated) currentContactName = cleanName
                    CallAlertEvent(
                        state = STATE_RINGING,
                        direction = currentDirection,
                        number = currentNumber,
                        contactName = currentContactName,
                        timestamp = timestampMs,
                        isRepeat = false
                    )
                } else {
                    lastOsState = CALL_STATE_RINGING
                    hasActiveAlert = true
                    currentDirection = DIRECTION_INCOMING
                    if (cleanNumber.isNotEmpty()) currentNumber = cleanNumber
                    if (cleanName.isNotEmpty()) currentContactName = cleanName
                    CallAlertEvent(
                        state = STATE_RINGING,
                        direction = currentDirection,
                        number = currentNumber,
                        contactName = currentContactName,
                        timestamp = timestampMs,
                        isRepeat = false
                    )
                }
            }

            CALL_STATE_OFFHOOK -> {
                if (lastOsState == CALL_STATE_OFFHOOK && hasActiveAlert) {
                    val numberUpdated = cleanNumber.isNotEmpty() && currentNumber != cleanNumber
                    val nameUpdated = cleanName.isNotEmpty() && currentContactName != cleanName
                    if (!numberUpdated && !nameUpdated) {
                        return null
                    }
                    if (numberUpdated) currentNumber = cleanNumber
                    if (nameUpdated) currentContactName = cleanName
                    CallAlertEvent(
                        state = STATE_ACTIVE,
                        direction = currentDirection,
                        number = currentNumber,
                        contactName = currentContactName,
                        timestamp = timestampMs,
                        isRepeat = false
                    )
                } else {
                    // Coming from RINGING means the incoming call was answered; from IDLE means outgoing.
                    currentDirection = if (lastOsState == CALL_STATE_RINGING) {
                        DIRECTION_INCOMING
                    } else {
                        DIRECTION_OUTGOING
                    }
                    lastOsState = CALL_STATE_OFFHOOK
                    hasActiveAlert = true
                    if (cleanNumber.isNotEmpty()) currentNumber = cleanNumber
                    if (cleanName.isNotEmpty()) currentContactName = cleanName
                    CallAlertEvent(
                        state = STATE_ACTIVE,
                        direction = currentDirection,
                        number = currentNumber,
                        contactName = currentContactName,
                        timestamp = timestampMs,
                        isRepeat = false
                    )
                }
            }

            else -> {
                if (!hasActiveAlert && lastOsState == CALL_STATE_IDLE) {
                    return null
                }
                val endedDirection = currentDirection
                val endedNumber = currentNumber
                val endedName = currentContactName
                reset()
                CallAlertEvent(
                    state = STATE_IDLE,
                    direction = endedDirection,
                    number = endedNumber,
                    contactName = endedName,
                    timestamp = timestampMs,
                    isRepeat = false
                )
            }
        }
    }

    /**
     * Generates a repeating reminder event for an ongoing call, or null if idle.
     */
    @Synchronized
    fun buildRepeatEvent(timestampMs: Long = System.currentTimeMillis()): CallAlertEvent? {
        if (!hasActiveAlert || lastOsState == CALL_STATE_IDLE) {
            return null
        }
        val stateStr = if (lastOsState == CALL_STATE_RINGING) STATE_RINGING else STATE_ACTIVE
        return CallAlertEvent(
            state = stateStr,
            direction = currentDirection,
            number = currentNumber,
            contactName = currentContactName,
            timestamp = timestampMs,
            isRepeat = true
        )
    }

    /**
     * Returns whether a call is currently ringing or active.
     */
    @Synchronized
    fun isCallActive(): Boolean = hasActiveAlert && lastOsState != CALL_STATE_IDLE

    /**
     * Returns the current known phone number for the active call (used to avoid redundant lookups).
     */
    @Synchronized
    fun getCurrentNumber(): String = currentNumber

    /**
     * Resets all tracked call state back to idle.
     */
    @Synchronized
    fun reset() {
        lastOsState = CALL_STATE_IDLE
        hasActiveAlert = false
        currentDirection = DIRECTION_UNKNOWN
        currentNumber = ""
        currentContactName = ""
    }

    /**
     * Serializes a [CallAlertEvent] into the WebRTC DataChannel JSON envelope without
     * relying on Android's org.json runtime so JVM unit tests execute deterministically.
     */
    fun serializeEventJson(event: CallAlertEvent): String {
        return "{\"type\":\"$TYPE_CALL_STATE\"," +
            "\"state\":\"${escapeJson(event.state)}\"," +
            "\"direction\":\"${escapeJson(event.direction)}\"," +
            "\"number\":\"${escapeJson(event.number)}\"," +
            "\"contactName\":\"${escapeJson(event.contactName)}\"," +
            "\"timestamp\":${event.timestamp}," +
            "\"isRepeat\":${event.isRepeat}}"
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
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
