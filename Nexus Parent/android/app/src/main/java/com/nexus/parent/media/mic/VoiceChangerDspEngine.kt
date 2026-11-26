package com.nexus.parent.media.mic

import com.nexus.parent.state.VoicePresetId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * VoiceChangerDspEngine.kt - Real-time 10-preset Kotlin PCM Voice Changer DSP engine matching
 * Desktop `voice-changer-engine.ts` 1:1.
 *
 * Hey friend! Here is how real-time voice morphing works inside WebRTC's native 10ms audio frame
 * callback (`ExternalAudioProcessingFactory.setCapturePostProcessing`):
 * 1. Each 10ms callback passes raw 16-bit signed PCM samples right after AEC/NS capture and right
 *    BEFORE Opus encoding.
 * 2. When `isBypassed == true` (parent is muted/idle), we mute frames silently or pass clean zero
 *    level without touching preset state.
 * 3. When broadcasting, audio flows through modular Kotlin DSP blocks matching Desktop's 10 presets:
 *    - Dual-grain triangle-windowed granular pitch shifter (pitch up/down cleanly without FFT artifacts)
 *    - Ring modulators (sawtooth/triangle carrier oscillators)
 *    - Bilinear IIR biquad filters (lowpass, highpass, bandpass, lowshelf, highshelf, peaking)
 *    - Non-linear sigmoid harmonic distortion waveshaper
 *    - Spatial delay lines (ghost slapback, LFO chorus, damped canyon echo feedback)
 * 4. Switching voices mid-speech triggers a smooth 25ms crossfade between the outgoing and incoming
 *    DSP chains so Child hears zero clicks, pops, or WebRTC dropouts!
 */
class VoiceChangerDspEngine {

    companion object {
        private const val CROSSFADE_DURATION_SEC = 0.025f
        private const val GRAIN_DURATION_SEC = 0.080f
        private const val DEFAULT_SAMPLE_RATE = 48_000
    }

    private val lock = Any()

    @Volatile
    private var sampleRateHz: Int = DEFAULT_SAMPLE_RATE

    @Volatile
    private var currentPreset: VoicePresetId = VoicePresetId.ORIGINAL

    @Volatile
    private var isMutedOrBypassed: Boolean = true

    @Volatile
    private var lastOutboundRmsLevel: Float = 0f

    private var activeChain: PresetChain = buildChain(VoicePresetId.ORIGINAL, DEFAULT_SAMPLE_RATE)
    private var outgoingChain: PresetChain? = null
    private var crossfadeRemainingSamples: Int = 0
    private var crossfadeTotalSamples: Int = 1

    // Pre-allocated scratch buffers so real-time audio callbacks generate zero per-frame garbage
    private var monoInputScratch = FloatArray(1024)
    private var wetOutputScratch = FloatArray(1024)
    private var prevOutputScratch = FloatArray(1024)

    fun initialize(sampleRateHz: Int) {
        synchronized(lock) {
            val safeRate = sampleRateHz.coerceIn(8_000, 96_000)
            if (this.sampleRateHz != safeRate) {
                this.sampleRateHz = safeRate
                activeChain = buildChain(currentPreset, safeRate)
                outgoingChain = null
                crossfadeRemainingSamples = 0
            }
        }
    }

    fun setBypass(bypass: Boolean) {
        isMutedOrBypassed = bypass
        if (bypass) {
            lastOutboundRmsLevel = 0f
        }
    }

    fun getCurrentPreset(): VoicePresetId = currentPreset

    fun getOutboundRmsLevel(): Float = lastOutboundRmsLevel

    fun setPreset(newPreset: VoicePresetId) {
        synchronized(lock) {
            if (newPreset == currentPreset) return
            val nextChain = buildChain(newPreset, sampleRateHz)
            outgoingChain = activeChain
            activeChain = nextChain
            currentPreset = newPreset

            val fadeSamples = (sampleRateHz * CROSSFADE_DURATION_SEC).toInt().coerceAtLeast(1)
            crossfadeTotalSamples = fadeSamples
            crossfadeRemainingSamples = fadeSamples
        }
    }

    fun reset() {
        synchronized(lock) {
            activeChain.reset()
            outgoingChain = null
            crossfadeRemainingSamples = 0
            lastOutboundRmsLevel = 0f
        }
    }

    /**
     * Mutates a WebRTC 10ms PCM [ByteBuffer] (little-endian signed 16-bit interleaved) in-place.
     */
    fun processCaptureBufferInPlace(buffer: ByteBuffer, sampleRateHz: Int, channels: Int) {
        val safeChannels = channels.coerceAtLeast(1)
        val totalBytes = buffer.remaining()
        val totalShorts = totalBytes / 2
        if (totalShorts <= 0) return

        val frames = totalShorts / safeChannels
        if (frames <= 0) return

        // Always operate in explicit little-endian order on WebRTC native PCM memory
        val duplicate = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        if (isMutedOrBypassed) {
            lastOutboundRmsLevel = 0f
            return
        }

        ensureScratchCapacity(frames)

        // Downmix to normalized mono [-1f..1f] while processing
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until safeChannels) {
                sum += duplicate.getShort() / 32768f
            }
            monoInputScratch[i] = sum / safeChannels.toFloat()
        }

        synchronized(lock) {
            if (this.sampleRateHz != sampleRateHz && sampleRateHz in 8_000..96_000) {
                this.sampleRateHz = sampleRateHz
                activeChain = buildChain(currentPreset, sampleRateHz)
                outgoingChain = null
                crossfadeRemainingSamples = 0
            }

            processSamplesLocked(frames)
        }

        // Write morphed mono back into all channels with soft safety limiter
        var energySum = 0.0
        val outWriter = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val limited = softLimitSample(wetOutputScratch[i])
            energySum += (limited * limited).toDouble()
            val pcmShort = (limited * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            for (c in 0 until safeChannels) {
                outWriter.putShort(pcmShort)
            }
        }
        val rms = sqrt(energySum / frames.toDouble()).toFloat()
        lastOutboundRmsLevel = (rms * 3.2f).coerceIn(0f, 1f)
    }

    /**
     * Pure-Kotlin helper used both internally and in JVM unit tests for deterministic PCM frames.
     */
    fun processMonoPcm16(samples: ShortArray, sampleRateHz: Int = DEFAULT_SAMPLE_RATE): ShortArray {
        val frames = samples.size
        if (frames == 0 || isMutedOrBypassed) return samples.copyOf()

        ensureScratchCapacity(frames)
        for (i in 0 until frames) {
            monoInputScratch[i] = samples[i] / 32768f
        }

        synchronized(lock) {
            if (this.sampleRateHz != sampleRateHz && sampleRateHz in 8_000..96_000) {
                this.sampleRateHz = sampleRateHz
                activeChain = buildChain(currentPreset, sampleRateHz)
                outgoingChain = null
                crossfadeRemainingSamples = 0
            }
            processSamplesLocked(frames)
        }

        val out = ShortArray(frames)
        var energySum = 0.0
        for (i in 0 until frames) {
            val limited = softLimitSample(wetOutputScratch[i])
            energySum += (limited * limited).toDouble()
            out[i] = (limited * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        lastOutboundRmsLevel = (sqrt(energySum / frames.toDouble()).toFloat() * 3.2f).coerceIn(0f, 1f)
        return out
    }

    private fun processSamplesLocked(frames: Int) {
        val prev = outgoingChain
        if (prev != null && crossfadeRemainingSamples > 0) {
            activeChain.process(monoInputScratch, wetOutputScratch, frames)
            prev.process(monoInputScratch, prevOutputScratch, frames)

            for (i in 0 until frames) {
                if (crossfadeRemainingSamples > 0) {
                    val elapsed = crossfadeTotalSamples - crossfadeRemainingSamples
                    val alphaNew = (elapsed.toFloat() / crossfadeTotalSamples.toFloat()).coerceIn(0f, 1f)
                    val alphaOld = 1f - alphaNew
                    wetOutputScratch[i] = (wetOutputScratch[i] * alphaNew) + (prevOutputScratch[i] * alphaOld)
                    crossfadeRemainingSamples--
                }
            }
            if (crossfadeRemainingSamples <= 0) {
                outgoingChain = null
            }
        } else {
            activeChain.process(monoInputScratch, wetOutputScratch, frames)
        }
    }

    private fun ensureScratchCapacity(frames: Int) {
        if (monoInputScratch.size < frames) {
            monoInputScratch = FloatArray(frames)
            wetOutputScratch = FloatArray(frames)
            prevOutputScratch = FloatArray(frames)
        }
    }

    private fun softLimitSample(value: Float): Float {
        if (!value.isFinite()) return 0f
        if (abs(value) <= 0.85f) return value
        // Smooth saturation curve above 0.85f prevents harsh digital clipping
        return (value / (1f + abs(value) * 0.35f)).coerceIn(-0.99f, 0.99f)
    }

    // -------------------------------------------------------------------------
    // PRESET DSP GRAPH FACTORY (1:1 WITH DESKTOP voice-changer-engine.ts)
    // -------------------------------------------------------------------------

    private fun buildChain(preset: VoicePresetId, rate: Int): PresetChain {
        return when (preset) {
            VoicePresetId.ORIGINAL -> OriginalPresetChain()
            VoicePresetId.ROBOT_MALE -> RobotPresetChain(rate, carrierHz = 62f, bandpassHz = 1050f, pitchRatio = 0.88f)
            VoicePresetId.ROBOT_FEMALE -> RobotPresetChain(rate, carrierHz = 92f, bandpassHz = 1600f, pitchRatio = 1.18f)
            VoicePresetId.HUMAN_MALE -> HumanMalePresetChain(rate)
            VoicePresetId.HUMAN_FEMALE -> HumanFemalePresetChain(rate)
            VoicePresetId.MALE_MONSTAR -> MonsterMalePresetChain(rate)
            VoicePresetId.FEMALE_MONSTAR -> MonsterFemalePresetChain(rate)
            VoicePresetId.OVERALL_MORPHED -> OverallMorphedPresetChain(rate)
            VoicePresetId.ECHO -> EchoPresetChain(rate)
            VoicePresetId.ALIEN -> AlienPresetChain(rate)
        }
    }

    private interface PresetChain {
        fun process(input: FloatArray, output: FloatArray, frames: Int)
        fun reset()
    }

    private class OriginalPresetChain : PresetChain {
        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            System.arraycopy(input, 0, output, 0, frames)
        }

        override fun reset() = Unit
    }

    private class RobotPresetChain(
        rate: Int,
        carrierHz: Float,
        bandpassHz: Float,
        pitchRatio: Float
    ) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio)
        private val osc = PhaseOscillator(rate, carrierHz, Waveform.SAWTOOTH)
        private val bandpass = BiquadFilter.bandpass(rate, bandpassHz, q = 2.2f)
        private val shaper = WaveShaper(amount = 18f)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val pitched = pitch.processSample(input[i])
                val modulated = pitched * osc.nextSample()
                val shaped = shaper.shape(bandpass.process(modulated))
                output[i] = (pitched * 0.35f) + (shaped * 0.85f)
            }
        }

        override fun reset() {
            pitch.reset()
            osc.reset()
            bandpass.reset()
        }
    }

    private class HumanMalePresetChain(rate: Int) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio = 0.82f)
        private val bassShelf = BiquadFilter.lowShelf(rate, freqHz = 180f, gainDb = 5.5f)
        private val trebleShelf = BiquadFilter.highShelf(rate, freqHz = 4500f, gainDb = -3.0f)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val x = pitch.processSample(input[i])
                output[i] = trebleShelf.process(bassShelf.process(x))
            }
        }

        override fun reset() {
            pitch.reset()
            bassShelf.reset()
            trebleShelf.reset()
        }
    }

    private class HumanFemalePresetChain(rate: Int) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio = 1.22f)
        private val highpass = BiquadFilter.highpass(rate, freqHz = 160f, q = 0.707f)
        private val presence = BiquadFilter.peaking(rate, freqHz = 2800f, q = 1.4f, gainDb = 4.0f)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val x = pitch.processSample(input[i])
                output[i] = presence.process(highpass.process(x))
            }
        }

        override fun reset() {
            pitch.reset()
            highpass.reset()
            presence.reset()
        }
    }

    private class MonsterMalePresetChain(rate: Int) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio = 0.63f)
        private val subBass = BiquadFilter.lowShelf(rate, freqHz = 130f, gainDb = 8.0f)
        private val darkFilter = BiquadFilter.lowpass(rate, freqHz = 3200f, q = 0.707f)
        private val shaper = WaveShaper(amount = 35f)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val x = pitch.processSample(input[i])
                val filtered = darkFilter.process(subBass.process(x))
                output[i] = shaper.shape(filtered) * 1.15f
            }
        }

        override fun reset() {
            pitch.reset()
            subBass.reset()
            darkFilter.reset()
        }
    }

    private class MonsterFemalePresetChain(rate: Int) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio = 0.86f)
        private val shriek = BiquadFilter.peaking(rate, freqHz = 2200f, q = 4.2f, gainDb = 7.0f)
        private val lfo = PhaseOscillator(rate, freqHz = 11f, waveform = Waveform.SINE)
        private val slapDelay = SimpleDelayLine((rate * 0.045f).toInt().coerceAtLeast(1))

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val pitched = shriek.process(pitch.processSample(input[i]))
                val modulated = pitched * (0.65f + 0.35f * lfo.nextSample())
                val delayed = slapDelay.tapAndPush(modulated)
                output[i] = modulated + (delayed * 0.45f)
            }
        }

        override fun reset() {
            pitch.reset()
            shriek.reset()
            lfo.reset()
            slapDelay.reset()
        }
    }

    private class OverallMorphedPresetChain(private val rate: Int) : PresetChain {
        private val pitch = GranularPitchShifter(rate, pitchRatio = 1.08f)
        private val formant1 = BiquadFilter.peaking(rate, freqHz = 750f, q = 2.0f, gainDb = 6.0f)
        private val formant2 = BiquadFilter.peaking(rate, freqHz = 2400f, q = 2.5f, gainDb = 5.0f)
        private val lfo = PhaseOscillator(rate, freqHz = 0.9f, waveform = Waveform.SINE)
        private val chorusLine = ModulatedDelayLine((rate * 0.05f).toInt().coerceAtLeast(16))

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val core = formant2.process(formant1.process(pitch.processSample(input[i])))
                val delayMs = 18f + 3f * lfo.nextSample()
                val delaySamples = (delayMs * rate / 1000f).coerceAtLeast(1f)
                val wet = chorusLine.readInterpolated(delaySamples)
                chorusLine.push(core)
                output[i] = core + (wet * 0.6f)
            }
        }

        override fun reset() {
            pitch.reset()
            formant1.reset()
            formant2.reset()
            lfo.reset()
            chorusLine.reset()
        }
    }

    private class EchoPresetChain(rate: Int) : PresetChain {
        private val delaySamples = (rate * 0.240f).toInt().coerceAtLeast(1)
        private val delay = SimpleDelayLine(delaySamples)
        private val damping = BiquadFilter.lowpass(rate, freqHz = 2800f, q = 0.707f)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val dry = input[i]
                val delayed = delay.peekOldest()
                val damped = damping.process(delayed)
                delay.push(dry + damped * 0.42f)
                output[i] = dry + (damped * 0.65f)
            }
        }

        override fun reset() {
            delay.reset()
            damping.reset()
        }
    }

    private class AlienPresetChain(rate: Int) : PresetChain {
        private val peak = BiquadFilter.peaking(rate, freqHz = 2400f, q = 4.8f, gainDb = 6.5f)
        private val lfo = PhaseOscillator(rate, freqHz = 18f, waveform = Waveform.SINE)
        private val carrier = PhaseOscillator(rate, freqHz = 135f, waveform = Waveform.TRIANGLE)

        override fun process(input: FloatArray, output: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                val filtered = peak.process(input[i])
                val tremolo = filtered * (0.5f + 0.45f * lfo.nextSample())
                val spaceTone = carrier.nextSample() * 0.14f * (abs(filtered) * 2.5f + 0.15f).coerceAtMost(1f)
                output[i] = tremolo + spaceTone
            }
        }

        override fun reset() {
            peak.reset()
            lfo.reset()
            carrier.reset()
        }
    }

    // -------------------------------------------------------------------------
    // DSP PRIMITIVES
    // -------------------------------------------------------------------------

    private enum class Waveform { SINE, SAWTOOTH, TRIANGLE }

    private class PhaseOscillator(
        private val sampleRate: Int,
        private val freqHz: Float,
        private val waveform: Waveform
    ) {
        private var phase = 0f

        fun nextSample(): Float {
            val out = when (waveform) {
                Waveform.SINE -> sin((phase * 2.0 * PI)).toFloat()
                Waveform.SAWTOOTH -> (2f * phase) - 1f
                Waveform.TRIANGLE -> 1f - 4f * abs(phase - 0.5f)
            }
            phase += freqHz / sampleRate.toFloat()
            if (phase >= 1f) phase -= 1f
            return out
        }

        fun reset() {
            phase = 0f
        }
    }

    /**
     * Sigmoid soft saturation matching Web Audio `WaveShaperNode`.
     */
    private class WaveShaper(amount: Float) {
        private val table = FloatArray(1024)

        init {
            val deg = (PI / 180.0).toFloat()
            for (i in table.indices) {
                val x = (i * 2f) / table.size.toFloat() - 1f
                table[i] = ((3f + amount) * x * 20f * deg) / (PI.toFloat() + amount * abs(x))
            }
        }

        fun shape(x: Float): Float {
            val norm = ((x.coerceIn(-1f, 1f) + 1f) * 0.5f) * (table.size - 1)
            val idx = norm.toInt().coerceIn(0, table.size - 1)
            return table[idx]
        }
    }

    private class SimpleDelayLine(capacity: Int) {
        private val buffer = FloatArray(capacity)
        private var cursor = 0

        fun peekOldest(): Float = buffer[cursor]

        fun push(sample: Float) {
            buffer[cursor] = sample
            cursor = (cursor + 1) % buffer.size
        }

        fun tapAndPush(sample: Float): Float {
            val oldest = buffer[cursor]
            buffer[cursor] = sample
            cursor = (cursor + 1) % buffer.size
            return oldest
        }

        fun reset() {
            buffer.fill(0f)
            cursor = 0
        }
    }

    private class ModulatedDelayLine(capacity: Int) {
        private val buffer = FloatArray(capacity)
        private var writeIdx = 0

        fun push(sample: Float) {
            buffer[writeIdx] = sample
            writeIdx = (writeIdx + 1) % buffer.size
        }

        fun readInterpolated(delaySamples: Float): Float {
            val clamped = delaySamples.coerceIn(0f, (buffer.size - 2).toFloat())
            var readPos = writeIdx - clamped
            if (readPos < 0f) readPos += buffer.size
            val idx0 = readPos.toInt() % buffer.size
            val idx1 = (idx0 + 1) % buffer.size
            val frac = readPos - readPos.toInt()
            return buffer[idx0] * (1f - frac) + buffer[idx1] * frac
        }

        fun reset() {
            buffer.fill(0f)
            writeIdx = 0
        }
    }

    /**
     * Dual staggered 80ms granular delay-line pitch shifter matching Desktop `createGranularPitchShifter`.
     */
    private class GranularPitchShifter(
        private val sampleRate: Int,
        private val pitchRatio: Float
    ) {
        private val grainSamples = (sampleRate * GRAIN_DURATION_SEC).toInt().coerceAtLeast(64)
        private val delayBuffer = ModulatedDelayLine(grainSamples * 3)
        private var phaseA = 0f
        private var phaseB = 0.5f

        fun processSample(input: Float): Float {
            if (abs(pitchRatio - 1.0f) < 0.02f) return input

            delayBuffer.push(input)

            // Rate step drives delay ramp velocity so frequency scales smoothly by pitchRatio
            val ratePerSample = abs(1f - pitchRatio) / grainSamples.toFloat()
            val isPitchUp = pitchRatio > 1.0f

            val delayA = computeDelay(phaseA, isPitchUp)
            val delayB = computeDelay(phaseB, isPitchUp)
            val winA = triangleWindow(phaseA)
            val winB = triangleWindow(phaseB)

            val out = delayBuffer.readInterpolated(delayA) * winA +
                delayBuffer.readInterpolated(delayB) * winB

            phaseA += ratePerSample
            if (phaseA >= 1f) phaseA -= 1f
            phaseB += ratePerSample
            if (phaseB >= 1f) phaseB -= 1f

            return out
        }

        private fun computeDelay(phase: Float, isPitchUp: Boolean): Float {
            val span = grainSamples.toFloat()
            return if (isPitchUp) (1f - phase) * span else phase * span
        }

        private fun triangleWindow(phase: Float): Float {
            return if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
        }

        fun reset() {
            delayBuffer.reset()
            phaseA = 0f
            phaseB = 0.5f
        }
    }

    /**
     * Direct Form I Biquad EQ filters (Audio EQ Cookbook).
     */
    private class BiquadFilter private constructor(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }

        companion object {
            fun lowpass(rate: Int, freqHz: Float, q: Float): BiquadFilter {
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha
                return BiquadFilter(
                    b0 = ((1f - cosW) / 2f) / a0,
                    b1 = (1f - cosW) / a0,
                    b2 = ((1f - cosW) / 2f) / a0,
                    a1 = (-2f * cosW) / a0,
                    a2 = (1f - alpha) / a0
                )
            }

            fun highpass(rate: Int, freqHz: Float, q: Float): BiquadFilter {
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha
                return BiquadFilter(
                    b0 = ((1f + cosW) / 2f) / a0,
                    b1 = -(1f + cosW) / a0,
                    b2 = ((1f + cosW) / 2f) / a0,
                    a1 = (-2f * cosW) / a0,
                    a2 = (1f - alpha) / a0
                )
            }

            fun bandpass(rate: Int, freqHz: Float, q: Float): BiquadFilter {
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha
                return BiquadFilter(
                    b0 = alpha / a0,
                    b1 = 0f,
                    b2 = -alpha / a0,
                    a1 = (-2f * cosW) / a0,
                    a2 = (1f - alpha) / a0
                )
            }

            fun peaking(rate: Int, freqHz: Float, q: Float, gainDb: Float): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0).toFloat()
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha / a
                return BiquadFilter(
                    b0 = (1f + alpha * a) / a0,
                    b1 = (-2f * cosW) / a0,
                    b2 = (1f - alpha * a) / a0,
                    a1 = (-2f * cosW) / a0,
                    a2 = (1f - alpha / a) / a0
                )
            }

            fun lowShelf(rate: Int, freqHz: Float, gainDb: Float): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0).toFloat()
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val sinW = sin(w0)
                val sqrtA2Alpha = 2f * sqrt(a) * (sinW / 2f) * sqrt(2f)
                val a0 = (a + 1f) + (a - 1f) * cosW + sqrtA2Alpha
                return BiquadFilter(
                    b0 = (a * ((a + 1f) - (a - 1f) * cosW + sqrtA2Alpha)) / a0,
                    b1 = (2f * a * ((a - 1f) - (a + 1f) * cosW)) / a0,
                    b2 = (a * ((a + 1f) - (a - 1f) * cosW - sqrtA2Alpha)) / a0,
                    a1 = (-2f * ((a - 1f) + (a + 1f) * cosW)) / a0,
                    a2 = ((a + 1f) + (a - 1f) * cosW - sqrtA2Alpha) / a0
                )
            }

            fun highShelf(rate: Int, freqHz: Float, gainDb: Float): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0).toFloat()
                val w0 = (2.0 * PI * freqHz / rate).toFloat()
                val cosW = cos(w0)
                val sinW = sin(w0)
                val sqrtA2Alpha = 2f * sqrt(a) * (sinW / 2f) * sqrt(2f)
                val a0 = (a + 1f) - (a - 1f) * cosW + sqrtA2Alpha
                return BiquadFilter(
                    b0 = (a * ((a + 1f) + (a - 1f) * cosW + sqrtA2Alpha)) / a0,
                    b1 = (-2f * a * ((a - 1f) + (a + 1f) * cosW)) / a0,
                    b2 = (a * ((a + 1f) + (a - 1f) * cosW - sqrtA2Alpha)) / a0,
                    a1 = (2f * ((a - 1f) - (a + 1f) * cosW)) / a0,
                    a2 = ((a + 1f) - (a - 1f) * cosW - sqrtA2Alpha) / a0
                )
            }
        }
    }
}
