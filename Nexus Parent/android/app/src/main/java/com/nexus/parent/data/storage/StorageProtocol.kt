package com.nexus.parent.data.storage

import com.nexus.parent.state.StorageBreadcrumb
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * StorageProtocol.kt - Wire constants, command builders, and Desktop-matching formatters
 * for Remote Storage Monitoring (`desktop/src/ts/main-content/storage/types.ts`).
 *
 * Hey friend! All DataChannel commands and responses match Desktop and Child's
 * `StorageCommandHandler.kt` verbatim:
 * - Parent -> Child: `STORAGE_CHECK_DIR`, `STORAGE_LIST`, `STORAGE_GET_FILE`,
 *   `STORAGE_CANCEL_TRANSFER`, `STORAGE_DELETE_FILE`
 * - Child -> Parent: `STORAGE_DIR_UNCHANGED`, `STORAGE_DIR_CHANGED`, `STORAGE_LIST_BATCH`,
 *   `STORAGE_LIST_ERROR`, `STORAGE_FILE_START`, `STORAGE_FILE_CHUNK`, `STORAGE_FILE_COMPLETE`,
 *   `STORAGE_FILE_ERROR`, `STORAGE_DELETE_SUCCESS`, `STORAGE_DELETE_ERROR`
 */
object StorageProtocol {

    const val DEFAULT_STORAGE_ROOT = "/storage/emulated/0"
    const val DEFAULT_PAGE_LIMIT = 40

    const val PURPOSE_DOWNLOAD = "download"
    const val PURPOSE_PREVIEW = "preview"

    // Outbound commands (Parent -> Child)
    const val CMD_STORAGE_CHECK_DIR = "STORAGE_CHECK_DIR"
    const val CMD_STORAGE_LIST = "STORAGE_LIST"
    const val CMD_STORAGE_GET_FILE = "STORAGE_GET_FILE"
    const val CMD_STORAGE_CANCEL_TRANSFER = "STORAGE_CANCEL_TRANSFER"
    const val CMD_STORAGE_DELETE_FILE = "STORAGE_DELETE_FILE"

    // Inbound response packet types (Child -> Parent)
    const val TYPE_STORAGE_DIR_UNCHANGED = "STORAGE_DIR_UNCHANGED"
    const val TYPE_STORAGE_DIR_CHANGED = "STORAGE_DIR_CHANGED"
    const val TYPE_STORAGE_LIST_BATCH = "STORAGE_LIST_BATCH"
    const val TYPE_STORAGE_LIST_ERROR = "STORAGE_LIST_ERROR"
    const val TYPE_STORAGE_FILE_START = "STORAGE_FILE_START"
    const val TYPE_STORAGE_FILE_CHUNK = "STORAGE_FILE_CHUNK"
    const val TYPE_STORAGE_FILE_COMPLETE = "STORAGE_FILE_COMPLETE"
    const val TYPE_STORAGE_FILE_ERROR = "STORAGE_FILE_ERROR"
    const val TYPE_STORAGE_DELETE_SUCCESS = "STORAGE_DELETE_SUCCESS"
    const val TYPE_STORAGE_DELETE_ERROR = "STORAGE_DELETE_ERROR"

    private const val ONE_GB_BYTES = 1024.0 * 1024.0 * 1024.0

    fun buildCheckDirCommand(path: String, cachedModified: Long, cachedCount: Int): String {
        // NOTE: Do NOT use Android's org.json.JSONObject.toString(), because it escapes '/' as '\/'
        // and Child's StorageCommandHandler regex parser expects unescaped '/' matching JS JSON.stringify.
        return "{" +
            "\"type\":\"${escapeJson(CMD_STORAGE_CHECK_DIR)}\"," +
            "\"path\":\"${escapeJson(path)}\"," +
            "\"cachedModified\":$cachedModified," +
            "\"cachedCount\":$cachedCount" +
            "}"
    }

    fun buildListCommand(path: String, offset: Int = 0, limit: Int = DEFAULT_PAGE_LIMIT): String {
        return "{" +
            "\"type\":\"${escapeJson(CMD_STORAGE_LIST)}\"," +
            "\"path\":\"${escapeJson(path)}\"," +
            "\"offset\":$offset," +
            "\"limit\":$limit" +
            "}"
    }

    fun buildGetFileCommand(path: String, transferId: String, purpose: String): String {
        return "{" +
            "\"type\":\"${escapeJson(CMD_STORAGE_GET_FILE)}\"," +
            "\"path\":\"${escapeJson(path)}\"," +
            "\"transferId\":\"${escapeJson(transferId)}\"," +
            "\"purpose\":\"${escapeJson(purpose)}\"" +
            "}"
    }

    fun buildCancelTransferCommand(transferId: String): String {
        return "{" +
            "\"type\":\"${escapeJson(CMD_STORAGE_CANCEL_TRANSFER)}\"," +
            "\"transferId\":\"${escapeJson(transferId)}\"" +
            "}"
    }

    fun buildDeleteFileCommand(path: String): String {
        return "{" +
            "\"type\":\"${escapeJson(CMD_STORAGE_DELETE_FILE)}\"," +
            "\"path\":\"${escapeJson(path)}\"" +
            "}"
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Converts raw byte counts cleanly matching Desktop `formatBytes()` (`"0 B"`, `"4.2 MB"`, `"128 GB"`).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val unitIndex = floor(ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val scaled = bytes.toDouble() / 1024.0.pow(unitIndex.toDouble())
        val formatted = if (scaled >= 100.0 || unitIndex == 0) {
            String.format(Locale.US, "%.0f", scaled)
        } else {
            String.format(Locale.US, "%.1f", scaled)
        }
        return "$formatted ${units[unitIndex]}"
    }

    /**
     * Converts epoch milliseconds into relative labels matching Desktop `formatRelativeTime()`.
     */
    fun formatRelativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "Unknown"
        val diffSeconds = ((nowMillis - epochMillis) / 1000L).coerceAtLeast(0L)
        return when {
            diffSeconds < 60L -> "Just now"
            diffSeconds < 3600L -> "${diffSeconds / 60L} mins ago"
            diffSeconds < 86_400L -> "${diffSeconds / 3600L} hours ago"
            diffSeconds < 172_800L -> "Yesterday"
            else -> {
                val targetCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
                val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val pattern = if (targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                    "MMM d"
                } else {
                    "MMM d, yyyy"
                }
                SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))
            }
        }
    }

    /**
     * Formats the top storage capacity gauge matching Desktop `updateStorageGauge()`.
     */
    fun formatStorageCapacityStrip(freeBytes: Long, totalBytes: Long): Pair<String, Float> {
        if (totalBytes <= 0L) {
            return "STORAGE TELEMETRY UNAVAILABLE" to 0f
        }
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        val usedFraction = (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        val usedPercent = (usedFraction * 100f).roundToInt().coerceIn(0, 100)
        val usedGb = String.format(Locale.US, "%.1f", usedBytes / ONE_GB_BYTES)
        val totalGb = String.format(Locale.US, "%.1f", totalBytes / ONE_GB_BYTES)
        return "$usedGb GB / $totalGb GB USED ($usedPercent%)" to usedFraction
    }

    /**
     * Builds clickable path segments starting safely from `/storage/emulated/0` (`0 (Internal)`).
     */
    fun buildBreadcrumbs(path: String): List<StorageBreadcrumb> {
        val cleanPath = path.trim().ifBlank { DEFAULT_STORAGE_ROOT }
        if (!cleanPath.startsWith(DEFAULT_STORAGE_ROOT)) {
            val parts = cleanPath.split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) return listOf(StorageBreadcrumb("0 (Internal)", DEFAULT_STORAGE_ROOT))
            var acc = ""
            return parts.map { part ->
                acc += "/$part"
                val label = if (part == "0") "0 (Internal)" else part
                StorageBreadcrumb(label = label, fullPath = acc)
            }
        }

        val relative = cleanPath.removePrefix(DEFAULT_STORAGE_ROOT)
            .split('/')
            .filter { it.isNotBlank() }

        val result = mutableListOf(StorageBreadcrumb("0 (Internal)", DEFAULT_STORAGE_ROOT))
        var acc = DEFAULT_STORAGE_ROOT
        for (segment in relative) {
            acc = "$acc/$segment"
            result.add(StorageBreadcrumb(label = segment, fullPath = acc))
        }
        return result
    }

    /**
     * Resolves parent directory while clamping at `/storage/emulated/0` so parents do not
     * ascend into unreadable Android system directories (`/storage/emulated` or `/`).
     */
    fun computeParentPath(currentPath: String): String? {
        val clean = currentPath.trimEnd('/').ifBlank { DEFAULT_STORAGE_ROOT }
        if (clean == DEFAULT_STORAGE_ROOT || !clean.startsWith(DEFAULT_STORAGE_ROOT)) {
            return null
        }
        val parent = clean.substringBeforeLast('/', "")
        return if (parent.length < DEFAULT_STORAGE_ROOT.length) {
            DEFAULT_STORAGE_ROOT
        } else {
            parent
        }
    }
}
