package com.nexus.child.components.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WellbeingComponentTest {

    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(utcZone).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `aggregator pairs sessions, clamps across midnight, closes on screen off, and counts unlocks`() {
        val midnight = utcMillis(2026, Calendar.NOVEMBER, 26, 0, 0)
        val now = utcMillis(2026, Calendar.NOVEMBER, 26, 14, 0)

        val events = listOf(
            // Opened before midnight (23:50) -> paused at 00:10 => only 10m counted for Today!
            RawUsageEvent("com.whatsapp", WellbeingUsageAggregator.EVENT_ACTIVITY_RESUMED, midnight - 10 * 60_000L),
            RawUsageEvent("com.whatsapp", WellbeingUsageAggregator.EVENT_ACTIVITY_PAUSED, midnight + 10 * 60_000L),

            // Screen unlock at 09:00 + YouTube from 09:00 to 09:45 (45m)
            RawUsageEvent("android", WellbeingUsageAggregator.EVENT_KEYGUARD_HIDDEN, utcMillis(2026, Calendar.NOVEMBER, 26, 9, 0)),
            RawUsageEvent("com.google.android.youtube", WellbeingUsageAggregator.EVENT_ACTIVITY_RESUMED, utcMillis(2026, Calendar.NOVEMBER, 26, 9, 0)),
            RawUsageEvent("com.google.android.youtube", WellbeingUsageAggregator.EVENT_SCREEN_NON_INTERACTIVE, utcMillis(2026, Calendar.NOVEMBER, 26, 9, 45)),

            // Ignored SystemUI & Self package
            RawUsageEvent("com.android.systemui", WellbeingUsageAggregator.EVENT_ACTIVITY_RESUMED, utcMillis(2026, Calendar.NOVEMBER, 26, 10, 0)),
            RawUsageEvent("com.android.systemui", WellbeingUsageAggregator.EVENT_ACTIVITY_PAUSED, utcMillis(2026, Calendar.NOVEMBER, 26, 10, 15)),
            RawUsageEvent("com.nexus.child", WellbeingUsageAggregator.EVENT_ACTIVITY_RESUMED, utcMillis(2026, Calendar.NOVEMBER, 26, 11, 0)),
            RawUsageEvent("com.nexus.child", WellbeingUsageAggregator.EVENT_ACTIVITY_PAUSED, utcMillis(2026, Calendar.NOVEMBER, 26, 11, 20)),

            // Second unlock at 13:30 + Chrome open from 13:30 through now (14:00) -> 30m split across 13 & 14 (capped at 14:00)
            RawUsageEvent("android", WellbeingUsageAggregator.EVENT_KEYGUARD_HIDDEN, utcMillis(2026, Calendar.NOVEMBER, 26, 13, 30)),
            RawUsageEvent("com.android.chrome", WellbeingUsageAggregator.EVENT_ACTIVITY_RESUMED, utcMillis(2026, Calendar.NOVEMBER, 26, 13, 30))
        )

        val snapshot = WellbeingUsageAggregator.aggregate(
            events = events,
            windowStartMs = midnight,
            windowEndMs = now,
            range = WellbeingRange.TODAY,
            metadataResolver = { pkg ->
                when (pkg) {
                    "com.whatsapp" -> AppMetadata("WhatsApp", "social")
                    "com.google.android.youtube" -> AppMetadata("YouTube", "video")
                    "com.android.chrome" -> AppMetadata("Chrome", "productivity")
                    else -> AppMetadata(pkg, "other")
                }
            },
            timeZone = utcZone,
            selfPackageName = "com.nexus.child"
        )

        // 10m (WhatsApp) + 45m (YouTube) + 30m (Chrome) = 85m
        assertEquals(85 * 60_000L, snapshot.totalScreenTimeMs)
        assertEquals(2, snapshot.unlockCount)
        assertEquals(2, snapshot.totalLaunches) // YouTube + Chrome (WhatsApp started before midnight)
        assertEquals("Chrome", snapshot.activeAppName)
        assertEquals("com.android.chrome", snapshot.activePackageName)
        assertEquals(24, snapshot.buckets.size)
        assertEquals(10 * 60_000L, snapshot.buckets[0].durationMs)
        assertEquals(45 * 60_000L, snapshot.buckets[9].durationMs)
        assertEquals(30 * 60_000L, snapshot.buckets[13].durationMs)

        assertEquals(3, snapshot.apps.size)
        assertEquals("YouTube", snapshot.apps[0].appName)
        assertEquals("Chrome", snapshot.apps[1].appName)
        assertEquals("WhatsApp", snapshot.apps[2].appName)
    }

    @Test
    fun `snapshot chunking splits app lists while preserving metadata on chunk 0`() {
        val apps = (1..30).map { idx ->
            WellbeingAppUsage(
                packageName = "com.example.app$idx",
                appName = "App $idx",
                category = "other",
                totalTimeMs = (31 - idx) * 60_000L,
                launchCount = idx,
                lastUsedTimestamp = 1_700_000_000_000L,
                percent = 3
            )
        }
        val snapshot = WellbeingSnapshot(
            range = WellbeingRange.SEVEN_DAYS,
            totalScreenTimeMs = 1_000_000L,
            unlockCount = 12,
            totalLaunches = 45,
            activeAppName = null,
            activePackageName = null,
            generatedAt = 1_700_000_000_000L,
            buckets = listOf(WellbeingTimeBucket("Mon", 500_000L)),
            apps = apps
        )

        val chunks = snapshot.toChunkedJsonList(chunkSize = 25)
        assertEquals(2, chunks.size)
        assertEquals(0, chunks[0].getInt("chunkIndex"))
        assertEquals(2, chunks[0].getInt("totalChunks"))
        assertEquals(25, chunks[0].getJSONArray("apps").length())
        assertEquals(12, chunks[0].getInt("unlockCount"))
        assertEquals(1, chunks[1].getInt("chunkIndex"))
        assertEquals(5, chunks[1].getJSONArray("apps").length())
        assertFalse(chunks[1].has("buckets"))
    }

    @Test
    fun `command handler validates permission, manages lifecycle, and resets on disconnect`() {
        val sentMessages = mutableListOf<String>()
        var usageAccessGranted = false
        var activeRange = WellbeingRange.TODAY
        var monitoring = false
        var refreshCount = 0

        val fakeController = object : IWellbeingController {
            override fun hasUsageAccess(): Boolean = usageAccessGranted
            override fun startMonitoring(range: WellbeingRange): Boolean {
                activeRange = range
                monitoring = true
                sentMessages.add(WellbeingConstants.RESP_WELLBEING_STARTED)
                return true
            }

            override fun stopMonitoring(): Boolean {
                monitoring = false
                sentMessages.add(WellbeingConstants.RESP_WELLBEING_STOPPED)
                return true
            }

            override fun refreshNow(range: WellbeingRange?): Boolean {
                if (range != null) activeRange = range
                refreshCount += 1
                return true
            }

            override fun setRange(range: WellbeingRange) {
                activeRange = range
            }

            override fun getCurrentRange(): WellbeingRange = activeRange
            override fun isMonitoringActive(): Boolean = monitoring
            override fun cleanup() {
                monitoring = false
            }
        }

        val handler = WellbeingCommandHandler(fakeController) { msg ->
            sentMessages.add(msg)
            true
        }

        // 1. Missing permission reports explicit WELLBEING_ERROR
        assertTrue(handler.handleCommand(WellbeingConstants.CMD_WELLBEING_ON))
        assertTrue(sentMessages.last().startsWith(WellbeingConstants.RESP_WELLBEING_ERROR_PREFIX))
        assertFalse(monitoring)

        // 2. Grant permission -> start with JSON range
        usageAccessGranted = true
        assertTrue(handler.handleCommand("""{"type":"WELLBEING_ON","range":"7d"}"""))
        assertTrue(monitoring)
        assertEquals(WellbeingRange.SEVEN_DAYS, activeRange)
        assertEquals(WellbeingConstants.RESP_WELLBEING_STARTED, sentMessages.last())

        // 3. Range switch + refresh
        assertTrue(handler.handleCommand("WELLBEING_RANGE:today"))
        assertEquals(WellbeingRange.TODAY, activeRange)
        assertEquals(1, refreshCount)

        assertTrue(handler.handleCommand(WellbeingConstants.CMD_WELLBEING_REFRESH))
        assertEquals(2, refreshCount)

        // 4. Peer disconnect stops active monitoring cleanly
        handler.onPeerDisconnected()
        assertFalse(monitoring)
        assertEquals(WellbeingConstants.RESP_WELLBEING_STOPPED, sentMessages.last())

        // Unrelated command returns false
        assertFalse(handler.handleCommand("UNKNOWN_COMMAND"))
        assertNull(null)
    }
}
