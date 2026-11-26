package com.nexus.parent.data.monitor

import com.nexus.parent.state.NotificationFilter
import com.nexus.parent.state.WellbeingCategoryFilter
import com.nexus.parent.state.WellbeingRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VibratePattern(val wireValue: String, val displayLabel: String) {
    SHORT("short", "Short Buzz"),
    DOUBLE("double", "Double Tap"),
    PULSE("pulse", "Pulse Wave"),
    SOS("sos", "SOS Pattern")
}

enum class FlashAction(val wireValue: String, val displayLabel: String) {
    ON("on", "Flashlight On"),
    OFF("off", "Flashlight Off"),
    STROBE("strobe", "Strobe (4 Hz)"),
    BEACON("beacon", "Emergency Beacon")
}

enum class CombinedPattern(val wireValue: String, val displayLabel: String) {
    ALERT("alert", "Alert Alarm"),
    SOS("sos", "Panic SOS"),
    PULSE("pulse", "Pulse Strobe")
}

/**
 * MonitorProtocol.kt - Wire constants and deterministic formatters matching Desktop `right-sidebar/types.ts`.
 *
 * Hey friend! Putting every DataChannel command string, response prefix, timeout constant, and
 * human-readable formatter in one module guarantees Nexus Parent Android and Nexus Child speak
 * the exact same protocol as the Desktop app.
 */
object MonitorProtocol {

    // Timeouts (matching Desktop right-sidebar.ts & wallpaper.ts)
    const val MONITOR_CONFIRM_TIMEOUT_MS = 20_000L
    const val APPLOCK_STOP_TIMEOUT_MS = 10_000L
    const val WALLPAPER_FETCH_TIMEOUT_MS = 15_000L
    const val WALLPAPER_PUSH_TIMEOUT_MS = 30_000L

    // Limits & Chunking
    const val KEYBOARD_MAX_RECORDS = 500
    const val WALLPAPER_PUSH_CHUNK_SIZE = 16_384 // Exact multiple of 4 for Child per-chunk Base64 decoding
    const val WALLPAPER_MAX_FILE_BYTES = 10L * 1024L * 1024L

    // SMS
    const val CMD_SMS_ON = "SMS_ON"
    const val CMD_SMS_OFF = "SMS_OFF"
    const val RESP_SMS_STARTED = "SMS_STARTED"
    const val RESP_SMS_STOPPED = "SMS_STOPPED"
    const val RESP_SMS_ERROR_PREFIX = "SMS_ERROR: "
    const val TYPE_SMS_HISTORY = "SMS_HISTORY"
    const val TYPE_SMS_NEW = "SMS_NEW"

    // Call Log
    const val CMD_CALLLOG_ON = "CALLLOG_ON"
    const val CMD_CALLLOG_OFF = "CALLLOG_OFF"
    const val RESP_CALLLOG_STARTED = "CALLLOG_STARTED"
    const val RESP_CALLLOG_STOPPED = "CALLLOG_STOPPED"
    const val RESP_CALLLOG_ERROR_PREFIX = "CALLLOG_ERROR: "
    const val TYPE_CALLLOG_HISTORY = "CALLLOG_HISTORY"
    const val TYPE_CALLLOG_NEW = "CALLLOG_NEW"

    // Notifications
    const val CMD_NOTIFICATION_ON = "NOTIFICATION_ON"
    const val CMD_NOTIFICATION_OFF = "NOTIFICATION_OFF"
    const val RESP_NOTIFICATION_STARTED = "NOTIFICATION_STARTED"
    const val RESP_NOTIFICATION_STOPPED = "NOTIFICATION_STOPPED"
    const val RESP_NOTIFICATION_ERROR_PREFIX = "NOTIFICATION_ERROR: "
    const val TYPE_NOTIFICATION_SNAPSHOT = "NOTIFICATION_SNAPSHOT"
    const val TYPE_NOTIFICATION_NEW = "NOTIFICATION_NEW"
    const val TYPE_NOTIFICATION_REMOVED = "NOTIFICATION_REMOVED"

    // Keyboard
    const val CMD_KEYBOARD_ON = "KEYBOARD_ON"
    const val CMD_KEYBOARD_OFF = "KEYBOARD_OFF"
    const val RESP_KEYBOARD_STARTED = "KEYBOARD_STARTED"
    const val RESP_KEYBOARD_STOPPED = "KEYBOARD_STOPPED"
    const val RESP_KEYBOARD_ERROR_PREFIX = "KEYBOARD_ERROR: "
    const val TYPE_KEYBOARD_SNAPSHOT = "KEYBOARD_SNAPSHOT"
    const val TYPE_KEYBOARD_NEW = "KEYBOARD_NEW"

    // Digital Wellbeing
    const val CMD_WELLBEING_ON = "WELLBEING_ON"
    const val CMD_WELLBEING_OFF = "WELLBEING_OFF"
    const val CMD_WELLBEING_REFRESH = "WELLBEING_REFRESH"
    const val CMD_WELLBEING_RANGE_PREFIX = "WELLBEING_RANGE:"
    const val RESP_WELLBEING_STARTED = "WELLBEING_STARTED"
    const val RESP_WELLBEING_STOPPED = "WELLBEING_STOPPED"
    const val RESP_WELLBEING_ERROR_PREFIX = "WELLBEING_ERROR: "
    const val TYPE_WELLBEING_SNAPSHOT = "WELLBEING_SNAPSHOT"

    // App Lock
    const val CMD_APPLOCK_ON = "APPLOCK_ON"
    const val CMD_APPLOCK_OFF = "APPLOCK_OFF"
    const val CMD_APPLOCK_TOGGLE = "APPLOCK_TOGGLE"
    const val CMD_APPLOCK_SET_PIN = "APPLOCK_SET_PIN"
    const val CMD_APPLOCK_CLEAR = "APPLOCK_CLEAR"
    const val CMD_APPLOCK_GET_STATUS = "APPLOCK_GET_STATUS"
    const val RESP_APPLOCK_STARTED = "APPLOCK_STARTED"
    const val RESP_APPLOCK_STOPPED = "APPLOCK_STOPPED"
    const val RESP_APPLOCK_ERROR_PREFIX = "APPLOCK_ERROR: "
    const val RESP_APPLOCK_STATUS = "APPLOCK_STATUS"
    const val RESP_APPLOCK_PIN_CHANGED_PREFIX = "APPLOCK_PIN_CHANGED:"
    const val RESP_APPLOCK_CLEARED_ACK = "APPLOCK_CLEARED_ACK"
    const val TYPE_APPLOCK_SNAPSHOT = "APPLOCK_SNAPSHOT"
    const val TYPE_APPLOCK_NEW = "APPLOCK_NEW"

    // Vibrate & Flash
    const val CMD_VIBRATE_PREFIX = "VIBRATE:"
    const val CMD_FLASH_PREFIX = "FLASH:"
    const val CMD_VIBRATE_FLASH_PREFIX = "VIBRATE_FLASH:"
    const val RESP_VIBRATE_SUCCESS_PREFIX = "VIBRATE_SUCCESS: "
    const val RESP_VIBRATE_ERROR_PREFIX = "VIBRATE_ERROR: "
    const val RESP_FLASH_SUCCESS_PREFIX = "FLASH_SUCCESS: "
    const val RESP_FLASH_ERROR_PREFIX = "FLASH_ERROR: "
    const val RESP_VIBRATE_FLASH_SUCCESS_PREFIX = "VIBRATE_FLASH_SUCCESS: "
    const val RESP_VIBRATE_FLASH_ERROR_PREFIX = "VIBRATE_FLASH_ERROR: "

    // Wallpaper
    const val CMD_WALLPAPER_ON = "WALLPAPER_ON"
    const val CMD_WALLPAPER_OFF = "WALLPAPER_OFF"
    const val CMD_WALLPAPER_GET = "WALLPAPER_GET"
    const val RESP_WALLPAPER_STARTED = "WALLPAPER_STARTED"
    const val RESP_WALLPAPER_STOPPED = "WALLPAPER_STOPPED"
    const val RESP_WALLPAPER_ERROR_PREFIX = "WALLPAPER_ERROR: "
    const val TYPE_WALLPAPER_CHUNK = "WALLPAPER_CHUNK"
    const val CMD_PUSH_WALLPAPER_START = "PUSH_WALLPAPER_START"
    const val CMD_PUSH_WALLPAPER_CHUNK = "PUSH_WALLPAPER_CHUNK"
    const val RESP_WALLPAPER_SET_SUCCESS = "WALLPAPER_SET_SUCCESS"
    const val RESP_WALLPAPER_SET_ERROR_PREFIX = "WALLPAPER_SET_ERROR: "

    fun formatSimSlot(rawSim: String?): String {
        val trimmed = rawSim?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (Regex("^SIM\\s+\\d+\\s*•", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return trimmed
        }
        if (Regex("^SIM\\s+[12]$", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return trimmed.uppercase(Locale.US)
        }
        val simMatch = Regex("^SIM\\s+(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (simMatch != null) {
            val num = simMatch.groupValues[1].toIntOrNull() ?: 1
            return when {
                num == 0 || num == 1 -> "SIM 1"
                num == 2 -> "SIM 2"
                num % 2 == 0 -> "SIM 2"
                else -> "SIM 1"
            }
        }
        val intVal = trimmed.toIntOrNull()
        if (intVal != null) {
            return when {
                intVal == 0 || intVal == 1 -> "SIM 1"
                intVal == 2 -> "SIM 2"
                intVal % 2 == 0 -> "SIM 2"
                else -> "SIM 1"
            }
        }
        return trimmed
    }

    fun formatCallDuration(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatScreenTimeDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0m"
        val totalSeconds = durationMs / 1000L
        if (totalSeconds < 60L) return "${totalSeconds}s"
        val totalMinutes = totalSeconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "--"
        val diffSec = (nowMs - timestampMs) / 1000L
        if (diffSec < 10L) return "Just now"
        if (diffSec < 60L) return "${diffSec}s ago"
        val diffMin = diffSec / 60L
        if (diffMin < 60L) return "${diffMin}m ago"
        val diffHours = diffMin / 60L
        if (diffHours < 24L) {
            return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestampMs))
        }
        val diffDays = diffHours / 24L
        if (diffDays == 1L) return "Yesterday"
        if (diffDays < 7L) return "${diffDays}d ago"
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }

    fun formatFullDateTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "--"
        return SimpleDateFormat("MMM d, yyyy • hh:mm:ss a", Locale.getDefault()).format(Date(timestampMs))
    }

    fun mapNotificationCategory(rawCategory: String, packageName: String): NotificationFilter {
        val raw = rawCategory.lowercase(Locale.US)
        val pkg = packageName.lowercase(Locale.US)
        return when {
            raw == "msg" || raw.contains("message") ||
                pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("sms") ||
                pkg.contains("messaging") -> NotificationFilter.MESSAGES
            raw == "social" ||
                pkg.contains("instagram") || pkg.contains("snapchat") ||
                pkg.contains("facebook") || pkg.contains("twitter") -> NotificationFilter.SOCIAL
            raw == "sys" || raw == "service" || raw == "alarm" ||
                pkg.contains("android") || pkg.contains("system") -> NotificationFilter.SYSTEM
            raw == "transport" || raw.contains("media") ||
                pkg.contains("youtube") || pkg.contains("spotify") -> NotificationFilter.MEDIA
            else -> NotificationFilter.ALL
        }
    }

    fun mapWellbeingCategory(rawCategory: String): WellbeingCategoryFilter {
        return when (rawCategory.lowercase(Locale.US).trim()) {
            "social" -> WellbeingCategoryFilter.SOCIAL
            "video" -> WellbeingCategoryFilter.VIDEO
            "game", "games" -> WellbeingCategoryFilter.GAME
            "productivity", "work" -> WellbeingCategoryFilter.PRODUCTIVITY
            else -> WellbeingCategoryFilter.OTHER
        }
    }

    fun rangeToWire(range: WellbeingRange): String {
        return when (range) {
            WellbeingRange.TODAY -> "today"
            WellbeingRange.SEVEN_DAYS -> "7d"
        }
    }

    fun rangeFromWire(raw: String?): WellbeingRange {
        return if (raw?.trim()?.lowercase(Locale.US) == "7d") {
            WellbeingRange.SEVEN_DAYS
        } else {
            WellbeingRange.TODAY
        }
    }
}
