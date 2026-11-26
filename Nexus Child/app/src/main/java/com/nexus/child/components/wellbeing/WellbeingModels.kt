package com.nexus.child.components.wellbeing

import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol constants and data contracts for Digital Wellbeing telemetry on Nexus Child.
 *
 * Hey friend! These constants must match the Desktop Parent (`right-sidebar/types.ts`)
 * so both sides speak the exact same WebRTC DataChannel protocol.
 */
object WellbeingConstants {
    const val CMD_WELLBEING_ON = "WELLBEING_ON"
    const val CMD_WELLBEING_OFF = "WELLBEING_OFF"
    const val CMD_WELLBEING_REFRESH = "WELLBEING_REFRESH"
    const val CMD_WELLBEING_RANGE_PREFIX = "WELLBEING_RANGE:"

    const val RESP_WELLBEING_STARTED = "WELLBEING_STARTED"
    const val RESP_WELLBEING_STOPPED = "WELLBEING_STOPPED"
    const val RESP_WELLBEING_ERROR_PREFIX = "WELLBEING_ERROR: "

    const val TYPE_WELLBEING_SNAPSHOT = "WELLBEING_SNAPSHOT"

    const val POLL_INTERVAL_MS = 15_000L
    const val APPS_CHUNK_SIZE = 25
}

/**
 * Supported aggregation time windows for Digital Wellbeing telemetry.
 */
enum class WellbeingRange(val wireValue: String) {
    TODAY("today"),
    SEVEN_DAYS("7d");

    companion object {
        fun fromWire(raw: String?): WellbeingRange {
            return if (raw?.trim()?.lowercase() == "7d") SEVEN_DAYS else TODAY
        }
    }
}

/**
 * Lightweight, framework-free representation of an Android UsageEvents.Event entry.
 * Keeping this pure allows deterministic unit testing on the JVM without Android mocks.
 */
data class RawUsageEvent(
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long
)

/**
 * Resolved human-readable metadata for an installed Android package.
 */
data class AppMetadata(
    val appName: String,
    val category: String
)

/**
 * Aggregated screen time and launch telemetry for a single installed application.
 */
data class WellbeingAppUsage(
    val packageName: String,
    val appName: String,
    val category: String,
    val totalTimeMs: Long,
    val launchCount: numberOrInt = 0,
    val lastUsedTimestamp: Long,
    val percent: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("packageName", packageName)
            put("appName", appName)
            put("category", category)
            put("totalTimeMs", totalTimeMs)
            put("launchCount", launchCount)
            put("lastUsedTimestamp", lastUsedTimestamp)
            put("percent", percent)
        }
    }
}

typealias numberOrInt = Int

/**
 * Single histogram bucket (hourly for Today, daily for 7 Days).
 */
data class WellbeingTimeBucket(
    val label: String,
    val durationMs: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("label", label)
            put("durationMs", durationMs)
        }
    }
}

/**
 * Complete aggregated Digital Wellbeing snapshot ready for chunked DataChannel transmission.
 */
data class WellbeingSnapshot(
    val range: WellbeingRange,
    val totalScreenTimeMs: Long,
    val unlockCount: Int,
    val totalLaunches: Int,
    val activeAppName: String?,
    val activePackageName: String?,
    val generatedAt: Long,
    val buckets: List<WellbeingTimeBucket>,
    val apps: List<WellbeingAppUsage>
) {
    /**
     * Splits the app list into DataChannel-safe chunks (`chunkIndex` / `totalChunks`).
     * Summary metrics and histogram buckets are attached to chunk `0`.
     */
    fun toChunkedJsonList(chunkSize: Int = WellbeingConstants.APPS_CHUNK_SIZE): List<JSONObject> {
        val safeChunkSize = chunkSize.coerceAtLeast(1)
        val appChunks = if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(safeChunkSize)
        val totalChunks = appChunks.size

        return appChunks.mapIndexed { index, chunk ->
            JSONObject().apply {
                put("type", WellbeingConstants.TYPE_WELLBEING_SNAPSHOT)
                put("range", range.wireValue)
                put("chunkIndex", index)
                put("totalChunks", totalChunks)

                if (index == 0) {
                    put("totalScreenTimeMs", totalScreenTimeMs)
                    put("unlockCount", unlockCount)
                    put("totalLaunches", totalLaunches)
                    if (activeAppName != null) put("activeAppName", activeAppName) else put("activeAppName", JSONObject.NULL)
                    if (activePackageName != null) put("activePackageName", activePackageName) else put("activePackageName", JSONObject.NULL)
                    put("generatedAt", generatedAt)

                    val bucketsArray = JSONArray()
                    buckets.forEach { bucketsArray.put(it.toJson()) }
                    put("buckets", bucketsArray)
                }

                val appsArray = JSONArray()
                chunk.forEach { appsArray.put(it.toJson()) }
                put("apps", appsArray)
            }
        }
    }
}
