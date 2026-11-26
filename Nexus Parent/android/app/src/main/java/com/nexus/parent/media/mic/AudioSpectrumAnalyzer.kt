package com.nexus.parent.media.mic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioSpectrumAnalyzer.kt - Real-time 36-bar frequency spectrum extractor matching Desktop `mic.ts`.
 *
 * Hey friend! Converts incoming 10ms decoded PCM frames from Child's remote `AudioTrack` into 36
 * smoothed frequency bars in `[0.08f..1.0f]` using a Hann-windowed 128-point radix-2 FFT.
 * Throttles UI emissions to ~30fps so Compose renders fluid waves with negligible CPU cost.
 */
class AudioSpectrumAnalyzer(
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val BAR_COUNT = 36
        const val MIN_BAR_AMPLITUDE = 0.08f
        private const val FFT_SIZE = 128
        private const val MIN_EMIT_INTERVAL_MS = 33L
        private const val SMOOTHING_DECAY = 0.72f
        val IDLE_BARS: List<Float> = List(BAR_COUNT) { 0.12f }
    }

    private val _bars = MutableStateFlow(IDLE_BARS)
    val bars: StateFlow<List<Float>> = _bars.asStateFlow()

    @Volatile
    private var isListeningActive: Boolean = false

    private val ringBuffer = FloatArray(FFT_SIZE)
    private var ringCursor = 0
    private var samplesAccumulated = 0
    private var lastEmitMs = 0L

    private val hannWindow = FloatArray(FFT_SIZE) { n ->
        (0.5 * (1.0 - cos((2.0 * PI * n) / (FFT_SIZE - 1)))).toFloat()
    }
    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)
    private val smoothedBars = FloatArray(BAR_COUNT) { 0.12f }

    // Precompute monotonic bin ranges mapping 64 FFT magnitude bins -> 36 visual bars
    private val barBinRanges = Array(BAR_COUNT) { barIdx ->
        val start = ((barIdx * (FFT_SIZE / 2)) / BAR_COUNT).coerceIn(1, (FFT_SIZE / 2) - 1)
        val end = (((barIdx + 1) * (FFT_SIZE / 2)) / BAR_COUNT).coerceIn(start + 1, FFT_SIZE / 2)
        start until end
    }

    fun setListeningActive(active: Boolean) {
        isListeningActive = active
        if (!active) {
            ringBuffer.fill(0f)
            ringCursor = 0
            samplesAccumulated = 0
            smoothedBars.fill(0.12f)
            _bars.value = IDLE_BARS
        }
    }

    /**
     * Ingests a 10ms WebRTC render PCM [ByteBuffer] without mutating playout samples.
     */
    fun onRenderPcmBuffer(buffer: ByteBuffer, channels: Int) {
        if (!isListeningActive) return
        val safeChannels = channels.coerceAtLeast(1)
        val totalShorts = buffer.remaining() / 2
        val frames = totalShorts / safeChannels
        if (frames <= 0) return

        val reader = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until safeChannels) {
                sum += reader.getShort() / 32768f
            }
            pushSample(sum / safeChannels.toFloat())
        }
        maybeComputeAndEmit()
    }

    /**
     * Pure-Kotlin PCM array entry point for unit testing and offline inspection.
     */
    fun onMonoPcm16Samples(samples: ShortArray, forceEmit: Boolean = false): List<Float> {
        if (!isListeningActive) return IDLE_BARS
        for (sample in samples) {
            pushSample(sample / 32768f)
        }
        return if (forceEmit) {
            computeAndUpdateBars()
        } else {
            maybeComputeAndEmit()
            _bars.value
        }
    }

    fun buildSpeakingPulseBars(outboundLevel: Float, stepRad: Double): List<Float> {
        val baseDrive = outboundLevel.coerceIn(0.18f, 1.0f)
        return List(BAR_COUNT) { idx ->
            val wave = kotlin.math.abs(sin(stepRad * 2.1 + idx * 0.36) * 0.5 + cos(stepRad * 2.9 - idx * 0.21) * 0.35)
            (0.16f + baseDrive * (0.28f + 0.56f * wave.toFloat())).coerceIn(MIN_BAR_AMPLITUDE, 1f)
        }
    }

    private fun pushSample(normalized: Float) {
        ringBuffer[ringCursor] = normalized
        ringCursor = (ringCursor + 1) % FFT_SIZE
        if (samplesAccumulated < FFT_SIZE) {
            samplesAccumulated++
        }
    }

    private fun maybeComputeAndEmit() {
        if (samplesAccumulated < FFT_SIZE) return
        val now = timeProviderMs()
        if (now - lastEmitMs < MIN_EMIT_INTERVAL_MS) return
        lastEmitMs = now
        computeAndUpdateBars()
    }

    private fun computeAndUpdateBars(): List<Float> {
        for (i in 0 until FFT_SIZE) {
            val idx = (ringCursor + i) % FFT_SIZE
            real[i] = ringBuffer[idx] * hannWindow[i]
            imag[i] = 0f
        }

        fftInPlace(real, imag)

        val result = ArrayList<Float>(BAR_COUNT)
        for (barIdx in 0 until BAR_COUNT) {
            var peakMag = 0f
            for (bin in barBinRanges[barIdx]) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]) / (FFT_SIZE * 0.35f)
                if (mag > peakMag) peakMag = mag
            }
            // Log-compressed perceptual scaling so whispers and loud sounds both show clear motion
            val scaled = (ln(1f + peakMag * 18f) / ln(19f)).coerceIn(MIN_BAR_AMPLITUDE, 1f)
            val blended = (smoothedBars[barIdx] * SMOOTHING_DECAY) + (scaled * (1f - SMOOTHING_DECAY))
            smoothedBars[barIdx] = blended.coerceIn(MIN_BAR_AMPLITUDE, 1f)
            result.add(smoothedBars[barIdx])
        }
        _bars.value = result
        return result
    }

    /**
     * Iterative radix-2 Cooley-Tukey FFT on arrays of length [FFT_SIZE] (128).
     */
    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = (-2.0 * PI / len)
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val evenIdx = i + k
                    val oddIdx = i + k + half
                    val uRe = re[evenIdx]
                    val uIm = im[evenIdx]
                    val vRe = re[oddIdx] * wRe - im[oddIdx] * wIm
                    val vIm = re[oddIdx] * wIm + im[oddIdx] * wRe
                    re[evenIdx] = uRe + vRe
                    im[evenIdx] = uIm + vIm
                    re[oddIdx] = uRe - vRe
                    im[oddIdx] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
