package com.nexus.child.components.wellbeing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin Digital Wellbeing usage aggregator that reconstructs foreground sessions,
 * unlock counts, app launches, and hourly/daily histograms from Android UsageEvents.
 *
 * Hey friend! Why reconstruct sessions from `UsageEvents` instead of reading raw `UsageStats`?
 * Because `UsageStatsManager.queryUsageStats(INTERVAL_DAILY)` uses system/UTC bucket rollovers
 * that often include yesterday's screen time inside today's totals! Pairing `ACTIVITY_RESUMED`
 * and `ACTIVITY_PAUSED` / `SCREEN_NON_INTERACTIVE` gives exact local-midnight screen time.
 */
object WellbeingUsageAggregator {

    // Android UsageEvents.Event numeric constants (kept pure so JVM tests don't need Android stubs)
    const val EVENT_ACTIVITY_RESUMED = 1
    const val EVENT_ACTIVITY_PAUSED = 2
    const val EVENT_SCREEN_INTERACTIVE = 15
    const val EVENT_SCREEN_NON_INTERACTIVE = 16
    const val EVENT_KEYGUARD_HIDDEN = 18
    const val EVENT_ACTIVITY_STOPPED = 23
    const val EVENT_DEVICE_SHUTDOWN = 26

    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui"
    )

    /**
     * Computes local midnight (`00:00:00.000`) and window bounds for the requested range.
     */
    fun computeWindowBounds(
        range: WellbeingRange,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (range == WellbeingRange.SEVEN_DAYS) {
                add(Calendar.DAY_OF_YEAR, -6)
            }
        }
        return cal.timeInMillis to nowMs
    }

    /**
     * Aggregates raw usage events into a complete [WellbeingSnapshot].
     */
    fun aggregate(
        events: List<RawUsageEvent>,
        windowStartMs: Long,
        windowEndMs: Long,
        range: WellbeingRange,
        metadataResolver: (String) -> AppMetadata,
        timeZone: TimeZone = TimeZone.getDefault(),
        selfPackageName: String? = null
    ): WellbeingSnapshot {
        val safeEnd = max(windowStartMs, windowEndMs)
        val sortedEvents = events.sortedBy { it.timestampMs }

        val bucketStarts = buildBucketStarts(windowStartMs, safeEnd, range, timeZone)
        val bucketDurations = LongArray(bucketStarts.size)

        val appDurations = mutableMapOf<String, Long>()
        val appLaunches = mutableMapOf<String, Int>()
        val appLastUsed = mutableMapOf<String, Long>()

        val activeStarts = mutableMapOf<String, Long>()
        var activePackage: String? = null
        var unlockCount = 0

        fun closeSession(pkg: String, closeTimestamp: Long) {
            val start = activeStarts.remove(pkg) ?: return
            if (shouldIgnorePackage(pkg, selfPackageName)) return

            val clampedStart = max(start, windowStartMs)
            val clampedEnd = min(closeTimestamp, safeEnd)
            val duration = clampedEnd - clampedStart
            if (duration > 0L) {
                appDurations[pkg] = (appDurations[pkg] ?: 0L) + duration
                appLastUsed[pkg] = max(appLastUsed[pkg] ?: 0L, clampedEnd)
                distributeAcrossBuckets(clampedStart, clampedEnd, bucketStarts, bucketDurations, safeEnd)
            }
        }

        for (event in sortedEvents) {
            val ts = event.timestampMs
            val pkg = event.packageName

            when (event.eventType) {
                EVENT_KEYGUARD_HIDDEN -> {
                    if (ts in windowStartMs..safeEnd) {
                        unlockCount += 1
                    }
                }

                EVENT_ACTIVITY_RESUMED -> {
                    if (shouldIgnorePackage(pkg, selfPackageName)) {
                        // Moving to ignored package still suspends the previous foreground app
                        activePackage?.let { prev ->
                            if (prev != pkg) {
                                closeSession(prev, ts)
                            }
                        }
                        activePackage = null
                        continue
                    }

                    // Close previous foreground package if switching directly to a new app
                    val prev = activePackage
                    if (prev != null && prev != pkg) {
                        closeSession(prev, ts)
                    }

                    if (!activeStarts.containsKey(pkg)) {
                        activeStarts[pkg] = ts
                        if (ts in windowStartMs..safeEnd) {
                            appLaunches[pkg] = (appLaunches[pkg] ?: 0) + 1
                            appLastUsed[pkg] = max(appLastUsed[pkg] ?: 0L, ts)
                        }
                    }
                    activePackage = pkg
                }

                EVENT_ACTIVITY_PAUSED,
                EVENT_ACTIVITY_STOPPED -> {
                    closeSession(pkg, ts)
                    if (activePackage == pkg) {
                        activePackage = null
                    }
                }

                EVENT_SCREEN_NON_INTERACTIVE,
                EVENT_DEVICE_SHUTDOWN -> {
                    // Screen turned off or device shut down: close every open foreground session
                    val openPackages = activeStarts.keys.toList()
                    for (openPkg in openPackages) {
                        closeSession(openPkg, ts)
                    }
                    activePackage = null
                }
            }
        }

        // Capture any currently open foreground app session up to windowEndMs
        val ongoingPkg = activePackage
        if (ongoingPkg != null && activeStarts.containsKey(ongoingPkg)) {
            closeSession(ongoingPkg, safeEnd)
        }

        val totalScreenTimeMs = appDurations.values.sum()
        val totalLaunches = appLaunches.values.sum()

        // Include launched apps even if session lasted <1s so launch counts stay consistent
        val allPackages = (appDurations.keys + appLaunches.keys).distinct()
        val appUsages = allPackages.map { pkg ->
            val timeMs = appDurations[pkg] ?: 0L
            val launches = appLaunches[pkg] ?: 0
            val lastUsed = appLastUsed[pkg] ?: 0L
            val meta = metadataResolver(pkg)
            val percent = if (totalScreenTimeMs > 0L) {
                ((timeMs * 100.0) / totalScreenTimeMs).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            WellbeingAppUsage(
                packageName = pkg,
                appName = meta.appName,
                category = meta.category,
                totalTimeMs = timeMs,
                launchCount = launches,
                lastUsedTimestamp = lastUsed,
                percent = percent
            )
        }.filter { it.totalTimeMs > 0L || it.launchCount > 0 }
            .sortedWith(compareByDescending<WellbeingAppUsage> { it.totalTimeMs }.thenByDescending { it.launchCount })

        val activeMeta = ongoingPkg?.let(metadataResolver)
        val buckets = bucketStarts.mapIndexed { idx, bucketStart ->
            WellbeingTimeBucket(
                label = formatBucketLabel(bucketStart, idx, range, timeZone),
                durationMs = bucketDurations[idx]
            )
        }

        return WellbeingSnapshot(
            range = range,
            totalScreenTimeMs = totalScreenTimeMs,
            unlockCount = unlockCount,
            totalLaunches = totalLaunches,
            activeAppName = activeMeta?.appName,
            activePackageName = ongoingPkg,
            generatedAt = safeEnd,
            buckets = buckets,
            apps = appUsages
        )
    }

    private fun shouldIgnorePackage(pkg: String, selfPackageName: String?): Boolean {
        if (pkg.isBlank()) return true
        if (IGNORED_PACKAGES.contains(pkg)) return true
        if (selfPackageName != null && pkg == selfPackageName) return true
        return false
    }

    private fun buildBucketStarts(
        windowStartMs: Long,
        windowEndMs: Long,
        range: WellbeingRange,
        timeZone: TimeZone
    ): List<Long> {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = windowStartMs
        }
        val count = if (range == WellbeingRange.TODAY) 24 else 7
        val stepField = if (range == WellbeingRange.TODAY) Calendar.HOUR_OF_DAY else Calendar.DAY_OF_YEAR
        val starts = ArrayList<Long>(count)
        for (i in 0 until count) {
            starts.add(cal.timeInMillis)
            cal.add(stepField, 1)
        }
        // Keep compiler satisfied if windowEndMs is checked later
        if (windowEndMs < windowStartMs) return emptyList()
        return starts
    }

    private fun distributeAcrossBuckets(
        sessionStart: Long,
        sessionEnd: Long,
        bucketStarts: List<Long>,
        bucketDurations: LongArray,
        windowEndMs: Long
    ) {
        if (bucketStarts.isEmpty() || sessionEnd <= sessionStart) return

        for (i in bucketStarts.indices) {
            val bStart = bucketStarts[i]
            val bEnd = if (i + 1 < bucketStarts.size) bucketStarts[i + 1] else max(bStart, windowEndMs)
            val overlapStart = max(sessionStart, bStart)
            val overlapEnd = min(sessionEnd, bEnd)
            if (overlapEnd > overlapStart) {
                bucketDurations[i] += (overlapEnd - overlapStart)
            }
        }
    }

    private fun formatBucketLabel(
        bucketStartMs: Long,
        index: Int,
        range: WellbeingRange,
        timeZone: TimeZone
    ): String {
        return if (range == WellbeingRange.TODAY) {
            index.toString().padStart(2, '0')
        } else {
            val sdf = SimpleDateFormat("EEE", Locale.US).apply {
                this.timeZone = timeZone
            }
            sdf.format(bucketStartMs)
        }
    }
}
