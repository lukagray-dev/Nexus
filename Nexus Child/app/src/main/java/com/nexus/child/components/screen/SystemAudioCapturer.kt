package com.nexus.child.components.screen

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SystemAudioCapturer captures internal system, game, and application audio on Android 10+ (API 29+).
 *
 * Hey friend! Here is how internal audio capture works on Android:
 * 1. Uses Android's AudioPlaybackCaptureConfiguration linked to a valid MediaProjection token.
 * 2. Targets media playback, games, and standard application audio streams.
 * 3. Captures raw 16-bit PCM audio at 48,000 Hz Mono in continuous 20ms frames (1,920 bytes / 960 samples).
 * 4. Dispatches raw PCM buffers to OpusEncoder for compression before DataChannel transmission.
 * 5. On Android 9 and older, audio capture is gracefully skipped because the OS does not support
 *    internal playback capture without root.
 */
class SystemAudioCapturer(
    private val mediaProjection: MediaProjection,
    private val onPcmFrame: (pcmBuffer: ByteArray, size: Int) -> Unit
) {

    companion object {
        private const val TAG = "SystemAudioCapturer"
        const val SAMPLE_RATE = 48000
        const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // 20ms frame at 48kHz Mono 16-bit = 48000 * 0.02 = 960 samples * 2 bytes = 1920 bytes
        const val FRAME_SIZE_BYTES = 1920
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val isCapturing = AtomicBoolean(false)

    /**
     * Initializes and starts the audio playback capture loop.
     *
     * @return true if AudioRecord was initialized and started; false otherwise.
     */
    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "System audio capture requires Android 10+ (API 29+). Skipped.")
            return false
        }

        if (isCapturing.get()) {
            Log.d(TAG, "Audio capture is already active")
            return true
        }

        try {
            Log.d(TAG, "Configuring AudioPlaybackCaptureConfiguration...")
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNELS)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, AUDIO_ENCODING)
            val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)

            val record = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record
            isCapturing.set(true)

            val thread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buffer = ByteArray(FRAME_SIZE_BYTES)

                Log.d(TAG, "System audio capture thread running at 48kHz Mono...")
                while (isCapturing.get()) {
                    val bytesRead = record.read(buffer, 0, FRAME_SIZE_BYTES)
                    if (bytesRead > 0) {
                        try {
                            onPcmFrame(buffer.clone(), bytesRead)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onPcmFrame callback: ${e.message}")
                        }
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $bytesRead")
                    }
                }
                Log.d(TAG, "System audio capture thread exited")
            }, "NexusSysAudioCaptureThread")

            captureThread = thread
            thread.start()

            Log.d(TAG, "System audio capture started successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start system audio capture: ${e.message}", e)
            stopCapture()
            return false
        }
    }

    /**
     * Stops the audio recording loop and releases native AudioRecord handles.
     */
    fun stopCapture() {
        if (!isCapturing.getAndSet(false)) {
            return
        }

        Log.d(TAG, "Stopping system audio capture...")
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            captureThread?.interrupt()
            captureThread?.join(500)
        } catch (_: Exception) {}
        captureThread = null

        Log.d(TAG, "System audio capture stopped successfully")
    }

    fun isRunning(): Boolean = isCapturing.get()
}
