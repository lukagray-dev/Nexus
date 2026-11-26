package com.nexus.parent.data.monitor

import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.WallpaperTarget
import com.nexus.parent.state.WellbeingRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * MonitorCoordinatorTest.kt - Verifies spinner/confirmation state machines, 20s timeout fallback,
 * multi-chunk snapshot reassembly, chunked Base64 wallpaper upload, and disconnect cleanup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorCoordinatorTest {

    @Test
    fun `play pause lifecycle awaits confirmation and falls back cleanly on 20s timeout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordScope = TestScope(dispatcher)
        val sentCommands = mutableListOf<String>()
        var transportConnected = true

        val holder = ParentStateHolder()
        val coordinator = MonitorCoordinator(
            context = null,
            scope = coordScope,
            sendCommand = { cmd ->
                sentCommands += cmd
                transportConnected
            },
            ioDispatcher = dispatcher,
            base64Decoder = { Base64.getDecoder().decode(it) },
            base64Encoder = { Base64.getEncoder().encodeToString(it) }
        )
        coordinator.attachStateHolder(holder)

        // 1. Disconnected guard reverts STARTING -> IDLE with toast
        transportConnected = false
        coordinator.toggleFeatureMonitoring(FeatureId.SMS)
        assertEquals(MonitoringState.IDLE, holder.uiState.value.monitor.smsState)
        assertTrue(holder.uiState.value.toastMessage?.contains("not connected") == true)

        // 2. Normal flow: IDLE -> STARTING -> SMS_STARTED -> LIVE -> STOPPING -> SMS_STOPPED -> IDLE
        transportConnected = true
        coordinator.toggleFeatureMonitoring(FeatureId.SMS)
        assertEquals("SMS_ON", sentCommands.last())
        assertEquals(MonitoringState.STARTING, holder.uiState.value.monitor.smsState)
        assertFalse(FeatureId.SMS in holder.uiState.value.liveFeatures)

        coordinator.handleIncomingMessage("SMS_STARTED")
        assertEquals(MonitoringState.LIVE, holder.uiState.value.monitor.smsState)
        assertTrue(FeatureId.SMS in holder.uiState.value.liveFeatures)

        coordinator.toggleFeatureMonitoring(FeatureId.SMS)
        assertEquals("SMS_OFF", sentCommands.last())
        assertEquals(MonitoringState.STOPPING, holder.uiState.value.monitor.smsState)

        coordinator.handleIncomingMessage("SMS_STOPPED")
        assertEquals(MonitoringState.IDLE, holder.uiState.value.monitor.smsState)
        assertFalse(FeatureId.SMS in holder.uiState.value.liveFeatures)

        // 3. Timeout fallback when Child never confirms CALLLOG_ON
        coordinator.toggleFeatureMonitoring(FeatureId.CALLLOG)
        assertEquals(MonitoringState.STARTING, holder.uiState.value.monitor.callLogState)
        coordScope.advanceTimeBy(MonitorProtocol.MONITOR_CONFIRM_TIMEOUT_MS + 100L)
        coordScope.runCurrent()
        assertEquals(MonitoringState.IDLE, holder.uiState.value.monitor.callLogState)
        assertTrue(holder.uiState.value.toastMessage?.contains("timed out") == true)
    }

    @Test
    fun `reassembles multi-chunk Wellbeing and Wallpaper payloads and handles disconnect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordScope = TestScope(dispatcher)
        val holder = ParentStateHolder()
        val coordinator = MonitorCoordinator(
            context = null,
            scope = coordScope,
            sendCommand = { true },
            ioDispatcher = dispatcher,
            base64Decoder = { Base64.getDecoder().decode(it) },
            base64Encoder = { Base64.getEncoder().encodeToString(it) }
        )
        coordinator.attachStateHolder(holder)

        // 1. Multi-chunk Digital Wellbeing snapshot
        coordinator.handleIncomingMessage("WELLBEING_STARTED")
        coordinator.handleIncomingMessage(
            """{"type":"WELLBEING_SNAPSHOT","range":"7d","chunkIndex":0,"totalChunks":2,"totalScreenTimeMs":5400000,"unlockCount":25,"totalLaunches":60,"activeAppName":"Chrome","buckets":[{"label":"Mon","durationMs":3000000},{"label":"Tue","durationMs":1500000}],"apps":[{"packageName":"com.second","appName":"Small App","category":"other","totalTimeMs":600000,"launchCount":4,"percent":11}]}"""
        )
        // Not committed until final chunk arrives
        assertTrue(holder.uiState.value.monitor.wellbeing.apps.isEmpty())

        coordinator.handleIncomingMessage(
            """{"type":"WELLBEING_SNAPSHOT","range":"7d","chunkIndex":1,"totalChunks":2,"apps":[{"packageName":"com.first","appName":"Big App","category":"social","totalTimeMs":4800000,"launchCount":20,"percent":89}]}"""
        )
        val wb = holder.uiState.value.monitor.wellbeing
        assertEquals(WellbeingRange.SEVEN_DAYS, wb.range)
        assertEquals("1h 30m", wb.totalScreenTime)
        assertEquals(2, wb.apps.size)
        // Sorted descending by totalTimeMs
        assertEquals("Big App", wb.apps[0].appName)
        assertEquals(1f, wb.buckets[0].normalizedValue, 0.01f)
        assertEquals(0.5f, wb.buckets[1].normalizedValue, 0.01f)

        // 2. Multi-chunk Wallpaper fetch preview with isSame=true
        val fakeImageBytes = ByteArray(120) { (it * 3).toByte() }
        val encoded = Base64.getEncoder().encodeToString(fakeImageBytes)
        val mid = encoded.length / 2
        coordinator.handleIncomingMessage(
            """{"type":"WALLPAPER_CHUNK","screen":"home","chunkIndex":0,"totalChunks":2,"data":"${encoded.substring(0, mid)}","isSame":true}"""
        )
        coordinator.handleIncomingMessage(
            """{"type":"WALLPAPER_CHUNK","screen":"home","chunkIndex":1,"totalChunks":2,"data":"${encoded.substring(mid)}","isSame":true}"""
        )
        val wp = holder.uiState.value.monitor.wallpaper
        assertTrue(wp.hasPreviewLoaded)
        assertArrayEquals(fakeImageBytes, wp.homeImageBytes)
        assertArrayEquals(fakeImageBytes, wp.lockImageBytes)

        // 3. Peer disconnect resets live monitoring states cleanly
        coordinator.onPeerDisconnected()
        assertEquals(MonitoringState.IDLE, holder.uiState.value.monitor.wellbeing.monitoringState)
        assertFalse(FeatureId.WELLBEING in holder.uiState.value.liveFeatures)
    }

    @Test
    fun `pushWallpaperBytes emits 16KB Base64 chunks cleanly matching Child decoder constraints`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordScope = TestScope(dispatcher)
        val sent = mutableListOf<String>()
        val holder = ParentStateHolder()
        val coordinator = MonitorCoordinator(
            context = null,
            scope = coordScope,
            sendCommand = { cmd ->
                sent += cmd
                true
            },
            ioDispatcher = dispatcher,
            base64Decoder = { Base64.getDecoder().decode(it) },
            base64Encoder = { Base64.getEncoder().encodeToString(it) }
        )
        coordinator.attachStateHolder(holder)
        holder.setWallpaperTarget(WallpaperTarget.BOTH)

        // ~30 KB raw bytes => ~40 KB Base64 => 3 chunks (16384, 16384, remainder)
        val payloadBytes = ByteArray(30_000) { (it % 251).toByte() }
        coordinator.pushWallpaperBytes(payloadBytes)

        val startObj = JSONObject(sent.first())
        assertEquals(MonitorProtocol.CMD_PUSH_WALLPAPER_START, startObj.getString("type"))
        assertEquals("both", startObj.getString("target"))
        val totalChunks = startObj.getInt("totalChunks")
        assertEquals(3, totalChunks)

        val chunkPackets = sent.drop(1).map { JSONObject(it) }
        assertEquals(totalChunks, chunkPackets.size)
        // Non-final chunks must be exact multiples of 4 so Child's per-chunk decoder never fails padding
        assertEquals(16384, chunkPackets[0].getString("data").length)
        assertEquals(0, chunkPackets[0].getString("data").length % 4)
        assertEquals(0, chunkPackets[1].getString("data").length % 4)

        // Reconstruct decoded chunks the exact same way Child WallpaperCommandHandler does
        val reconstructed = chunkPackets.flatMap { pkt ->
            Base64.getDecoder().decode(pkt.getString("data")).toList()
        }.toByteArray()
        assertArrayEquals(payloadBytes, reconstructed)
        assertTrue(holder.uiState.value.monitor.wallpaper.isPushing)

        coordinator.handleIncomingMessage("WALLPAPER_SET_SUCCESS")
        assertFalse(holder.uiState.value.monitor.wallpaper.isPushing)
        assertEquals(100, holder.uiState.value.monitor.wallpaper.pushProgressPercent)
        assertNotNull(holder.uiState.value.toastMessage)
    }
}
