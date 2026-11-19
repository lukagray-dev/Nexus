package com.nexus.child.components.screen

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * OpusEncoder encodes raw PCM audio buffers to Opus format using Android's native hardware MediaCodec.
 *
 * Hey friend! Here is why Opus was selected for system audio streaming:
 * 1. Designed specifically for interactive real-time low-latency audio transmission (20ms frames).
 * 2. Compresses 1,920 bytes of 16-bit 48kHz PCM into compact ~80-byte Opus frames (32 kbps).
 * 3. These compact packets stream with microsecond latency across the existing WebRTC DataChannel
 *    without complex RTP transceiver negotiation.
 * 4. Desktop Parent receives and decodes the Opus packets via Web Audio API, playing system sound
 *    in sync with the Full HD screen video.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class OpusEncoder {

    companion object {
        private const val TAG = "OpusEncoder"

        // Opus format specifications matching SystemAudioCapturer
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 1
        const val BITRATE = 32000
        const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS

        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 960 samples
        const val FRAME_BYTES = SAMPLES_PER_FRAME * 2 // 1920 bytes

        private const val TIMEOUT_US = 10000L // 10ms
    }

    private var mediaCodec: MediaCodec? = null

    @Volatile
    private var isEncoding = false

    /**
     * Initializes and starts the MediaCodec Opus encoder.
     */
    fun start(): Boolean {
        if (isEncoding) {
            Log.d(TAG, "Opus encoder already started")
            return true
        }

        try {
            Log.d(TAG, "Configuring MediaCodec Opus encoder (48kHz Mono, 32kbps)...")
            val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES)
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            mediaCodec = codec
            isEncoding = true
            Log.d(TAG, "Opus encoder started successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Opus encoder: ${e.message}", e)
            cleanup()
            return false
        }
    }

    /**
     * Encodes a single 20ms PCM audio buffer into compressed Opus format.
     *
     * @param pcmData Raw 16-bit PCM bytes (1920 bytes).
     * @param size Number of bytes to encode.
     * @return Encoded Opus byte array, or null if buffer was queued without immediate output.
     */
    fun encode(pcmData: ByteArray, size: Int): ByteArray? {
        val codec = mediaCodec ?: return null
        if (!isEncoding) return null

        try {
            // Queue raw PCM frame to encoder input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, 0, size)

                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    size,
                    System.nanoTime() / 1000L,
                    0
                )
            }

            // Dequeue encoded Opus packet
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                val opusData = ByteArray(bufferInfo.size)

                outputBuffer?.position(bufferInfo.offset)
                outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer?.get(opusData)

                codec.releaseOutputBuffer(outputBufferIndex, false)
                return opusData
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Opus encoding error: ${e.message}", e)
            return null
        }
    }

    /**
     * Stops the encoder and frees native resources.
     */
    fun stop() {
        if (!isEncoding) return
        Log.d(TAG, "Stopping Opus encoder...")
        isEncoding = false
        cleanup()
    }

    private fun cleanup() {
        try {
            mediaCodec?.stop()
        } catch (_: Exception) {}

        try {
            mediaCodec?.release()
        } catch (_: Exception) {}

        mediaCodec = null
        isEncoding = false
    }

    fun isRunning(): Boolean = isEncoding
}
