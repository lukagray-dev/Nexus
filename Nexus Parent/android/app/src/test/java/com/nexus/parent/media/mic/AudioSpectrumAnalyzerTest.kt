package com.nexus.parent.media.mic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * AudioSpectrumAnalyzerTest.kt - Verifies real-time 36-bar FFT spectrum extraction and speaking pulse bars.
 */
class AudioSpectrumAnalyzerTest {

    @Test
    fun `silence produces floor bars while sine tone drives elevated bounded FFT spectrum`() {
        var nowMs = 100L
        val analyzer = AudioSpectrumAnalyzer(timeProviderMs = { nowMs })
        analyzer.setListeningActive(true)

        val silence = ShortArray(256)
        val silentBars = analyzer.onMonoPcm16Samples(silence, forceEmit = true)
        assertEquals(AudioSpectrumAnalyzer.BAR_COUNT, silentBars.size)
        assertTrue(silentBars.all { it <= 0.14f })

        // Feed energetic 1800 Hz sine burst across multiple 10ms frames
        val tone = ShortArray(480) { n ->
            (0.8f * sin(2.0 * PI * 1800.0 * n / 48_000.0) * 32767.0).toInt().toShort()
        }
        val buf = ByteBuffer.allocateDirect(tone.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        tone.forEach { buf.putShort(it) }
        buf.flip()

        nowMs += 50L
        analyzer.onRenderPcmBuffer(buf, channels = 1)
        val activeBars = analyzer.bars.value

        assertEquals(AudioSpectrumAnalyzer.BAR_COUNT, activeBars.size)
        assertTrue(activeBars.all { it in AudioSpectrumAnalyzer.MIN_BAR_AMPLITUDE..1f })
        assertTrue("Expected elevated FFT peak", (activeBars.maxOrNull() ?: 0f) > 0.30f)

        // Turning listening off resets to idle bars immediately
        analyzer.setListeningActive(false)
        assertEquals(AudioSpectrumAnalyzer.IDLE_BARS, analyzer.bars.value)
    }

    @Test
    fun `speaking pulse bars scale smoothly with outbound rms level`() {
        val analyzer = AudioSpectrumAnalyzer()
        val quietPulse = analyzer.buildSpeakingPulseBars(outboundLevel = 0.05f, stepRad = 0.5)
        val loudPulse = analyzer.buildSpeakingPulseBars(outboundLevel = 0.9f, stepRad = 0.5)

        assertEquals(AudioSpectrumAnalyzer.BAR_COUNT, loudPulse.size)
        assertTrue(loudPulse.average() > quietPulse.average())
    }
}
