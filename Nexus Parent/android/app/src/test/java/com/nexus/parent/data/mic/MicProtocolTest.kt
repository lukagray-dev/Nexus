package com.nexus.parent.data.mic

import com.nexus.parent.state.LoudspeakerVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MicProtocolTest.kt - Verifies command formatting and CALL_STATE JSON parsing.
 */
class MicProtocolTest {

    @Test
    fun `builds exact Parent audio unmute and volume commands matching Child and Desktop`() {
        assertEquals("PARENT_AUDIO_UNMUTE:max", MicProtocol.buildParentAudioUnmuteCommand(LoudspeakerVolume.MAX))
        assertEquals("PARENT_AUDIO_UNMUTE:high", MicProtocol.buildParentAudioUnmuteCommand(LoudspeakerVolume.HIGH))
        assertEquals("PARENT_AUDIO_UNMUTE:normal", MicProtocol.buildParentAudioUnmuteCommand(LoudspeakerVolume.NORMAL))

        assertEquals("PARENT_AUDIO_VOLUME:max", MicProtocol.buildParentAudioVolumeCommand(LoudspeakerVolume.MAX))
        assertEquals("PARENT_AUDIO_VOLUME:high", MicProtocol.buildParentAudioVolumeCommand(LoudspeakerVolume.HIGH))
        assertEquals("PARENT_AUDIO_VOLUME:normal", MicProtocol.buildParentAudioVolumeCommand(LoudspeakerVolume.NORMAL))
    }

    @Test
    fun `parses CALL_STATE packets into ActiveCallAlert and clears on idle`() {
        val ringingJson = """{"type":"CALL_STATE","state":"ringing","direction":"incoming","number":"+15551234","contactName":"Mom","isRepeat":false}"""
        val packet = MicProtocol.parseCallStatePacket(ringingJson)
        assertNotNull(packet)
        val alert = MicProtocol.toActiveCallAlert(packet!!)
        assertEquals("Incoming Call", alert?.title)
        assertEquals("Mom (+15551234)", alert?.subtitle)

        val idleJson = """{"type":"CALL_STATE","state":"idle"}"""
        val idlePacket = MicProtocol.parseCallStatePacket(idleJson)
        assertNotNull(idlePacket)
        assertNull(MicProtocol.toActiveCallAlert(idlePacket!!))
        assertNull(MicProtocol.parseCallStatePacket("MIC_STARTED"))
    }
}
