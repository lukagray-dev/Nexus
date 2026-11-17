package com.nexus.child.components.mic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Parent-to-Child Voice Intercom command handling and state transitions.
 *
 * Hey friend! Here is what these tests verify:
 * 1. Command PARENT_AUDIO_UNMUTE triggers volume boost and replies PARENT_AUDIO_UNMUTED.
 * 2. If volume boost fails, reports error with PARENT_AUDIO_ERROR prefix.
 * 3. Command PARENT_AUDIO_MUTE restores normal volume and replies PARENT_AUDIO_MUTED.
 * 4. Aliases PARENT_MIC_ON and PARENT_MIC_OFF behave identically to standard commands.
 * 5. Cleanup safely releases speaker manager resources and restores states.
 */
class ParentAudioTest {

    private class MockMicController(
        var active: Boolean = false
    ) : IMicrophoneController {
        var stopCalled = false
        var startCalled = false
        override fun hasRecordAudioPermission(): Boolean = true
        override fun isActive(): Boolean = active
        override fun startMicrophone(): Boolean {
            startCalled = true
            active = true
            return true
        }
        override fun stopMicrophone(): Boolean {
            stopCalled = true
            active = false
            return true
        }
        override fun cleanup() {
            active = false
        }
    }

    private class TestableParentAudioSpeakerManager(
        var enableResult: Boolean = true,
        var restoreResult: Boolean = true,
        var active: Boolean = false
    ) : IParentAudioSpeakerManager {
        var enableCalled = false
        var restoreCalled = false
        var cleanupCalled = false

        override fun onRemoteAudioTrackAdded(track: org.webrtc.AudioTrack) {}

        override fun enableMaxVolumePlayback(): Boolean {
            enableCalled = true
            if (enableResult) active = true
            return enableResult
        }

        override fun restoreNormalVolume(): Boolean {
            restoreCalled = true
            if (restoreResult) active = false
            return restoreResult
        }

        override fun cleanup() {
            cleanupCalled = true
            active = false
        }

        override fun isMaxVolumeActive(): Boolean = active
    }

    @Test
    fun testParentAudioUnmuteSuccessTransmitsUnmuted() {
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager(enableResult = true)
        val sentMessages = mutableListOf<String>()

        val handler = MicCommandHandler(
            micController = MockMicController(),
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_PARENT_AUDIO_UNMUTE)
        assertTrue("PARENT_AUDIO_UNMUTE must be recognized and handled", handled)
        assertTrue("enableMaxVolumePlayback must be called", fakeSpeakerMgr.enableCalled)
        assertTrue("Speaker manager must report active max volume", fakeSpeakerMgr.isMaxVolumeActive())
        assertEquals(1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_PARENT_AUDIO_UNMUTED, sentMessages[0])
    }

    @Test
    fun testParentAudioUnmuteFailureTransmitsError() {
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager(enableResult = false)
        val sentMessages = mutableListOf<String>()

        val handler = MicCommandHandler(
            micController = MockMicController(),
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_PARENT_AUDIO_UNMUTE)
        assertTrue(handled)
        assertTrue(fakeSpeakerMgr.enableCalled)
        assertFalse(fakeSpeakerMgr.isMaxVolumeActive())
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must send PARENT_AUDIO_ERROR prefix",
            sentMessages[0].startsWith(MicCommandHandler.RESP_PARENT_AUDIO_ERROR_PREFIX)
        )
    }

    @Test
    fun testParentAudioMuteSuccessTransmitsMuted() {
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager(active = true, restoreResult = true)
        val sentMessages = mutableListOf<String>()

        val handler = MicCommandHandler(
            micController = MockMicController(),
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(MicCommandHandler.CMD_PARENT_AUDIO_MUTE)
        assertTrue("PARENT_AUDIO_MUTE must be recognized and handled", handled)
        assertTrue("restoreNormalVolume must be called", fakeSpeakerMgr.restoreCalled)
        assertFalse("Speaker manager must report not active after mute", fakeSpeakerMgr.isMaxVolumeActive())
        assertEquals(1, sentMessages.size)
        assertEquals(MicCommandHandler.RESP_PARENT_AUDIO_MUTED, sentMessages[0])
    }

    @Test
    fun testParentAudioAliasesHandledCorrectly() {
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager()
        val sentMessages = mutableListOf<String>()

        val handler = MicCommandHandler(
            micController = MockMicController(),
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // Alias for UNMUTE: PARENT_MIC_ON
        val unmuteHandled = handler.handleCommand(MicCommandHandler.CMD_PARENT_MIC_ON)
        assertTrue(unmuteHandled)
        assertEquals(MicCommandHandler.RESP_PARENT_AUDIO_UNMUTED, sentMessages.last())

        // Alias for MUTE: PARENT_MIC_OFF
        val muteHandled = handler.handleCommand(MicCommandHandler.CMD_PARENT_MIC_OFF)
        assertTrue(muteHandled)
        assertEquals(MicCommandHandler.RESP_PARENT_AUDIO_MUTED, sentMessages.last())
    }

    @Test
    fun testCleanupInvokesSpeakerManagerCleanup() {
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager(active = true)

        val handler = MicCommandHandler(
            micController = MockMicController(),
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { true }
        )

        handler.cleanup()
        assertTrue("cleanup on speaker manager must be called", fakeSpeakerMgr.cleanupCalled)
        assertFalse("active state must be false after cleanup", fakeSpeakerMgr.isMaxVolumeActive())
    }

    @Test
    fun testOnPeerDisconnectedShutsDownMicAndLoudspeaker() {
        val mockMic = MockMicController(active = true)
        val fakeSpeakerMgr = TestableParentAudioSpeakerManager(active = true)

        val handler = MicCommandHandler(
            micController = mockMic,
            parentAudioSpeakerManager = fakeSpeakerMgr,
            renegotiationTrigger = {},
            dataSender = { true }
        )

        assertTrue(mockMic.isActive())
        assertTrue(fakeSpeakerMgr.isMaxVolumeActive())

        // Simulate disconnection
        handler.onPeerDisconnected()

        assertTrue("stopMicrophone must be called on disconnect", mockMic.stopCalled)
        assertFalse("Mic must not be active after disconnect", mockMic.isActive())
        assertTrue("restoreNormalVolume must be called on disconnect", fakeSpeakerMgr.restoreCalled)
        assertFalse("Loudspeaker boost must not be active after disconnect", fakeSpeakerMgr.isMaxVolumeActive())
    }
}
