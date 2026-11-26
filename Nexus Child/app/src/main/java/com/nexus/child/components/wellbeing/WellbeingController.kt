package com.nexus.child.components.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WellbeingController queries Android UsageStatsManager / UsageEvents, enriches apps with
 * PackageManager labels and categories, and streams chunked Digital Wellbeing snapshots over WebRTC.
 *
 * Hey friend! Here is how it works safely:
 * 1. Checks `AppOpsManager.OPSTR_GET_USAGE_STATS` before querying UsageStatsManager.
 * 2. Queries `UsageEvents` with a 6-hour lookback prior to local midnight so apps opened
 *    before midnight are paired cleanly without leaking yesterday's screen time.
 * 3. Caches resolved `PackageManager` app labels and categories to avoid repeated binder lookups.
 * 4. Polls every 15 seconds while monitoring is active, cancelling immediately on stop or disconnect.
 */
class WellbeingController(
    private val context: Context,
    private val dataSender: (String) -> Boolean
) : IWellbeingController {

    companion object {
        private const val TAG = "WellbeingController"
        private const val LOOKBACK_BUFFER_MS = 6L * 60L * 60L * 1000L

        /**
         * Single source of truth for checking Android Usage Access (`OPSTR_GET_USAGE_STATS`).
         * Uses `unsafeCheckOpNoThrow` without logging an audit event (`noteOpNoThrow`).
         */
        @Suppress("DEPRECATION")
        fun hasUsageStatsPermission(context: Context): Boolean {
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                    ?: return false
                val mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify Usage Access permission: ${e.message}", e)
                false
            }
        }
    }

    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isMonitoring = AtomicBoolean(false)
    private val metadataCache = ConcurrentHashMap<String, AppMetadata>()

    @Volatile
    private var currentRange: WellbeingRange = WellbeingRange.TODAY

    private var pollingJob: Job? = null

    override fun hasUsageAccess(): Boolean = hasUsageStatsPermission(context)

    override fun startMonitoring(range: WellbeingRange): Boolean {
        if (!hasUsageAccess()) {
            Log.w(TAG, "Cannot start Wellbeing monitoring: Usage Access not granted.")
            return false
        }

        currentRange = range
        val alreadyActive = isMonitoring.getAndSet(true)
        Log.d(TAG, "Starting Digital Wellbeing monitoring (range=${range.wireValue}, alreadyActive=$alreadyActive)")

        // Acknowledge immediately so Parent's loading spinner flips to LIVE cleanly
        dataSender(WellbeingConstants.RESP_WELLBEING_STARTED)

        // Dispatch initial snapshot immediately on IO thread
        controllerScope.launch {
            collectAndSendSnapshot(currentRange)
        }

        // Start or ensure 15-second live polling loop
        if (pollingJob?.isActive != true) {
            pollingJob = controllerScope.launch {
                while (isActive && isMonitoring.get()) {
                    delay(WellbeingConstants.POLL_INTERVAL_MS)
                    if (isActive && isMonitoring.get()) {
                        collectAndSendSnapshot(currentRange)
                    }
                }
            }
        }

        return true
    }

    override fun stopMonitoring(): Boolean {
        val wasActive = isMonitoring.getAndSet(false)
        pollingJob?.cancel()
        pollingJob = null
        Log.d(TAG, "Stopped Digital Wellbeing monitoring (wasActive=$wasActive)")
        dataSender(WellbeingConstants.RESP_WELLBEING_STOPPED)
        return wasActive
    }

    override fun refreshNow(range: WellbeingRange?): Boolean {
        if (!hasUsageAccess()) {
            return false
        }
        if (range != null) {
            currentRange = range
        }
        val activeRange = currentRange
        controllerScope.launch {
            collectAndSendSnapshot(activeRange)
        }
        return true
    }

    override fun setRange(range: WellbeingRange) {
        currentRange = range
    }

    override fun getCurrentRange(): WellbeingRange = currentRange

    override fun isMonitoringActive(): Boolean = isMonitoring.get()

    override fun cleanup() {
        isMonitoring.set(false)
        pollingJob?.cancel()
        pollingJob = null
        metadataCache.clear()
        controllerScope.cancel()
        Log.d(TAG, "WellbeingController cleaned up")
    }

    private fun collectAndSendSnapshot(range: WellbeingRange) {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager == null) {
                dataSender("${WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX}UsageStatsManager unavailable on this device")
                return
            }

            val nowMs = System.currentTimeMillis()
            val (windowStartMs, windowEndMs) = WellbeingUsageAggregator.computeWindowBounds(range, nowMs)
            val queryStartMs = (windowStartMs - LOOKBACK_BUFFER_MS).coerceAtLeast(0L)

            val rawEvents = mutableListOf<RawUsageEvent>()
            val usageEvents = usageStatsManager.queryEvents(queryStartMs, windowEndMs)
            if (usageEvents != null) {
                val eventOut = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(eventOut)
                    val pkg = eventOut.packageName ?: ""
                    rawEvents.add(
                        RawUsageEvent(
                            packageName = pkg,
                            eventType = eventOut.eventType,
                            timestampMs = eventOut.timeStamp
                        )
                    )
                }
            }

            var snapshot = WellbeingUsageAggregator.aggregate(
                events = rawEvents,
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                range = range,
                metadataResolver = { pkg -> resolveAppMetadata(pkg) },
                selfPackageName = context.packageName
            )

            // Fallback to queryUsageStats if OEM cleared UsageEvents buffer while keeping daily aggregates
            if (snapshot.apps.isEmpty()) {
                snapshot = buildFallbackFromUsageStats(usageStatsManager, windowStartMs, windowEndMs, range, snapshot)
            }

            val chunkedPayloads = snapshot.toChunkedJsonList()
            for (payload in chunkedPayloads) {
                dataSender(payload.toString())
            }
            Log.d(
                TAG,
                "Dispatched WELLBEING_SNAPSHOT range=${range.wireValue}, apps=${snapshot.apps.size}, chunks=${chunkedPayloads.size}, screenTimeMs=${snapshot.totalScreenTimeMs}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting Digital Wellbeing telemetry: ${e.message}", e)
            dataSender("${WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX}${e.message ?: "Failed to collect usage stats"}")
        }
    }

    private fun buildFallbackFromUsageStats(
        usageStatsManager: UsageStatsManager,
        windowStartMs: Long,
        windowEndMs: Long,
        range: WellbeingRange,
        baseSnapshot: WellbeingSnapshot
    ): WellbeingSnapshot {
        val interval = if (range == WellbeingRange.TODAY) {
            UsageStatsManager.INTERVAL_DAILY
        } else {
            UsageStatsManager.INTERVAL_WEEKLY
        }
        val stats = usageStatsManager.queryUsageStats(interval, windowStartMs, windowEndMs).orEmpty()
        if (stats.isEmpty()) return baseSnapshot

        val mergedByPkg = mutableMapOf<String, Pair<Long, Long>>()
        for (item in stats) {
            val pkg = item.packageName ?: continue
            if (pkg == context.packageName || pkg == "com.android.systemui") continue
            val timeMs = item.totalTimeInForeground
            if (timeMs <= 0L) continue
            val prev = mergedByPkg[pkg]
            val sumTime = (prev?.first ?: 0L) + timeMs
            val maxLast = maxOf(prev?.second ?: 0L, item.lastTimeUsed)
            mergedByPkg[pkg] = sumTime to maxLast
        }

        val totalMs = mergedByPkg.values.sumOf { it.first }
        if (totalMs <= 0L) return baseSnapshot

        val apps = mergedByPkg.entries.map { (pkg, pair) ->
            val meta = resolveAppMetadata(pkg)
            val pct = ((pair.first * 100.0) / totalMs).toInt().coerceIn(0, 100)
            WellbeingAppUsage(
                packageName = pkg,
                appName = meta.appName,
                category = meta.category,
                totalTimeMs = pair.first,
                launchCount = 0,
                lastUsedTimestamp = pair.second,
                percent = pct
            )
        }.sortedByDescending { it.totalTimeMs }

        return baseSnapshot.copy(
            totalScreenTimeMs = totalMs,
            apps = apps
        )
    }

    private fun resolveAppMetadata(packageName: String): AppMetadata {
        metadataCache[packageName]?.let { return it }

        val resolved = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
            val category = mapAndroidCategory(appInfo.category)
            AppMetadata(appName = label, category = category)
        } catch (_: PackageManager.NameNotFoundException) {
            AppMetadata(
                appName = formatFallbackPackageName(packageName),
                category = "other"
            )
        } catch (_: Exception) {
            AppMetadata(
                appName = formatFallbackPackageName(packageName),
                category = "other"
            )
        }

        metadataCache[packageName] = resolved
        return resolved
    }

    private fun mapAndroidCategory(category: Int): String {
        return when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "social"
            ApplicationInfo.CATEGORY_VIDEO -> "video"
            ApplicationInfo.CATEGORY_AUDIO -> "audio"
            ApplicationInfo.CATEGORY_GAME -> "game"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
            ApplicationInfo.CATEGORY_NEWS -> "news"
            ApplicationInfo.CATEGORY_MAPS -> "maps"
            else -> "other"
        }
    }

    private fun formatFallbackPackageName(packageName: String): String {
        val lastSegment = packageName.substringAfterLast('.').trim()
        if (lastSegment.isBlank()) return packageName
        return lastSegment.replaceFirstChar { it.uppercase() }
    }
}
