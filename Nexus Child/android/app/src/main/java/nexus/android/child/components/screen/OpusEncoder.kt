package nexus.android.child.components.screen

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * OpusEncoder - Encodes PCM audio to Opus format using Android MediaCodec
 * 
 * ARCHITECTURE:
 * - Uses Android's built-in MediaCodec with Opus encoder (Android 10+)
 * - Optimized for low-latency real-time streaming
 * - Produces Opus packets suitable for DataChannel transmission
 * 
 * OPUS CONFIGURATION:
 * - Sample rate: 48000 Hz (Opus native rate)
 * - Channels: 1 (Mono)
 * - Bitrate: 32 kbps (good quality for system audio)
 * - Frame size: 20ms (960 samples @ 48kHz)
 * - Complexity: 5 (balance between quality and CPU)
 * 
 * WHY OPUS:
 * - Designed for real-time audio streaming
 * - Low latency (20ms frames)
 * - Excellent quality at low bitrates
 * - Wide browser support (WebRTC standard)
 * - Better than AAC for voice/music mix
 * 
 * USAGE:
 * ```
 * val encoder = OpusEncoder()
 * encoder.start()
 * 
 * // Feed 20ms PCM frames (1920 bytes)
 * val opusData = encoder.encode(pcmData, pcmSize)
 * if (opusData != null) {
 *     dataChannel.send(opusData)
 * }
 * 
 * encoder.stop()
 * ```
 */
@RequiresApi(Build.VERSION_CODES.Q)
class OpusEncoder {
    
    companion object {
        private const val TAG = "OpusEncoder"
        
        // Opus configuration (matches SystemAudioCapturer)
        private const val SAMPLE_RATE = 48000 // Hz
        private const val CHANNELS = 1 // Mono
        private const val BITRATE = 32000 // 32 kbps (good quality)
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS
        
        // Opus frame configuration
        private const val FRAME_DURATION_MS = 20 // 20ms frames
        private const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 960
        
        // MediaCodec timeout
        private const val TIMEOUT_US = 10000L // 10ms
    }
    
    private var mediaCodec: MediaCodec? = null
    private var isEncoding = false
    
    /**
     * Start the Opus encoder
     * 
     * @return true if encoder started successfully, false otherwise
     */
    fun start(): Boolean {
        if (isEncoding) {
            Log.w(TAG, "⚠️ Encoder already started")
            return false
        }
        
        try {
            Log.d(TAG, "🎵 Starting Opus encoder")
            
            // Create MediaFormat for Opus
            val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, SAMPLES_PER_FRAME * 2) // 1920 bytes
            }
            
            Log.d(TAG, "📊 Opus config: 48kHz Mono, 32kbps, 20ms frames")
            
            // Create and configure MediaCodec
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()
            
            isEncoding = true
            
            Log.d(TAG, "✅ Opus encoder started successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start Opus encoder", e)
            cleanup()
            return false
        }
    }
    
    /**
     * Encode PCM audio to Opus
     * 
     * @param pcmData Raw PCM audio data (16-bit signed, little-endian)
     * @param size Number of bytes to encode (should be 1920 for 20ms @ 48kHz mono)
     * @return Encoded Opus data, or null if encoding failed
     */
    fun encode(pcmData: ByteArray, size: Int): ByteArray? {
        if (!isEncoding || mediaCodec == null) {
            Log.w(TAG, "⚠️ Encoder not started")
            return null
        }
        
        try {
            // Get input buffer
            val inputBufferIndex = mediaCodec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
            
            if (inputBufferIndex >= 0) {
                // Fill input buffer with PCM data
                val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, 0, size)
                
                // Queue input buffer for encoding
                mediaCodec?.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    size,
                    System.nanoTime() / 1000, // Presentation time in microseconds
                    0
                )
            }
            
            // Get encoded output
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
            
            if (outputBufferIndex >= 0) {
                // Extract encoded Opus data
                val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)
                val opusData = ByteArray(bufferInfo.size)
                
                outputBuffer?.position(bufferInfo.offset)
                outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer?.get(opusData)
                
                // Release output buffer
                mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
                
                return opusData
                
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = mediaCodec?.outputFormat
                Log.d(TAG, "📊 Output format changed: $newFormat")
            }
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Encoding error", e)
            return null
        }
    }
    
    /**
     * Stop the Opus encoder
     */
    fun stop() {
        if (!isEncoding) {
            return
        }
        
        Log.d(TAG, "🛑 Stopping Opus encoder")
        
        isEncoding = false
        cleanup()
        
        Log.d(TAG, "✅ Opus encoder stopped")
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error stopping MediaCodec", e)
        }
        
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error releasing MediaCodec", e)
        }
        
        mediaCodec = null
    }
    
    /**
     * Check if encoder is running
     */
    fun isEncoding(): Boolean = isEncoding
}
