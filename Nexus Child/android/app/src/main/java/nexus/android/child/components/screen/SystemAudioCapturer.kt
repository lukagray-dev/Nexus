package nexus.android.child.components.screen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * SystemAudioCapturer - Captures system audio playback for DataChannel streaming
 * 
 * ARCHITECTURE:
 * - Uses AudioRecord with AudioPlaybackCaptureConfiguration (Android 10+)
 * - Captures audio from apps with USAGE_MEDIA, USAGE_GAME, USAGE_UNKNOWN
 * - Runs capture loop in background thread
 * - Delivers raw PCM audio via callback for encoding and DataChannel transmission
 * 
 * AUDIO FORMAT:
 * - Sample rate: 48000 Hz (Opus native rate, no resampling needed)
 * - Encoding: PCM 16-bit signed little-endian
 * - Channels: Mono (1 channel) - reduces bandwidth
 * - Frame size: 20ms (960 samples @ 48kHz) - Opus optimal frame size
 * 
 * WHY DATACHANNEL INSTEAD OF WEBRTC AUDIO TRACK:
 * - No WebRTC audio processing overhead (AGC, AEC, etc.)
 * - Direct Opus encoding control (better quality, lower latency)
 * - No track mixing complexity on parent side
 * - Simpler implementation (no WebRTC internal modifications needed)
 * 
 * LIMITATIONS:
 * - Android 10+ only
 * - Cannot capture DRM-protected content (Netflix, Spotify, etc.)
 * - Cannot capture voice communication (USAGE_VOICE_COMMUNICATION)
 * - Apps can opt-out with ALLOW_CAPTURE_BY_NONE policy
 * 
 * USAGE:
 * ```
 * val capturer = SystemAudioCapturer(mediaProjection)
 * capturer.startCapture { pcmData, bytesRead ->
 *     // Encode to Opus and send via DataChannel
 *     val opusData = opusEncoder.encode(pcmData, bytesRead)
 *     dataChannel.send(opusData)
 * }
 * capturer.stopCapture()
 * ```
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioCapturer(private val mediaProjection: MediaProjection) {
    
    companion object {
        private const val TAG = "SystemAudioCapturer"
        
        // Audio configuration optimized for Opus encoding
        const val SAMPLE_RATE = 48000 // Hz - Opus native rate (no resampling)
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // Mono reduces bandwidth
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit signed PCM
        const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
        const val CHANNELS = 1 // Mono
        
        // Opus optimal frame size: 20ms @ 48kHz = 960 samples
        const val FRAME_DURATION_MS = 20 // 20ms frames (Opus optimal)
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 960 samples
        const val FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE * CHANNELS // 1920 bytes
    }
    
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile
    private var isCapturing = false
    private var audioCallback: ((ByteArray, Int) -> Unit)? = null
    
    /**
     * Start capturing system audio
     * 
     * @param callback Called with raw PCM data (20ms frames, 1920 bytes)
     *                 Parameters: (pcmData: ByteArray, bytesRead: Int)
     * @return true if capture started successfully, false otherwise
     */
    fun startCapture(callback: (ByteArray, Int) -> Unit): Boolean {
        if (isCapturing) {
            Log.w(TAG, "⚠️ Already capturing")
            return false
        }
        
        try {
            Log.d(TAG, "🔊 Starting system audio capture for DataChannel streaming")
            
            // Create AudioPlaybackCaptureConfiguration
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Music, videos
                .addMatchingUsage(AudioAttributes.USAGE_GAME) // Game audio
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN) // Other audio
                .build()
            
            // Create AudioFormat (Opus-optimized: 48kHz, Mono, 16-bit PCM)
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()
            
            // Calculate minimum buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Invalid buffer size: $minBufferSize")
                return false
            }
            
            // Use larger of frame size or minimum size
            val bufferSize = maxOf(FRAME_SIZE_BYTES, minBufferSize)
            
            Log.d(TAG, "📊 Audio config: 48kHz Mono, 20ms frames (960 samples, 1920 bytes)")
            Log.d(TAG, "📊 Buffer size: $bufferSize bytes (min: $minBufferSize)")
            
            // Create AudioRecord with playback capture configuration
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            // Start recording
            audioRecord?.startRecording()
            
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "❌ AudioRecord not recording")
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            // Store callback
            audioCallback = callback
            isCapturing = true
            
            // Start capture thread
            startCaptureThread()
            
            Log.d(TAG, "✅ System audio capture started (will capture MEDIA/GAME/UNKNOWN usage)")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start system audio capture", e)
            cleanup()
            return false
        }
    }
    
    /**
     * Stop capturing system audio
     */
    fun stopCapture() {
        if (!isCapturing) {
            return
        }
        
        Log.d(TAG, "🛑 Stopping system audio capture")
        
        isCapturing = false
        
        // Wait for capture thread to finish
        captureThread?.interrupt()
        captureThread?.join(1000)
        captureThread = null
        
        cleanup()
        
        Log.d(TAG, "✅ System audio capture stopped")
    }
    
    /**
     * Start background thread for audio capture
     * Reads 20ms frames (960 samples, 1920 bytes) continuously
     */
    private fun startCaptureThread() {
        captureThread = thread(name = "SystemAudioCapture") {
            Log.d(TAG, "📡 Capture thread started")
            
            // Allocate buffer for 20ms frames (Opus optimal)
            val buffer = ByteBuffer.allocateDirect(FRAME_SIZE_BYTES).order(ByteOrder.nativeOrder())
            val audioData = ByteArray(FRAME_SIZE_BYTES)
            
            while (isCapturing && !Thread.currentThread().isInterrupted) {
                try {
                    // Read 20ms frame from AudioRecord
                    buffer.clear()
                    val bytesRead = audioRecord?.read(buffer, FRAME_SIZE_BYTES, AudioRecord.READ_BLOCKING) ?: 0
                    
                    if (bytesRead > 0) {
                        // Convert ByteBuffer to byte array
                        buffer.rewind()
                        buffer.get(audioData, 0, bytesRead)
                        
                        // Deliver raw PCM to callback for Opus encoding
                        audioCallback?.invoke(audioData, bytesRead)
                        
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "⚠️ AudioRecord read error: $bytesRead")
                        break
                    }
                    
                } catch (e: InterruptedException) {
                    Log.d(TAG, "📡 Capture thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in capture thread", e)
                    break
                }
            }
            
            Log.d(TAG, "📡 Capture thread stopped")
        }
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error stopping AudioRecord", e)
        }
        
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error releasing AudioRecord", e)
        }
        
        audioRecord = null
        audioCallback = null
    }
    
    /**
     * Check if currently capturing
     */
    fun isCapturing(): Boolean = isCapturing
}
