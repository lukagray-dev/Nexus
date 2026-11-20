package com.nexus.child.components.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Screen command-and-control protocol, accessibility validation,
 * and fail-safe state machine.
 *
 * Hey friend! These tests verify:
 * 1. Permission security enforcement: SCREEN_ON fails immediately if Accessibility permission is missing.
 * 2. Successful SCREEN_ON flow: starts Full HD screen capture and confirms with SCREEN_STARTED.
 * 3. Idempotency: repeated SCREEN_ON or SCREEN_OFF commands return fast acknowledgments without thrashing.
 * 4. Error propagation: if Android screen capture consent is rejected, SCREEN_ERROR is sent to parent.
 * 5. Clean SCREEN_OFF flow: halts video capture, mutes stream, and confirms SCREEN_STOPPED.
 * 6. Fail-Safe Disconnection: peer disconnect automatically stops active screen streaming to save battery.
 * 7. Command aliases: handles SCREEN_START / START_SCREEN and SCREEN_STOP / STOP_SCREEN smoothly.
 */
class ScreenComponentTest {

    /**
     * Fake implementation of IScreenController for fast JVM unit testing.
     */
    private class FakeScreenController(
        var hasAccessibility: Boolean = true,
        var streamingState: Boolean = false,
        var startResult: Boolean = true,
        var shouldSucceedAsync: Boolean = true,
        var stopResult: Boolean = true
    ) : IScreenController {

        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false

        override fun hasAccessibilityPermission(): Boolean = hasAccessibility
        override fun isStreaming(): Boolean = streamingState

        override fun startScreenStreaming(
            onStarted: () -> Unit,
            onError: (errorMessage: String) -> Unit
        ): Boolean {
            startCalled = true
            if (!startResult) return false

            if (shouldSucceedAsync) {
                streamingState = true
                onStarted()
            } else {
                onError("User denied screen capture")
            }
            return true
        }

        override fun stopScreenStreaming(): Boolean {
            stopCalled = true
            if (stopResult) {
                streamingState = false
            }
            return stopResult
        }

        override fun cleanup() {
            cleanupCalled = true
            streamingState = false
        }
    }

    @Test
    fun testScreenOnWithoutAccessibilityPermissionSendsError() {
        val fakeController = FakeScreenController(hasAccessibility = false)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_ON)
        assertTrue("SCREEN_ON command must be recognized", handled)
        assertFalse("startScreenStreaming must not be called without accessibility permission", fakeController.startCalled)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report accessibility permission error",
            sentMessages[0].startsWith(ScreenCommandHandler.RESP_SCREEN_ERROR_PREFIX)
        )
    }

    @Test
    fun testScreenOnWithPermissionStartsStreamingAndConfirmsStarted() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = false)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_ON)
        assertTrue("SCREEN_ON must be handled", handled)
        assertTrue("startScreenStreaming must be invoked", fakeController.startCalled)
        assertTrue("Streaming state must be active", fakeController.isStreaming())
        assertEquals(2, sentMessages.size)
        assertEquals(ScreenCommandHandler.RESP_SCREEN_STARTED, sentMessages[0])
        assertEquals(ScreenCommandHandler.RESP_SCREEN_RECORDING_STARTED, sentMessages[1])
    }

    @Test
    fun testScreenOnWhenAlreadyStreamingImmediatelyAcknowledgesStarted() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = true)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_ON)
        assertTrue("SCREEN_ON must be handled", handled)
        assertFalse("startScreenStreaming must not be called again when already streaming", fakeController.startCalled)
        assertEquals(2, sentMessages.size)
        assertEquals(ScreenCommandHandler.RESP_SCREEN_STARTED, sentMessages[0])
        assertEquals(ScreenCommandHandler.RESP_SCREEN_RECORDING_STARTED, sentMessages[1])
    }

    @Test
    fun testScreenOnWhenStartFailsSendsError() {
        val fakeController = FakeScreenController(
            hasAccessibility = true,
            streamingState = false,
            shouldSucceedAsync = false
        )
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_ON)
        assertTrue("SCREEN_ON must be handled", handled)
        assertTrue("startScreenStreaming was attempted", fakeController.startCalled)
        assertFalse("Streaming state should not be active", fakeController.isStreaming())
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must send error response",
            sentMessages[0].startsWith(ScreenCommandHandler.RESP_SCREEN_ERROR_PREFIX)
        )
    }

    @Test
    fun testScreenOffHaltsStreamingAndConfirmsStopped() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = true)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_OFF)
        assertTrue("SCREEN_OFF must be handled", handled)
        assertTrue("stopScreenStreaming must be called", fakeController.stopCalled)
        assertFalse("Streaming state must now be false", fakeController.isStreaming())
        assertEquals(2, sentMessages.size)
        assertEquals(ScreenCommandHandler.RESP_SCREEN_STOPPED, sentMessages[0])
        assertEquals(ScreenCommandHandler.RESP_SCREEN_RECORDING_STOPPED, sentMessages[1])
    }

    @Test
    fun testScreenOffWhenAlreadyIdleImmediatelyAcknowledgesStopped() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = false)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_OFF)
        assertTrue("SCREEN_OFF must be handled", handled)
        assertFalse("stopScreenStreaming must not be called when already idle", fakeController.stopCalled)
        assertEquals(2, sentMessages.size)
        assertEquals(ScreenCommandHandler.RESP_SCREEN_STOPPED, sentMessages[0])
        assertEquals(ScreenCommandHandler.RESP_SCREEN_RECORDING_STOPPED, sentMessages[1])
    }

    @Test
    fun testFailSafePeerDisconnectionStopsActiveStreaming() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = true)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        handler.onPeerDisconnected()
        assertTrue("stopScreenStreaming must be called on peer disconnect", fakeController.stopCalled)
        assertFalse("Streaming must be inactive after disconnect", fakeController.isStreaming())
    }

    @Test
    fun testCleanupDelegatesToController() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = true)
        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { true }
        )

        handler.cleanup()
        assertTrue("cleanup must be invoked on IScreenController", fakeController.cleanupCalled)
    }

    @Test
    fun testAliasesRecognition() {
        val fakeController = FakeScreenController(hasAccessibility = true, streamingState = false)
        val sentMessages = mutableListOf<String>()

        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        assertTrue(handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_START))
        assertTrue(handler.handleCommand(ScreenCommandHandler.CMD_START_SCREEN))
        assertTrue(handler.handleCommand(ScreenCommandHandler.CMD_SCREEN_STOP))
        assertTrue(handler.handleCommand(ScreenCommandHandler.CMD_STOP_SCREEN))
    }

    @Test
    fun testUnrecognizedCommandsIgnored() {
        val fakeController = FakeScreenController(hasAccessibility = true)
        val handler = ScreenCommandHandler(
            screenController = fakeController,
            dataSender = { true }
        )

        assertFalse("Random command must not be handled", handler.handleCommand("UNKNOWN_CMD"))
        assertFalse("CAMERA_ON must not be handled by ScreenCommandHandler", handler.handleCommand("CAMERA_ON"))
    }
}
