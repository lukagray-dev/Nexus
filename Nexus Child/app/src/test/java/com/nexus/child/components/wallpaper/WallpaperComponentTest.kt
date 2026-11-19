package com.nexus.child.components.wallpaper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Wallpaper DataChannel protocol, chunked image transport,
 * Home/Lock screen differentiation, live observer callbacks, and remote push application.
 *
 * Hey friend! These unit tests verify:
 * 1. Protocol commands: WALLPAPER_GET, WALLPAPER_ON, WALLPAPER_OFF, PUSH_WALLPAPER_START/CHUNK.
 * 2. Handshake confirmations: WALLPAPER_STARTED, WALLPAPER_STOPPED, WALLPAPER_SET_SUCCESS.
 * 3. 16KB Base64 chunking for large image transfers over SCTP DataChannels.
 * 4. Multi-screen support: distinguishes when Lock Screen has a unique image vs. shares Home Screen.
 * 5. Fail-safe cleanup upon peer disconnection.
 */
class WallpaperComponentTest {

    private class FakeWallpaperController(
        var hasPermission: Boolean = true,
        var isMonitoringActive: Boolean = false,
        var startResult: Boolean = true,
        var applyResult: Boolean = true,
        var mockBundle: WallpaperDataBundle = WallpaperDataBundle(
            homeBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            lockBase64 = null,
            isSame = true
        )
    ) : IWallpaperController {

        var startCalled = false
        var stopCalled = false
        var lastAppliedTarget: String? = null
        var lastAppliedBytes: ByteArray? = null
        var activeCallback: ((WallpaperDataBundle) -> Unit)? = null

        override fun hasWallpaperPermission(): Boolean = hasPermission

        override fun getActiveWallpapers(): WallpaperDataBundle = mockBundle

        override fun startWallpaperMonitoring(onWallpaperChanged: (WallpaperDataBundle) -> Unit): Boolean {
            startCalled = true
            activeCallback = onWallpaperChanged
            if (startResult) {
                isMonitoringActive = true
            }
            return startResult
        }

        override fun stopWallpaperMonitoring(): Boolean {
            stopCalled = true
            val was = isMonitoringActive
            isMonitoringActive = false
            activeCallback = null
            return was
        }

        override fun isMonitoring(): Boolean = isMonitoringActive

        override fun applyWallpaper(imageData: ByteArray, target: String): Boolean {
            lastAppliedBytes = imageData
            lastAppliedTarget = target
            return applyResult
        }

        override fun cleanup() {
            isMonitoringActive = false
            activeCallback = null
        }

        fun triggerLiveChange(bundle: WallpaperDataBundle) {
            activeCallback?.invoke(bundle)
        }
    }

    @Test
    fun testWallpaperGetDispatchesChunks() {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val fakeController = FakeWallpaperController()
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScope = testScope
        )

        val handled = handler.handleCommand("WALLPAPER_GET")
        assertTrue(handled)

        assertTrue(dispatched.isNotEmpty())
        val firstChunk = dispatched[0]
        assertTrue(firstChunk.contains("\"type\":\"WALLPAPER_CHUNK\""))
        assertTrue(firstChunk.contains("\"screen\":\"home\""))
        assertTrue(firstChunk.contains("\"isSame\":true"))
    }

    @Test
    fun testWallpaperOnStartsMonitoringAndDispatchesSnapshot() {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val fakeController = FakeWallpaperController()
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScope = testScope
        )

        val handled = handler.handleCommand("WALLPAPER_ON")
        assertTrue(handled)
        assertTrue(fakeController.startCalled)

        // First message should be confirmation WALLPAPER_STARTED
        assertEquals("WALLPAPER_STARTED", dispatched[0])
        // Subsequent messages are the initial wallpaper chunk(s)
        assertTrue(dispatched.size > 1)
        val chunk = dispatched[1]
        assertTrue(chunk.contains("\"type\":\"WALLPAPER_CHUNK\""))
    }

    @Test
    fun testWallpaperOnWithoutPermissionSendsError() {
        val fakeController = FakeWallpaperController(hasPermission = false)
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true }
        )

        val handled = handler.handleCommand("WALLPAPER_ON")
        assertTrue(handled)
        assertEquals(1, dispatched.size)
        assertTrue(dispatched[0].startsWith("WALLPAPER_ERROR: "))
    }

    @Test
    fun testWallpaperOffStopsMonitoring() {
        val fakeController = FakeWallpaperController(isMonitoringActive = true)
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true }
        )

        val handled = handler.handleCommand("WALLPAPER_OFF")
        assertTrue(handled)
        assertTrue(fakeController.stopCalled)
        assertEquals(1, dispatched.size)
        assertEquals("WALLPAPER_STOPPED", dispatched[0])
    }

    @Test
    fun testPushWallpaperChunkReassemblyAndSuccess() {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val fakeController = FakeWallpaperController()
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true },
            ioDispatcher = Dispatchers.Unconfined,
            coroutineScope = testScope
        )

        // 1. Send PUSH_WALLPAPER_START
        val startJson = "{\"type\":\"PUSH_WALLPAPER_START\",\"target\":\"both\",\"totalChunks\":2,\"fileSize\":100}"
        assertTrue(handler.handleCommand(startJson))

        // 2. Send Chunk 0
        val sampleBytesPart1 = "PartOne-".toByteArray()
        val b64Part1 = java.util.Base64.getEncoder().encodeToString(sampleBytesPart1)
        val chunk0Json = "{\"type\":\"PUSH_WALLPAPER_CHUNK\",\"chunkIndex\":0,\"totalChunks\":2,\"data\":\"$b64Part1\"}"
        assertTrue(handler.handleCommand(chunk0Json))

        // 3. Send Chunk 1 (final chunk)
        val sampleBytesPart2 = "PartTwo".toByteArray()
        val b64Part2 = java.util.Base64.getEncoder().encodeToString(sampleBytesPart2)
        val chunk1Json = "{\"type\":\"PUSH_WALLPAPER_CHUNK\",\"chunkIndex\":1,\"totalChunks\":2,\"data\":\"$b64Part2\"}"
        assertTrue(handler.handleCommand(chunk1Json))

        // Verify controller received reassembled payload
        val expectedFullString = "PartOne-PartTwo"
        assertEquals("both", fakeController.lastAppliedTarget)
        assertEquals(expectedFullString, String(fakeController.lastAppliedBytes ?: ByteArray(0)))

        // Verify success response sent
        assertEquals(1, dispatched.size)
        assertEquals("WALLPAPER_SET_SUCCESS", dispatched[0])
    }

    @Test
    fun testDistinctLockScreenDispatchesBothHomeAndLockChunks() {
        val distinctBundle = WallpaperDataBundle(
            homeBase64 = "HomeBase64Data",
            lockBase64 = "LockBase64Data",
            isSame = false
        )
        val fakeController = FakeWallpaperController(mockBundle = distinctBundle)
        val dispatched = mutableListOf<String>()
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { msg -> dispatched.add(msg); true }
        )

        handler.sendWallpaperBundle(distinctBundle)

        // Should have at least 2 chunks (1 for home, 1 for lock)
        assertEquals(2, dispatched.size)
        val homeChunk = dispatched[0]
        val lockChunk = dispatched[1]

        assertTrue(homeChunk.contains("\"screen\":\"home\""))
        assertTrue(homeChunk.contains("\"isSame\":false"))

        assertTrue(lockChunk.contains("\"screen\":\"lock\""))
        assertTrue(lockChunk.contains("\"isSame\":false"))
    }

    @Test
    fun testPeerDisconnectHaltsMonitoringAndFlushesBuffers() {
        val fakeController = FakeWallpaperController(isMonitoringActive = true)
        val handler = WallpaperCommandHandler(
            wallpaperController = fakeController,
            dataSender = { true }
        )

        handler.onPeerDisconnected()
        assertTrue(fakeController.stopCalled)
        assertFalse(fakeController.isMonitoring())
    }
}
