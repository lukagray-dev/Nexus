package com.nexus.parent.data.mic

import com.nexus.parent.state.ActiveCallAlert
import com.nexus.parent.state.LoudspeakerVolume
import org.json.JSONObject

data class CallStatePacket(
    val state: String,
    val direction: String,
    val number: String,
    val contactName: String,
    val timestampMs: Long,
    val isRepeat: Boolean
)

/**
 * MicProtocol.kt - Wire constants, command builders, and CALL_STATE parsers matching
 * Child `MicCommandHandler.kt` & `CallAlertStateMachine.kt` and Desktop `mic.ts`.
 */
object MicProtocol {

    const val COMMAND_CONFIRMATION_TIMEOUT_MS = 20_000L

    // Child Mic Listening Commands & Confirmations
    const val CMD_MIC_ON = "MIC_ON"
    const val CMD_MIC_OFF = "MIC_OFF"
    const val RESP_MIC_STARTED = "MIC_STARTED"
    const val RESP_MIC_STOPPED = "MIC_STOPPED"
    const val RESP_MIC_ERROR_PREFIX = "MIC_ERROR:"

    // Parent-to-Child Broadcast Intercom Commands & Confirmations
    const val CMD_PARENT_AUDIO_UNMUTE = "PARENT_AUDIO_UNMUTE"
    const val CMD_PARENT_AUDIO_MUTE = "PARENT_AUDIO_MUTE"
    const val CMD_PARENT_AUDIO_VOLUME_PREFIX = "PARENT_AUDIO_VOLUME:"
    const val RESP_PARENT_AUDIO_UNMUTED = "PARENT_AUDIO_UNMUTED"
    const val RESP_PARENT_AUDIO_MUTED = "PARENT_AUDIO_MUTED"
    const val RESP_PARENT_AUDIO_VOLUME_SET_PREFIX = "PARENT_AUDIO_VOLUME_SET:"
    const val RESP_PARENT_AUDIO_ERROR_PREFIX = "PARENT_AUDIO_ERROR:"

    // Child Call Alert JSON packet type
    const val TYPE_CALL_STATE = "CALL_STATE"
    const val CMD_CALL_ALERT_ON = "CALL_ALERT_ON"
    const val CMD_CALL_ALERT_OFF = "CALL_ALERT_OFF"

    fun volumeToWire(volume: LoudspeakerVolume): String {
        return when (volume) {
            LoudspeakerVolume.MAX -> "max"
            LoudspeakerVolume.HIGH -> "high"
            LoudspeakerVolume.NORMAL -> "normal"
        }
    }

    fun buildParentAudioUnmuteCommand(volume: LoudspeakerVolume): String {
        return "$CMD_PARENT_AUDIO_UNMUTE:${volumeToWire(volume)}"
    }

    fun buildParentAudioVolumeCommand(volume: LoudspeakerVolume): String {
        return "$CMD_PARENT_AUDIO_VOLUME_PREFIX${volumeToWire(volume)}"
    }

    fun parseCallStatePacket(rawMessage: String): CallStatePacket? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            if (json.optString("type") != TYPE_CALL_STATE) return null
            val callState = json.optString("state", "").trim().lowercase()
            if (callState.isBlank()) return null

            CallStatePacket(
                state = callState,
                direction = json.optString("direction", "unknown").trim().lowercase(),
                number = json.optString("number", "").trim(),
                contactName = json.optString("contactName", "").trim(),
                timestampMs = json.optLong("timestamp", System.currentTimeMillis()),
                isRepeat = json.optBoolean("isRepeat", false)
            )
        }.getOrNull()
    }

    fun toActiveCallAlert(packet: CallStatePacket): ActiveCallAlert? {
        if (packet.state != "ringing" && packet.state != "active") return null

        val directionLabel = when (packet.direction) {
            "incoming" -> if (packet.state == "ringing") "Incoming Call" else "Active Incoming Call"
            "outgoing" -> "Outgoing Call"
            else -> if (packet.state == "ringing") "Child Phone Ringing" else "Child In Active Call"
        }
        val title = if (packet.isRepeat) "$directionLabel (Ongoing)" else directionLabel

        val partyLabel = when {
            packet.contactName.isNotBlank() && packet.number.isNotBlank() -> "${packet.contactName} (${packet.number})"
            packet.contactName.isNotBlank() -> packet.contactName
            packet.number.isNotBlank() -> packet.number
            else -> "Cellular call detected on Child device"
        }
        return ActiveCallAlert(title = title, subtitle = partyLabel)
    }
}
