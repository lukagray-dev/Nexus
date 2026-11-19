package com.nexus.child.components.vibrateflash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Vibrate and Flash DataChannel protocol parsing,
 * execution state tracking, success/error confirmations, and fail-safe disconnect cleanup.
 *
 * Hey friend! These unit tests verify:
 * 1. String-based command handling (VIBRATE:<pattern>, FLASH:<action>, VIBRATE_FLASH:<pattern>).
 * 2. Success confirmations sent back across DataChannel (VIBRATE_SUCCESS, FLASH_SUCCESS).
 * 3. Graceful error reporting when hardware motor or flash unit is absent.
 * 4. Backward-compatible JSON command parsing.
 * 5. Peer disconnection fail-safe: ensures active vibrating/strobing stops immediately.
 */
class VibrateFlashComponentTest {

    private class FakeVibrateFlashController(
        var vibratorAvailable: Boolean = true,
        var flashAvailable: Boolean = true,
        var vibrateResult: Boolean = true,
        var flashResult: Boolean = true,
        var combinedResult: Boolean = true
    ) : IVibrateFlashController {

        var lastVibratePattern: String? = null
        var lastFlashAction: String? = null
        var lastCombinedPattern: String? = null
        var stopAllCalled = false
        var cleanupCalled = false

        override fun vibrate(pattern: String): Boolean {
            lastVibratePattern = pattern
            return vibrateResult
        }

        override fun flash(action: String): Boolean {
            lastFlashAction = action
            return flashResult
        }

        override fun vibrateAndFlash(pattern: String): Boolean {
            lastCombinedPattern = pattern
            return combinedResult
        }

        override fun stopAll() {
            stopAllCalled = true
        }

        override fun hasVibrator(): Boolean = vibratorAvailable

        override fun hasFlash(): Boolean = flashAvailable

        override fun cleanup() {
            cleanupCalled = true
            stopAllCalled = true
        }
    }

    @Test
    fun testVibrateCommandSucceeds() {
        val fakeController = FakeVibrateFlashController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val handled = handler.handleCommand("VIBRATE:double")
        assertTrue(handled)
        assertEquals("double", fakeController.lastVibratePattern)
        assertEquals(1, dispatchedMessages.size)
        assertEquals("VIBRATE_SUCCESS: double", dispatchedMessages[0])
    }

    @Test
    fun testVibrateFailsWhenMotorMissing() {
        val fakeController = FakeVibrateFlashController(vibratorAvailable = false)
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val handled = handler.handleCommand("VIBRATE:short")
        assertTrue(handled)
        assertEquals(1, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].startsWith("VIBRATE_ERROR: "))
    }

    @Test
    fun testFlashCommandSucceeds() {
        val fakeController = FakeVibrateFlashController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val handled = handler.handleCommand("FLASH:strobe")
        assertTrue(handled)
        assertEquals("strobe", fakeController.lastFlashAction)
        assertEquals(1, dispatchedMessages.size)
        assertEquals("FLASH_SUCCESS: strobe", dispatchedMessages[0])
    }

    @Test
    fun testFlashFailsWhenTorchMissing() {
        val fakeController = FakeVibrateFlashController(flashAvailable = false)
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val handled = handler.handleCommand("FLASH:on")
        assertTrue(handled)
        assertEquals(1, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].startsWith("FLASH_ERROR: "))
    }

    @Test
    fun testCombinedVibrateFlashSucceeds() {
        val fakeController = FakeVibrateFlashController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val handled = handler.handleCommand("VIBRATE_FLASH:sos")
        assertTrue(handled)
        assertEquals("sos", fakeController.lastCombinedPattern)
        assertEquals(1, dispatchedMessages.size)
        assertEquals("VIBRATE_FLASH_SUCCESS: sos", dispatchedMessages[0])
    }

    @Test
    fun testJsonCommandFallback() {
        val fakeController = FakeVibrateFlashController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = VibrateFlashCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val jsonCommand = "{\"type\":\"VIBRATE\", \"pattern\":\"pulse\"}"
        val handled = handler.handleCommand(jsonCommand)
        assertTrue(handled)
        assertEquals("pulse", fakeController.lastVibratePattern)
        assertEquals(1, dispatchedMessages.size)
        assertEquals("VIBRATE_SUCCESS: pulse", dispatchedMessages[0])
    }

    @Test
    fun testStopAllCommandHaltsHardware() {
        val fakeController = FakeVibrateFlashController()
        val handler = VibrateFlashCommandHandler(fakeController) { true }

        val handled = handler.handleCommand("STOP_VIBRATE_FLASH")
        assertTrue(handled)
        assertTrue(fakeController.stopAllCalled)
    }

    @Test
    fun testPeerDisconnectHaltsHardware() {
        val fakeController = FakeVibrateFlashController()
        val handler = VibrateFlashCommandHandler(fakeController) { true }

        handler.onPeerDisconnected()
        assertTrue(fakeController.stopAllCalled)
    }

    @Test
    fun testUnrecognizedCommandReturnsFalse() {
        val fakeController = FakeVibrateFlashController()
        val handler = VibrateFlashCommandHandler(fakeController) { true }

        val handled = handler.handleCommand("SOME_OTHER_CMD")
        assertFalse(handled)
    }
}
