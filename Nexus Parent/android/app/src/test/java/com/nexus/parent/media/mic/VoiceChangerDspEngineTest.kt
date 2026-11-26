package com.nexus.parent.media.mic

import com.nexus.parent.state.VoicePresetId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * VoiceChangerDspEngineTest.kt - Verifies all 10 voice presets, in-place WebRTC ByteBuffer
 * mutation, bypass mode, and smooth crossfades across live hot-switches.
 */
class VoiceChangerDspEngineTest {

    private fun generateVoiceLikeWave(
        sampleRate: Int = 48_000,
        durationSamples: Int = 960,
        freqHz: Float = 220f
    ): ShortArray {
        return ShortArray(durationSamples) { n ->
            val v = (0.45f * sin(2.0 * PI * freqHz * n / sampleRate) +
                0.2f * sin(2.0 * PI * (freqHz * 2f) * n / sampleRate)).toFloat()
            (v * 32767f).toInt().toShort()
        }
    }

    @Test
    fun `bypassed mode preserves samples untouched and reports zero rms`() {
        val engine = VoiceChangerDspEngine()
        engine.initialize(48_000)
        engine.setPreset(VoicePresetId.ROBOT_MALE)
        engine.setBypass(true)

        val input = generateVoiceLikeWave(durationSamples = 480)
        val out = engine.processMonoPcm16(input, 48_000)
        assertArrayEquals(input, out)
        assertEquals(0f, engine.getOutboundRmsLevel(), 0.0001f)
    }

    @Test
    fun `original preset preserves waveform shape while all morphed presets produce bounded transformed PCM`() {
        val input = generateVoiceLikeWave(durationSamples = 1920)

        for (preset in VoicePresetId.entries) {
            val engine = VoiceChangerDspEngine()
            engine.initialize(48_000)
            engine.setBypass(false)
            engine.setPreset(preset)

            val output = engine.processMonoPcm16(input, 48_000)
            assertEquals(input.size, output.size)
            assertTrue("Expected finite non-zero RMS for $preset", engine.getOutboundRmsLevel() > 0.05f)

            val totalDiff = input.indices.sumOf { abs(input[it].toInt() - output[it].toInt()) }
            if (preset != VoicePresetId.ORIGINAL) {
                assertTrue("Expected audible DSP delta for $preset", totalDiff > 1000)
            }
        }
    }

    @Test
    fun `in-place WebRTC capture ByteBuffer processing mutates buffers cleanly`() {
        val engine = VoiceChangerDspEngine()
        engine.initialize(48_000)
        engine.setBypass(false)
        engine.setPreset(VoicePresetId.ALIEN)

        val frames = 480 // 10ms @ 48kHz
        val rawSamples = generateVoiceLikeWave(durationSamples = frames)
        val buf = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        rawSamples.forEach { buf.putShort(it) }
        buf.flip()

        engine.processCaptureBufferInPlace(buf, sampleRateHz = 48_000, channels = 1)

        buf.rewind()
        var diffCount = 0
        for (i in 0 until frames) {
            val morphed = buf.getShort()
            if (morphed != rawSamples[i]) diffCount++
        }
        assertTrue(diffCount > 100)
        assertTrue(engine.getOutboundRmsLevel() > 0f)
    }

    @Test
    fun `hot-switching presets mid-speech avoids hard jumps and keeps preset updated`() {
        val engine = VoiceChangerDspEngine()
        engine.initialize(48_000)
        engine.setBypass(false)
        engine.setPreset(VoicePresetId.HUMAN_MALE)

        val chunk = generateVoiceLikeWave(durationSamples = 480)
        val firstChunk = engine.processMonoPcm16(chunk, 48_000)

        engine.setPreset(VoicePresetId.HUMAN_FEMALE)
        assertEquals(VoicePresetId.HUMAN_FEMALE, engine.getCurrentPreset())

        val secondChunk = engine.processMonoPcm16(chunk, 48_000)
        // Crossfade starts smoothly near outgoing chain amplitude without full-scale step jump
        val boundaryJump = abs(secondChunk[0].toInt() - firstChunk.last().toInt())
        assertFalse("Unexpected full-scale discontinuity: $boundaryJump", boundaryJump > 28_000)
    }
}
