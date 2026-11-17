package com.nexus.child.components.mic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the microphone command-and-control protocol and state machine.
 *
 * Hey friend! These tests verify:
 * 1. Permission security enforcement: MIC_ON fails gracefully if RECORD_AUDIO is absent.
 * 2. Successful MIC_ON flow: triggers hardware start, renegotiation, and confirms MIC_STARTED
 *    only after RENEGOTIATION_COMPLETE arrives.
 * 3. Clean MIC_OFF flow: triggers hardware shutdown, renegotiation, and confirms MIC_STOPPED.
 * 4. Idempotency: duplicate commands return fast acknowledgments without thrashing hardware.
 */
class MicComponentTest {

    private class FakeMicrophoneController(
        var hasPermission: Boolean = true,
        var activeState: Boolean = false,
        var startResult: Boolean = true,
        var stopResult: Boolean = true
    ) : IMicrophoneController {
        var startCalled = false
        var stopCalled = false
        var cleanupCalled = false

        override fun hasRecordAudioPermission(): Boolean = hasPermission
        override fun isActive(): Boolean = activeState

        override fun startMicrophone(): Boolean {
            startCalled = true
            if (startResult) activeState = true
            return startResult
        }

        override fun stopMicrophone(): Boolean {
            stopCalled = true
            if (stopResult) activeState = false
            return stopResult
        }

        override fun cleanup() {
            cleanupCalled = true
            activeState = false
        }
    }

    private class FakeParentAudioSpeakerManager : IParentAudioSpeakerManager {
        var enableCalled = false
        var restoreCalled = false
        var cleanupCalled = false
        var isMaxVolume = false

        override fun onRemoteAudioTrackAdded(track: org.webrtc.AudioTrack) {}
        override fun enableMaxVolumePlayback(): Boolean {
            enableCalled = true
            isMaxVolume = true
            return true
        }
        override fun restoreNormalVolume(): Boolean {
            restoreCalled = true
            isMaxVolume = false
            return true
        }
        override fun cleanup() {
            cleanupCalled = true
            isMaxVolume = false
        }
        override fun isMaxVolumeActive(): Boolean = isMaxVolume
    }

    @Test
    fun testMicOnWithoutPermissionSendsError() {
        val fakeController = FakeMicrophoneController(hasPermission = false)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()

        val sentMessages = mutableListOf<String>()
        var renegotiationTriggered = false

        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = { renegotiationTriggered = true },
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_MIC_ON)
        assertTrue("MIC_ON must be handled", handled)
        assertFalse("Renegotiation must not trigger without permission", renegotiationTriggered)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report permission error",
            sentMessages[0].startsWith(MicCommandHandler.RESP_MIC_ERROR_PREFIX)
        )
        assertFalse("startMicrophone must not be called", fakeController.startCalled)
    }

    @Test
    fun testMicOnWithPermissionTriggersRenegotiationAndConfirmsStarted() {
        val fakeController = FakeMicrophoneController(hasPermission = true, activeState = false)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()

        val sentMessages = mutableListOf<String>()
        var triggerCount = 0

        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = { triggerCount++ },
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // 1. Send MIC_ON
        val handled = handler.handleCommand(MicCommandHandler.CMD_MIC_ON)
        assertTrue("MIC_ON must be handled", handled)
        assertTrue("startMicrophone must be called", fakeController.startCalled)
        assertEquals("Renegotiation must be triggered", 1, triggerCount)
        assertEquals("No confirmation should be sent before renegotiation completes", 0, sentMessages.size)

        // 2. Parent sends RENEGOTIATION_COMPLETE
        val completeHandled = handler.handleCommand(MicCommandHandler.CMD_RENEGOTIATION_COMPLETE)
        assertTrue("RENEGOTIATION_COMPLETE must be handled", completeHandled)
        assertEquals("MIC_STARTED must be transmitted after renegotiation completes", 1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_MIC_STARTED, sentMessages[0])
    }

    @Test
    fun testMicOffTriggersRenegotiationAndConfirmsStopped() {
        val fakeController = FakeMicrophoneController(activeState = true)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()

        val sentMessages = mutableListOf<String>()
        var triggerCount = 0

        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = { triggerCount++ },
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // 1. Send MIC_OFF
        val handled = handler.handleCommand(MicCommandHandler.CMD_MIC_OFF)
        assertTrue("MIC_OFF must be handled", handled)
        assertTrue("stopMicrophone must be called", fakeController.stopCalled)
        assertEquals("Renegotiation must be triggered", 1, triggerCount)
        assertEquals("No confirmation should be sent before renegotiation completes", 0, sentMessages.size)

        // 2. Parent sends RENEGOTIATION_COMPLETE
        val completeHandled = handler.handleCommand(MicCommandHandler.CMD_RENEGOTIATION_COMPLETE)
        assertTrue("RENEGOTIATION_COMPLETE must be handled", completeHandled)
        assertEquals("MIC_STOPPED must be transmitted after renegotiation completes", 1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_MIC_STOPPED, sentMessages[0])
    }

    @Test
    fun testMicOffWhenAlreadyIdleAcknowledgesImmediately() {
        val fakeController = FakeMicrophoneController(activeState = false)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()

        val sentMessages = mutableListOf<String>()
        var triggerCount = 0

        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = { triggerCount++ },
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_MIC_OFF)
        assertTrue(handled)
        assertEquals(0, triggerCount)
        assertEquals(1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_MIC_STOPPED, sentMessages[0])
    }

    @Test
    fun testMicOnWhenAlreadyActiveAcknowledgesImmediately() {
        val fakeController = FakeMicrophoneController(hasPermission = true, activeState = true)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()

        val sentMessages = mutableListOf<String>()
        var triggerCount = 0

        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = { triggerCount++ },
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_MIC_ON)
        assertTrue(handled)
        assertEquals(0, triggerCount)
        assertEquals(1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_MIC_STARTED, sentMessages[0])
    }

    @Test
    fun testUnknownCommandReturnsFalse() {
        val fakeController = FakeMicrophoneController()
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()
        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { true }
        )

        val handled = handler.handleCommand("CAMERA_ON")
        assertFalse("Unrelated command must return false", handled)
    }

    @Test
    fun testCleanupTearsDownController() {
        val fakeController = FakeMicrophoneController(activeState = true)
        val fakeSpeakerMgr = FakeParentAudioSpeakerManager()
        val handler = MicCommandHandler(
            micController = fakeController,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { true }
        )

        handler.cleanup()
        assertTrue("Controller cleanup must be called", fakeController.cleanupCalled)
        assertTrue("SpeakerManager cleanup must be called", fakeSpeakerMgr.cleanupCalled)
        assertFalse("Controller must not be active after cleanup", fakeController.isActive())
    }
}
