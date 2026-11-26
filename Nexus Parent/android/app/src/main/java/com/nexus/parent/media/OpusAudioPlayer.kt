package com.nexus.parent.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpusHeaderBuilder - Builds standard RFC 7845 Opus codec-specific headers (`csd-0`, `csd-1`, `csd-2`)
 * required by Android `MediaCodec` (`audio/opus`) to decode raw 20ms Mono 48kHz Opus frames
 * streamed over the WebRTC DataChannel.
 */
object OpusHeaderBuilder {

    fun buildOpusHead(
        sampleRateHz: Int = 48_000,
        channels: Int = 1,
        preSkipSamples: Int = 312
    ): ByteArray {
        return ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1.toByte()) // Opus specification version
            put(channels.toByte())
            putShort(preSkipSamples.toShort())
            putInt(sampleRateHz)
            putShort(0.toShort()) // Output gain 0 dB
            put(0.toByte()) // Channel mapping family 0 (mono/stereo)
        }.array()
    }

    fun buildNsHeader(nanoseconds: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(nanoseconds)
        }.array()
    }
}

/**
 * OpusAudioPlayer.kt - Low-latency Android internal audio player for Screen Mirroring (`opus-audio-player.ts`).
 *
 * Hey friend! During Screen Feed mirroring, Nexus Child captures internal system/game audio at
 * 48kHz Mono PCM and sends compressed 20ms Opus frames prefixed with `OPUS_AUDIO:<base64>`.
 * This player decodes every packet on a dedicated single-thread executor using Android's native
 * `MediaCodec` Opus decoder and streams the PCM output straight into `AudioTrack`.
 */
class OpusAudioPlayer {

    companion object {
        private const val TAG = "ParentOpusAudio"
        private const val SAMPLE_RATE = 48_000
        private const val CHANNELS = 1
        private const val FRAME_DURATION_US = 20_000L
        private const val DEQUEUE_TIMEOUT_US = 2_000L
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NexusOpusAudioThread").apply { isDaemon = true }
    }
    private val isActive = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    private var mediaCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var presentationUs: Long = 0L

    fun start() {
        if (isActive.get()) return
        executor.execute {
            initPipelineLocked()
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        executor.execute {
            runCatching {
                audioTrack?.setVolume(if (muted) 0f else 1f)
            }
        }
    }

    fun feedBase64Packet(base64Payload: String) {
        if (!isActive.get() || base64Payload.isBlank()) return
        executor.execute {
            if (!isActive.get()) return@execute
            decodeAndPlayLocked(base64Payload)
        }
    }

    fun reset() {
        executor.execute {
            releasePipelineLocked()
            presentationUs = 0L
        }
    }

    fun release() {
        isActive.set(false)
        executor.execute {
            releasePipelineLocked()
        }
        executor.shutdown()
    }

    private fun initPipelineLocked() {
        if (isActive.get()) return
        runCatching {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3840)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.setVolume(if (isMuted.get()) 0f else 1f)
            track.play()

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(OpusHeaderBuilder.buildOpusHead(SAMPLE_RATE, CHANNELS)))
                setByteBuffer("csd-1", ByteBuffer.wrap(OpusHeaderBuilder.buildNsHeader(6_500_000L)))
                setByteBuffer("csd-2", ByteBuffer.wrap(OpusHeaderBuilder.buildNsHeader(80_000_000L)))
            }

            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            codec.configure(format, null, null, 0)
            codec.start()

            audioTrack = track
            mediaCodec = codec
            presentationUs = 0L
            isActive.set(true)
        }.onFailure { err ->
            Log.w(TAG, "Unable to initialize Opus decoder pipeline: ${err.message}")
            releasePipelineLocked()
        }
    }

    private fun decodeAndPlayLocked(base64Payload: String) {
        val codec = mediaCodec ?: return
        val track = audioTrack ?: return

        val opusBytes = runCatching { Base64.decode(base64Payload, Base64.DEFAULT) }.getOrNull() ?: return
        if (opusBytes.isEmpty()) return

        runCatching {
            val inputIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIdx >= 0) {
                val inputBuf = codec.getInputBuffer(inputIdx)
                inputBuf?.clear()
                inputBuf?.put(opusBytes)
                codec.queueInputBuffer(inputIdx, 0, opusBytes.size, presentationUs, 0)
                presentationUs += FRAME_DURATION_US
            }

            val info = MediaCodec.BufferInfo()
            var outIdx = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            while (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0 && !isMuted.get()) {
                    val pcm = ByteArray(info.size)
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    outBuf.get(pcm)
                    track.write(pcm, 0, pcm.size)
                }
                codec.releaseOutputBuffer(outIdx, false)
                outIdx = codec.dequeueOutputBuffer(info, 0)
            }
        }.onFailure { err ->
            Log.w(TAG, "Opus decode warning: ${err.message}")
        }
    }

    private fun releasePipelineLocked() {
        isActive.set(false)
        runCatching { mediaCodec?.stop() }
        runCatching { mediaCodec?.release() }
        mediaCodec = null

        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }
}
