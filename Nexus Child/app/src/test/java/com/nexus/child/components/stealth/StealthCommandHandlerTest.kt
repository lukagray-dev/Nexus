package com.nexus.child.components.stealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying StealthCommandHandler protocol parsing, execution,
 * acknowledgement dispatches, and initial connection state synchronization.
 *
 * Hey friend! These unit tests verify:
 * 1. "STEALTH_ON" command execution and dual acknowledgement (ACK + STATUS:ON).
 * 2. "STEALTH_OFF" command execution and dual acknowledgement (ACK + STATUS:OFF).
 * 3. "GET_STEALTH_STATUS" query response.
 * 4. Automatic sendStatusUpdate() for initial WebRTC connection synchronization.
 * 5. Failure edge cases when component toggling fails.
 * 6. Ignored foreign commands.
 */
class StealthCommandHandlerTest {

    private class FakeStealthController(
        var isStealth: Boolean = false,
        var setSuccess: Boolean = true
    ) : IStealthController {

        var lastSetState: Boolean? = null
        var listener: ((isStealth: Boolean) -> Unit)? = null

        override fun isStealthMode(): Boolean = isStealth

        override fun setStealthMode(enable: Boolean): Boolean {
            lastSetState = enable
            if (setSuccess) {
                isStealth = enable
                listener?.invoke(enable)
                return true
            }
            return false
        }

        override fun toggleStealthMode(): Boolean {
            val newState = !isStealth
            setStealthMode(newState)
            return newState
        }

        override fun setOnStateChangeListener(listener: ((isStealth: Boolean) -> Unit)?) {
            this.listener = listener
        }
    }

    private lateinit var fakeController: FakeStealthController
    private val sentMessages = mutableListOf<String>()
    private lateinit var handler: StealthCommandHandler

    @Before
    fun setup() {
        fakeController = FakeStealthController(isStealth = false, setSuccess = true)
        sentMessages.clear()
        handler = StealthCommandHandler(
            stealthController = fakeController,
            dataSender = { msg ->
                sentMessages.add(msg)
                true
            }
        )
    }

    @Test
    fun handleCommand_stealthOn_shouldActivateStealthAndSendAck() {
        val handled = handler.handleCommand("STEALTH_ON")

        assertTrue(handled)
        assertTrue(fakeController.isStealth)
        assertEquals(true, fakeController.lastSetState)

        assertEquals(2, sentMessages.size)
        assertEquals(StealthCommandHandler.ACK_STEALTH_ON, sentMessages[0])
        assertEquals(StealthCommandHandler.STATUS_STEALTH_ON, sentMessages[1])
    }

    @Test
    fun handleCommand_stealthOff_shouldDeactivateStealthAndSendAck() {
        fakeController.isStealth = true

        val handled = handler.handleCommand("STEALTH_OFF")

        assertTrue(handled)
        assertFalse(fakeController.isStealth)
        assertEquals(false, fakeController.lastSetState)

        assertEquals(2, sentMessages.size)
        assertEquals(StealthCommandHandler.ACK_STEALTH_OFF, sentMessages[0])
        assertEquals(StealthCommandHandler.STATUS_STEALTH_OFF, sentMessages[1])
    }

    @Test
    fun handleCommand_getStealthStatus_whenVisible_shouldSendStatusOff() {
        fakeController.isStealth = false

        val handled = handler.handleCommand("GET_STEALTH_STATUS")

        assertTrue(handled)
        assertEquals(1, sentMessages.size)
        assertEquals(StealthCommandHandler.STATUS_STEALTH_OFF, sentMessages[0])
    }

    @Test
    fun handleCommand_getStealthStatus_whenHidden_shouldSendStatusOn() {
        fakeController.isStealth = true

        val handled = handler.handleCommand("GET_STEALTH_STATUS")

        assertTrue(handled)
        assertEquals(1, sentMessages.size)
        assertEquals(StealthCommandHandler.STATUS_STEALTH_ON, sentMessages[0])
    }

    @Test
    fun sendStatusUpdate_initialConnectionSync_shouldDispatchAccurateState() {
        // Initial state: visible
        handler.sendStatusUpdate()
        assertEquals(1, sentMessages.size)
        assertEquals(StealthCommandHandler.STATUS_STEALTH_OFF, sentMessages[0])

        // State changes to hidden
        fakeController.isStealth = true
        handler.sendStatusUpdate()
        assertEquals(2, sentMessages.size)
        assertEquals(StealthCommandHandler.STATUS_STEALTH_ON, sentMessages[1])
    }

    @Test
    fun handleCommand_stealthOn_whenControllerFails_shouldSendFallbackStatus() {
        fakeController.setSuccess = false

        val handled = handler.handleCommand("STEALTH_ON")

        assertTrue(handled)
        // Should not send ACK, but sends fallback status (still OFF)
        assertEquals(1, sentMessages.size)
        assertEquals(StealthCommandHandler.STATUS_STEALTH_OFF, sentMessages[0])
    }

    @Test
    fun handleCommand_unrelatedCommand_shouldReturnFalse() {
        val handled = handler.handleCommand("CAMERA_ON")

        assertFalse(handled)
        assertTrue(sentMessages.isEmpty())
    }
}
