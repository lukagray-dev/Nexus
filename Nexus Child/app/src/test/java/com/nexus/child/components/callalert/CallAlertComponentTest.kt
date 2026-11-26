package com.nexus.child.components.callalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Nexus Child live call-state alerting:
 * 1. Incoming call lifecycle (IDLE -> RINGING -> OFFHOOK -> IDLE)
 * 2. Outgoing call lifecycle (IDLE -> OFFHOOK -> IDLE)
 * 3. Duplicate OS callback deduplication & caller-number/contact enrichment
 * 4. Repeat reminder event generation & reset on call termination
 * 5. CallAlertCommandHandler protocol (CALL_ALERT_ON / CALL_ALERT_OFF / lifecycle hooks)
 */
class CallAlertComponentTest {

    private class FakeCallAlertController(
        var hasPermission: Boolean = true,
        var enabled: Boolean = true
    ) : ICallAlertController {
        var started = false
        var stopped = false
        var cleanedUp = false

        override fun hasPhoneStatePermission(): Boolean = hasPermission
        override fun setAlertEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
        override fun isAlertEnabled(): Boolean = enabled
        override fun startMonitoring(): Boolean {
            if (!hasPermission) return false
            started = true
            return true
        }
        override fun stopMonitoring(): Boolean {
            val wasStarted = started
            started = false
            stopped = true
            return wasStarted
        }
        override fun cleanup() {
            cleanedUp = true
            started = false
        }
    }

    @Test
    fun testIncomingCallTransitionsFromRingingToActiveToIdle() {
        val sm = CallAlertStateMachine()

        val ringing = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_RINGING,
            rawNumber = "+15551234567",
            resolvedContactName = "Mom",
            timestampMs = 1000L
        )
        assertNotNull(ringing)
        assertEquals(CallAlertStateMachine.STATE_RINGING, ringing!!.state)
        assertEquals(CallAlertStateMachine.DIRECTION_INCOMING, ringing.direction)
        assertEquals("+15551234567", ringing.number)
        assertEquals("Mom", ringing.contactName)
        assertFalse(ringing.isRepeat)
        assertTrue(sm.isCallActive())

        val answered = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_OFFHOOK,
            timestampMs = 2000L
        )
        assertNotNull(answered)
        assertEquals(CallAlertStateMachine.STATE_ACTIVE, answered!!.state)
        assertEquals(CallAlertStateMachine.DIRECTION_INCOMING, answered.direction)
        assertEquals("Mom", answered.contactName)
        assertTrue(sm.isCallActive())

        val ended = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_IDLE,
            timestampMs = 5000L
        )
        assertNotNull(ended)
        assertEquals(CallAlertStateMachine.STATE_IDLE, ended!!.state)
        assertEquals(CallAlertStateMachine.DIRECTION_INCOMING, ended.direction)
        assertFalse(sm.isCallActive())
        assertNull(sm.buildRepeatEvent())
    }

    @Test
    fun testOutgoingCallTransitionsDirectlyFromIdleToOffhook() {
        val sm = CallAlertStateMachine()

        val outgoing = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_OFFHOOK,
            rawNumber = "+15559876543",
            resolvedContactName = "School",
            timestampMs = 10000L
        )
        assertNotNull(outgoing)
        assertEquals(CallAlertStateMachine.STATE_ACTIVE, outgoing!!.state)
        assertEquals(CallAlertStateMachine.DIRECTION_OUTGOING, outgoing.direction)
        assertEquals("School", outgoing.contactName)
        assertTrue(sm.isCallActive())

        val repeat = sm.buildRepeatEvent(timestampMs = 40000L)
        assertNotNull(repeat)
        assertTrue(repeat!!.isRepeat)
        assertEquals(CallAlertStateMachine.STATE_ACTIVE, repeat.state)
        assertEquals(CallAlertStateMachine.DIRECTION_OUTGOING, repeat.direction)

        val ended = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_IDLE,
            timestampMs = 50000L
        )
        assertNotNull(ended)
        assertEquals(CallAlertStateMachine.STATE_IDLE, ended!!.state)
        assertEquals(CallAlertStateMachine.DIRECTION_OUTGOING, ended.direction)
        assertFalse(sm.isCallActive())
    }

    @Test
    fun testDuplicateOsStatesAreIgnoredUnlessEnrichedWithCallerDetails() {
        val sm = CallAlertStateMachine()

        val initial = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_RINGING,
            rawNumber = "",
            timestampMs = 100L
        )
        assertNotNull(initial)
        assertEquals("", initial!!.number)

        // Exact duplicate without new info is suppressed
        val duplicate = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_RINGING,
            rawNumber = "",
            timestampMs = 120L
        )
        assertNull(duplicate)

        // Second broadcast carrying EXTRA_INCOMING_NUMBER enriches and dispatches
        val enriched = sm.onOsCallStateChanged(
            osState = CallAlertStateMachine.CALL_STATE_RINGING,
            rawNumber = "+15550001111",
            resolvedContactName = "Dad",
            timestampMs = 150L
        )
        assertNotNull(enriched)
        assertEquals("+15550001111", enriched!!.number)
        assertEquals("Dad", enriched.contactName)
    }

    @Test
    fun testSerializeEventJsonMatchesParentContract() {
        val sm = CallAlertStateMachine()
        val event = CallAlertEvent(
            state = CallAlertStateMachine.STATE_ACTIVE,
            direction = CallAlertStateMachine.DIRECTION_OUTGOING,
            number = "+1 \"555\" 123",
            contactName = "Alex",
            timestamp = 1731938400000L,
            isRepeat = true
        )

        val json = sm.serializeEventJson(event)
        assertTrue(json.contains("\"type\":\"CALL_STATE\""))
        assertTrue(json.contains("\"state\":\"active\""))
        assertTrue(json.contains("\"direction\":\"outgoing\""))
        assertTrue(json.contains("\"number\":\"+1 \\\"555\\\" 123\""))
        assertTrue(json.contains("\"contactName\":\"Alex\""))
        assertTrue(json.contains("\"timestamp\":1731938400000"))
        assertTrue(json.contains("\"isRepeat\":true"))
    }

    @Test
    fun testCommandHandlerControlsEnabledStateAndLifecycle() {
        val controller = FakeCallAlertController()
        val handler = CallAlertCommandHandler(controller)

        handler.onDataChannelOpened()
        assertTrue(controller.started)

        assertTrue(handler.handleCommand(CallAlertCommandHandler.CMD_CALL_ALERT_OFF))
        assertFalse(controller.isAlertEnabled())

        assertTrue(handler.handleCommand(CallAlertCommandHandler.CMD_CALL_ALERT_ON))
        assertTrue(controller.isAlertEnabled())

        assertFalse(handler.handleCommand("UNKNOWN_COMMAND"))

        handler.onPeerDisconnected()
        assertFalse(controller.started)
        assertTrue(controller.stopped)

        handler.cleanup()
        assertTrue(controller.cleanedUp)
    }
}
