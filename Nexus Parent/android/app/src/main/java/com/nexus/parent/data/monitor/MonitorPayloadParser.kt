package com.nexus.parent.data.monitor

import com.nexus.parent.state.AppLockAttemptItem
import com.nexus.parent.state.CallFilter
import com.nexus.parent.state.CallLogUiItem
import com.nexus.parent.state.KeyboardUiItem
import com.nexus.parent.state.NotificationUiItem
import com.nexus.parent.state.SmsUiItem
import com.nexus.parent.state.WellbeingAppItem
import com.nexus.parent.state.WellbeingRange
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class RawWellbeingBucket(
    val label: String,
    val durationMs: Long
)

sealed interface MonitorDataEvent {
    // Plain confirmation / error responses
    data object SmsStarted : MonitorDataEvent
    data object SmsStopped : MonitorDataEvent
    data class SmsError(val message: String) : MonitorDataEvent

    data object CallLogStarted : MonitorDataEvent
    data object CallLogStopped : MonitorDataEvent
    data class CallLogError(val message: String) : MonitorDataEvent

    data object NotificationStarted : MonitorDataEvent
    data object NotificationStopped : MonitorDataEvent
    data class NotificationError(val message: String) : MonitorDataEvent

    data object KeyboardStarted : MonitorDataEvent
    data object KeyboardStopped : MonitorDataEvent
    data class KeyboardError(val message: String) : MonitorDataEvent

    data object WellbeingStarted : MonitorDataEvent
    data object WellbeingStopped : MonitorDataEvent
    data class WellbeingError(val message: String) : MonitorDataEvent

    data object AppLockStarted : MonitorDataEvent
    data object AppLockStopped : MonitorDataEvent
    data object AppLockClearedAck : MonitorDataEvent
    data class AppLockError(val message: String) : MonitorDataEvent
    data class AppLockPinChanged(val success: Boolean, val message: String) : MonitorDataEvent
    data class AppLockStatus(val enabled: Boolean, val isLockedOut: Boolean) : MonitorDataEvent

    data class VibrateResult(val success: Boolean, val detail: String) : MonitorDataEvent
    data class FlashResult(val success: Boolean, val detail: String) : MonitorDataEvent
    data class VibrateFlashResult(val success: Boolean, val detail: String) : MonitorDataEvent

    data object WallpaperStarted : MonitorDataEvent
    data object WallpaperStopped : MonitorDataEvent
    data class WallpaperError(val message: String) : MonitorDataEvent
    data object WallpaperSetSuccess : MonitorDataEvent
    data class WallpaperSetError(val message: String) : MonitorDataEvent

    // JSON telemetry & snapshot payloads
    data class SmsHistoryChunk(
        val chunkIndex: Int,
        val totalChunks: Int,
        val messages: List<SmsUiItem>
    ) : MonitorDataEvent

    data class SmsNew(val item: SmsUiItem) : MonitorDataEvent

    data class CallLogHistory(val records: List<CallLogUiItem>) : MonitorDataEvent
    data class CallLogNew(val item: CallLogUiItem) : MonitorDataEvent

    data class NotificationSnapshot(val items: List<NotificationUiItem>) : MonitorDataEvent
    data class NotificationNew(val item: NotificationUiItem) : MonitorDataEvent
    data class NotificationRemoved(val key: String) : MonitorDataEvent

    data class KeyboardSnapshot(val records: List<KeyboardUiItem>) : MonitorDataEvent
    data class KeyboardNew(val record: KeyboardUiItem) : MonitorDataEvent

    data class WellbeingSnapshotChunk(
        val range: WellbeingRange,
        val chunkIndex: Int,
        val totalChunks: Int,
        val totalScreenTimeMs: Long,
        val unlockCount: Int,
        val totalLaunches: Int,
        val activeAppName: String?,
        val generatedAtMs: Long,
        val buckets: List<RawWellbeingBucket>,
        val apps: List<WellbeingAppItem>
    ) : MonitorDataEvent

    data class AppLockSnapshotChunk(
        val chunkIndex: Int,
        val totalChunks: Int,
        val records: List<AppLockAttemptItem>
    ) : MonitorDataEvent

    data class AppLockNew(val record: AppLockAttemptItem) : MonitorDataEvent

    data class WallpaperChunk(
        val screen: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val data: String,
        val isSame: Boolean
    ) : MonitorDataEvent
}

/**
 * MonitorPayloadParser.kt - Pure parser turning incoming DataChannel strings into strongly typed
 * `MonitorDataEvent` instances, verified 1:1 against Child command handlers & Desktop `types.ts`.
 */
object MonitorPayloadParser {

    fun parse(rawMessage: String, nowMs: Long = System.currentTimeMillis()): MonitorDataEvent? {
        val trimmed = rawMessage.trim()
        if (trimmed.isEmpty()) return null

        parsePlainMessage(trimmed)?.let { return it }

        if (!trimmed.startsWith("{")) return null
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val type = json.optString("type")

        return when (type) {
            MonitorProtocol.TYPE_SMS_HISTORY -> parseSmsHistory(json, nowMs)
            MonitorProtocol.TYPE_SMS_NEW -> json.optJSONObject("message")?.let {
                MonitorDataEvent.SmsNew(parseSmsRecord(it, nowMs))
            }
            MonitorProtocol.TYPE_CALLLOG_HISTORY -> parseCallLogHistory(json, nowMs)
            MonitorProtocol.TYPE_CALLLOG_NEW -> json.optJSONObject("record")?.let {
                MonitorDataEvent.CallLogNew(parseCallRecord(it, nowMs))
            }
            MonitorProtocol.TYPE_NOTIFICATION_SNAPSHOT -> parseNotificationSnapshot(json, nowMs)
            MonitorProtocol.TYPE_NOTIFICATION_NEW -> json.optJSONObject("notification")?.let {
                MonitorDataEvent.NotificationNew(parseNotificationRecord(it, nowMs))
            }
            MonitorProtocol.TYPE_NOTIFICATION_REMOVED -> {
                val key = json.optString("key").trim()
                if (key.isEmpty()) null else MonitorDataEvent.NotificationRemoved(key)
            }
            MonitorProtocol.TYPE_KEYBOARD_SNAPSHOT -> parseKeyboardSnapshot(json, nowMs)
            MonitorProtocol.TYPE_KEYBOARD_NEW -> json.optJSONObject("record")?.let {
                MonitorDataEvent.KeyboardNew(parseKeyboardRecord(it, nowMs))
            }
            MonitorProtocol.TYPE_WELLBEING_SNAPSHOT -> parseWellbeingSnapshot(json, nowMs)
            MonitorProtocol.RESP_APPLOCK_STATUS -> MonitorDataEvent.AppLockStatus(
                enabled = json.optBoolean("enabled", false),
                isLockedOut = json.optBoolean("isLockedOut", false)
            )
            MonitorProtocol.TYPE_APPLOCK_SNAPSHOT -> parseAppLockSnapshot(json)
            MonitorProtocol.TYPE_APPLOCK_NEW -> json.optJSONObject("record")?.let {
                MonitorDataEvent.AppLockNew(parseAppLockRecord(it))
            }
            MonitorProtocol.TYPE_WALLPAPER_CHUNK -> MonitorDataEvent.WallpaperChunk(
                screen = json.optString("screen", "home").lowercase(Locale.US),
                chunkIndex = json.optInt("chunkIndex", 0).coerceAtLeast(0),
                totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
                data = json.optString("data", ""),
                isSame = json.optBoolean("isSame", true)
            )
            else -> null
        }
    }

    private fun parsePlainMessage(trimmed: String): MonitorDataEvent? {
        return when {
            trimmed == MonitorProtocol.RESP_SMS_STARTED -> MonitorDataEvent.SmsStarted
            trimmed == MonitorProtocol.RESP_SMS_STOPPED -> MonitorDataEvent.SmsStopped
            trimmed.startsWith(MonitorProtocol.RESP_SMS_ERROR_PREFIX) ->
                MonitorDataEvent.SmsError(trimmed.removePrefix(MonitorProtocol.RESP_SMS_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_CALLLOG_STARTED -> MonitorDataEvent.CallLogStarted
            trimmed == MonitorProtocol.RESP_CALLLOG_STOPPED -> MonitorDataEvent.CallLogStopped
            trimmed.startsWith(MonitorProtocol.RESP_CALLLOG_ERROR_PREFIX) ->
                MonitorDataEvent.CallLogError(trimmed.removePrefix(MonitorProtocol.RESP_CALLLOG_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_NOTIFICATION_STARTED -> MonitorDataEvent.NotificationStarted
            trimmed == MonitorProtocol.RESP_NOTIFICATION_STOPPED -> MonitorDataEvent.NotificationStopped
            trimmed.startsWith(MonitorProtocol.RESP_NOTIFICATION_ERROR_PREFIX) ->
                MonitorDataEvent.NotificationError(trimmed.removePrefix(MonitorProtocol.RESP_NOTIFICATION_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_KEYBOARD_STARTED -> MonitorDataEvent.KeyboardStarted
            trimmed == MonitorProtocol.RESP_KEYBOARD_STOPPED -> MonitorDataEvent.KeyboardStopped
            trimmed.startsWith(MonitorProtocol.RESP_KEYBOARD_ERROR_PREFIX) ->
                MonitorDataEvent.KeyboardError(trimmed.removePrefix(MonitorProtocol.RESP_KEYBOARD_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_WELLBEING_STARTED -> MonitorDataEvent.WellbeingStarted
            trimmed == MonitorProtocol.RESP_WELLBEING_STOPPED -> MonitorDataEvent.WellbeingStopped
            trimmed.startsWith(MonitorProtocol.RESP_WELLBEING_ERROR_PREFIX) ->
                MonitorDataEvent.WellbeingError(trimmed.removePrefix(MonitorProtocol.RESP_WELLBEING_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_APPLOCK_STARTED -> MonitorDataEvent.AppLockStarted
            trimmed == MonitorProtocol.RESP_APPLOCK_STOPPED -> MonitorDataEvent.AppLockStopped
            trimmed == MonitorProtocol.RESP_APPLOCK_CLEARED_ACK -> MonitorDataEvent.AppLockClearedAck
            trimmed.startsWith(MonitorProtocol.RESP_APPLOCK_ERROR_PREFIX) ->
                MonitorDataEvent.AppLockError(trimmed.removePrefix(MonitorProtocol.RESP_APPLOCK_ERROR_PREFIX))
            trimmed.startsWith(MonitorProtocol.RESP_APPLOCK_PIN_CHANGED_PREFIX) -> {
                val result = trimmed.removePrefix(MonitorProtocol.RESP_APPLOCK_PIN_CHANGED_PREFIX)
                if (result == "SUCCESS") {
                    MonitorDataEvent.AppLockPinChanged(true, "PIN updated successfully on device")
                } else {
                    MonitorDataEvent.AppLockPinChanged(false, result.removePrefix("ERROR:"))
                }
            }

            trimmed.startsWith(MonitorProtocol.RESP_VIBRATE_SUCCESS_PREFIX) ->
                MonitorDataEvent.VibrateResult(true, trimmed.removePrefix(MonitorProtocol.RESP_VIBRATE_SUCCESS_PREFIX))
            trimmed.startsWith(MonitorProtocol.RESP_VIBRATE_ERROR_PREFIX) ->
                MonitorDataEvent.VibrateResult(false, trimmed.removePrefix(MonitorProtocol.RESP_VIBRATE_ERROR_PREFIX))

            trimmed.startsWith(MonitorProtocol.RESP_FLASH_SUCCESS_PREFIX) ->
                MonitorDataEvent.FlashResult(true, trimmed.removePrefix(MonitorProtocol.RESP_FLASH_SUCCESS_PREFIX))
            trimmed.startsWith(MonitorProtocol.RESP_FLASH_ERROR_PREFIX) ->
                MonitorDataEvent.FlashResult(false, trimmed.removePrefix(MonitorProtocol.RESP_FLASH_ERROR_PREFIX))

            trimmed.startsWith(MonitorProtocol.RESP_VIBRATE_FLASH_SUCCESS_PREFIX) ->
                MonitorDataEvent.VibrateFlashResult(true, trimmed.removePrefix(MonitorProtocol.RESP_VIBRATE_FLASH_SUCCESS_PREFIX))
            trimmed.startsWith(MonitorProtocol.RESP_VIBRATE_FLASH_ERROR_PREFIX) ->
                MonitorDataEvent.VibrateFlashResult(false, trimmed.removePrefix(MonitorProtocol.RESP_VIBRATE_FLASH_ERROR_PREFIX))

            trimmed == MonitorProtocol.RESP_WALLPAPER_STARTED -> MonitorDataEvent.WallpaperStarted
            trimmed == MonitorProtocol.RESP_WALLPAPER_STOPPED -> MonitorDataEvent.WallpaperStopped
            trimmed.startsWith(MonitorProtocol.RESP_WALLPAPER_ERROR_PREFIX) ->
                MonitorDataEvent.WallpaperError(trimmed.removePrefix(MonitorProtocol.RESP_WALLPAPER_ERROR_PREFIX))
            trimmed == MonitorProtocol.RESP_WALLPAPER_SET_SUCCESS -> MonitorDataEvent.WallpaperSetSuccess
            trimmed.startsWith(MonitorProtocol.RESP_WALLPAPER_SET_ERROR_PREFIX) ->
                MonitorDataEvent.WallpaperSetError(trimmed.removePrefix(MonitorProtocol.RESP_WALLPAPER_SET_ERROR_PREFIX))

            else -> null
        }
    }

    private fun parseSmsHistory(json: JSONObject, nowMs: Long): MonitorDataEvent.SmsHistoryChunk {
        val arr = json.optJSONArray("messages")
        val list = parseJsonObjects(arr) { parseSmsRecord(it, nowMs) }
        return MonitorDataEvent.SmsHistoryChunk(
            chunkIndex = json.optInt("chunkIndex", 0).coerceAtLeast(0),
            totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
            messages = list
        )
    }

    private fun parseSmsRecord(obj: JSONObject, nowMs: Long): SmsUiItem {
        val ts = obj.optLong("timestamp", 0L)
        val smsType = obj.optString("smsType", "unknown").lowercase(Locale.US)
        val address = obj.optString("address", "")
        val sender = obj.optString("senderName", "").trim()
        return SmsUiItem(
            id = obj.optString("id", "${ts}_${address.hashCode()}"),
            senderName = sender,
            address = address,
            body = obj.optString("body", ""),
            isIncoming = smsType == "inbox",
            isUnread = !obj.optBoolean("read", true),
            simSlot = MonitorProtocol.formatSimSlot(obj.optString("simId", "")),
            relativeTime = MonitorProtocol.formatRelativeTime(ts, nowMs),
            fullDateTime = MonitorProtocol.formatFullDateTime(ts),
            timestampMs = ts
        )
    }

    private fun parseCallLogHistory(json: JSONObject, nowMs: Long): MonitorDataEvent.CallLogHistory {
        val arr = json.optJSONArray("records")
        val list = parseJsonObjects(arr) { parseCallRecord(it, nowMs) }
            .sortedByDescending { it.timestampMs }
        return MonitorDataEvent.CallLogHistory(list)
    }

    private fun parseCallRecord(obj: JSONObject, nowMs: Long): CallLogUiItem {
        val ts = obj.optLong("timestamp", 0L)
        val rawCallType = obj.optString("callType", "unknown").lowercase(Locale.US)
        val filterType = when (rawCallType) {
            "missed" -> CallFilter.MISSED
            "incoming" -> CallFilter.INCOMING
            "outgoing" -> CallFilter.OUTGOING
            "rejected", "blocked" -> CallFilter.DECLINED
            else -> CallFilter.ALL
        }
        val name = obj.optString("name", "").trim()
        val number = obj.optString("number", "").trim()
        val durationSec = obj.optInt("duration", 0).coerceAtLeast(0)
        val features = buildList {
            val featArr = obj.optJSONArray("features")
            if (featArr != null) {
                for (i in 0 until featArr.length()) {
                    val v = featArr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }
        return CallLogUiItem(
            id = obj.optString("id", "${ts}_${number.hashCode()}"),
            displayName = name.ifEmpty { number.ifEmpty { "Unknown" } },
            number = number,
            numberType = obj.optString("numberType", "General").ifBlank { "General" },
            filterType = filterType,
            durationLabel = MonitorProtocol.formatCallDuration(durationSec),
            simSlot = MonitorProtocol.formatSimSlot(obj.optString("simId", "")),
            relativeTime = MonitorProtocol.formatRelativeTime(ts, nowMs),
            fullDateTime = MonitorProtocol.formatFullDateTime(ts),
            timestampMs = ts,
            durationSeconds = durationSec,
            directionLabel = rawCallType.replaceFirstChar { it.uppercaseChar() },
            location = obj.optString("location", "").trim().takeIf { it.isNotEmpty() },
            features = features
        )
    }

    private fun parseNotificationSnapshot(json: JSONObject, nowMs: Long): MonitorDataEvent.NotificationSnapshot {
        val arr = json.optJSONArray("notifications")
        val list = parseJsonObjects(arr) { parseNotificationRecord(it, nowMs) }
            .sortedByDescending { it.timestampMs }
        return MonitorDataEvent.NotificationSnapshot(list)
    }

    private fun parseNotificationRecord(obj: JSONObject, nowMs: Long): NotificationUiItem {
        val postTime = obj.optLong("postTime", 0L)
        val packageName = obj.optString("packageName", "")
        val rawCat = obj.optString("category", "")
        val text = obj.optString("text", "")
        val bigText = obj.optString("bigText", "").trim().takeIf { it.isNotEmpty() && it != text }
        val subText = obj.optString("subText", "").trim().takeIf { it.isNotEmpty() }
        val messages = buildList {
            val msgArr = obj.optJSONArray("messages")
            if (msgArr != null) {
                for (i in 0 until msgArr.length()) {
                    val mObj = msgArr.optJSONObject(i) ?: continue
                    val sender = mObj.optString("sender", "").trim()
                    val mText = mObj.optString("text", "").trim()
                    if (mText.isNotEmpty()) {
                        add(if (sender.isNotEmpty()) "$sender: $mText" else mText)
                    }
                }
            }
        }
        return NotificationUiItem(
            key = obj.optString("key", "${postTime}_${packageName}"),
            appName = obj.optString("appName", packageName).ifBlank { packageName },
            packageName = packageName,
            title = obj.optString("title", ""),
            body = bigText ?: text,
            category = MonitorProtocol.mapNotificationCategory(rawCat, packageName),
            relativeTime = MonitorProtocol.formatRelativeTime(postTime, nowMs),
            fullDateTime = MonitorProtocol.formatFullDateTime(postTime),
            timestampMs = postTime,
            bigText = bigText,
            subText = subText,
            messages = messages
        )
    }

    private fun parseKeyboardSnapshot(json: JSONObject, nowMs: Long): MonitorDataEvent.KeyboardSnapshot {
        val arr = json.optJSONArray("records")
        val list = parseJsonObjects(arr) { parseKeyboardRecord(it, nowMs) }
        return MonitorDataEvent.KeyboardSnapshot(list)
    }

    private fun parseKeyboardRecord(obj: JSONObject, nowMs: Long): KeyboardUiItem {
        val ts = obj.optLong("timestamp", 0L)
        return KeyboardUiItem(
            id = obj.optString("id", "${ts}_${obj.optString("text", "").hashCode()}"),
            appName = obj.optString("appName", obj.optString("app", "App")),
            fieldHint = obj.optString("fieldHint", "").trim().takeIf { it.isNotEmpty() },
            text = obj.optString("text", ""),
            inputType = obj.optString("inputType", "TEXT"),
            isPassword = obj.optBoolean("isPassword", false),
            relativeTime = MonitorProtocol.formatRelativeTime(ts, nowMs),
            packageName = obj.optString("app", ""),
            timestampMs = ts
        )
    }

    private fun parseWellbeingSnapshot(json: JSONObject, nowMs: Long): MonitorDataEvent.WellbeingSnapshotChunk {
        val buckets = parseJsonObjects(json.optJSONArray("buckets")) { bObj ->
            RawWellbeingBucket(
                label = bObj.optString("label", ""),
                durationMs = bObj.optLong("durationMs", 0L).coerceAtLeast(0L)
            )
        }
        val apps = parseJsonObjects(json.optJSONArray("apps")) { aObj ->
            val totalTimeMs = aObj.optLong("totalTimeMs", 0L).coerceAtLeast(0L)
            WellbeingAppItem(
                packageName = aObj.optString("packageName", ""),
                appName = aObj.optString("appName", aObj.optString("packageName", "")),
                category = MonitorProtocol.mapWellbeingCategory(aObj.optString("category", "other")),
                durationLabel = MonitorProtocol.formatScreenTimeDuration(totalTimeMs),
                launchCount = aObj.optInt("launchCount", 0).coerceAtLeast(0),
                percent = aObj.optInt("percent", 0).coerceIn(0, 100),
                totalTimeMs = totalTimeMs
            )
        }
        return MonitorDataEvent.WellbeingSnapshotChunk(
            range = MonitorProtocol.rangeFromWire(json.optString("range", "today")),
            chunkIndex = json.optInt("chunkIndex", 0).coerceAtLeast(0),
            totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
            totalScreenTimeMs = json.optLong("totalScreenTimeMs", 0L).coerceAtLeast(0L),
            unlockCount = json.optInt("unlockCount", 0).coerceAtLeast(0),
            totalLaunches = json.optInt("totalLaunches", 0).coerceAtLeast(0),
            activeAppName = json.optString("activeAppName", "").trim().takeIf { it.isNotEmpty() && it != "null" },
            generatedAtMs = json.optLong("generatedAt", nowMs),
            buckets = buckets,
            apps = apps
        )
    }

    private fun parseAppLockSnapshot(json: JSONObject): MonitorDataEvent.AppLockSnapshotChunk {
        val list = parseJsonObjects(json.optJSONArray("records")) { parseAppLockRecord(it) }
        return MonitorDataEvent.AppLockSnapshotChunk(
            chunkIndex = json.optInt("chunkIndex", 0).coerceAtLeast(0),
            totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
            records = list
        )
    }

    private fun parseAppLockRecord(obj: JSONObject): AppLockAttemptItem {
        val ts = obj.optLong("timestamp", 0L)
        val rawSelfie = obj.optString("selfie", "").trim().takeIf { it.isNotEmpty() && it != "null" }
        return AppLockAttemptItem(
            id = obj.optString("id", "applock_$ts"),
            timestampLabel = MonitorProtocol.formatRelativeTime(ts),
            fullDateTime = MonitorProtocol.formatFullDateTime(ts),
            pinCorrect = obj.optBoolean("pinCorrect", false),
            isLockedOut = obj.optBoolean("isLockedOut", false),
            failedAttempts = obj.optInt("failedAttempts", 0).coerceAtLeast(0),
            hasSelfiePreview = rawSelfie != null,
            timestampMs = ts,
            selfieBase64 = rawSelfie
        )
    }

    private inline fun <T> parseJsonObjects(array: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(mapper(obj))
            }
        }
    }
}
