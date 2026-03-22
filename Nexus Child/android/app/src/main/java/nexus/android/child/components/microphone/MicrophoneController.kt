package nexus.android.child.components.microphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender

/**
 * MicrophoneController - Manages audio capture and streaming for parental monitoring
 * 
 * ARCHITECTURE:
 * - Uses WebRTC native audio pipeline (AudioSource → AudioTrack → RtpSender)
 * - Integrates with PeerConnection for real-time streaming
 * - Supports on-demand audio capture (not always-on)
 * 
 * RENEGOTIATION FLOW:
 * - Initial Connection: Child = Answerer (battery efficient, on-demand WebRTC)
 * - Renegotiation: Child = Offerer (temporary role reversal)
 * - This allows adding/removing track BEFORE creating offer
 * - Ensures native AudioRecord initialization happens at the right time
 * 
 * CRITICAL CLEANUP ORDER:
 * 1. Disable track (stop sending data)
 * 2. Dispose AudioTrack (releases native AudioRecord)
 * 3. Dispose AudioSource (releases audio capture resources)
 * 4. Remove RtpSender from PeerConnection (updates SDP)
 * 
 * Flow:
 * 1. MIC_ON command received
 * 2. Add track to PeerConnection
 * 3. Trigger renegotiation (Child creates offer)
 * 4. Wait for renegotiation to complete
 * 5. Send MIC_STARTED confirmation
 * 
 * 6. MIC_OFF command received
 * 7. Dispose track FIRST (releases native AudioRecord)
 * 8. Remove track from PeerConnection
 * 9. Trigger renegotiation (Child creates offer)
 * 10. Wait for renegotiation to complete
 * 11. Send MIC_STOPPED confirmation
 */
class MicrophoneController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val pc: PeerConnection?
) {
    private val TAG = "MicrophoneCtrl"
    
    // Audio constraints for high-quality voice capture
    // These settings optimize for voice communication in parental monitoring scenarios
    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true")) // Remove echo from speaker feedback
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true")) // Normalize volume levels
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false")) // Keep ambient sounds (important for monitoring)
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true")) // Remove low-frequency noise
        mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true")) // Reduce keyboard sounds
        mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false")) // No audio mirroring
    }

    // WebRTC audio pipeline components
    private var audioSource: AudioSource? = null // Captures audio from device microphone
    private var audioTrack: AudioTrack? = null // Wraps audio data for WebRTC transmission
    private var rtpSender: RtpSender? = null // Sends audio packets over RTP protocol
    private var isActive = false // Tracks whether microphone is currently streaming

    /**
     * Start microphone streaming
     * 
     * IMPORTANT: This method adds the audio track to PeerConnection, which requires
     * renegotiation to update the SDP. The caller MUST trigger renegotiation after
     * this method returns true.
     * 
     * WHY ROLE REVERSAL WORKS:
     * - Child adds track → Child creates offer → Offer includes new track in SDP
     * - This ensures native AudioRecord initialization happens BEFORE offer creation
     * - Parent receives offer → Parent creates answer → Renegotiation completes
     * 
     * Returns:
     * - true: Track added successfully, renegotiation needed
     * - false: Failed to add track or already active
     */
    fun startMicrophone(): Boolean {
        Log.d(TAG, "🎤 startMicrophone() called, isActive=$isActive")
        
        if (isActive) {
            Log.w(TAG, "⚠️ Microphone already active")
            return false // Already streaming, no action needed
        }

        // Check for RECORD_AUDIO permission (required for microphone access)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Missing microphone permission")
            return false // Cannot proceed without permission
        }

        try {
            Log.d(TAG, "🎤 Setting up audio track...")
            
            // Step 1: Create AudioSource (captures raw audio from microphone)
            audioSource = factory.createAudioSource(audioConstraints)
                ?: run {
                    Log.e(TAG, "❌ Failed to create AudioSource")
                    return false
                }
            Log.d(TAG, "✅ AudioSource created: $audioSource")
            
            // Step 2: Create AudioTrack (wraps audio for WebRTC transmission)
            // Use timestamp-based ID to ensure uniqueness across multiple start/stop cycles
            audioTrack = factory.createAudioTrack("AUDIO_${System.currentTimeMillis()}", audioSource)
            Log.d(TAG, "✅ AudioTrack created: $audioTrack")
            
            // Step 3: Enable track (allows audio data to flow)
            audioTrack?.setEnabled(true)
            Log.d(TAG, "✅ AudioTrack enabled")
            
            // Step 4: Add track to PeerConnection (creates RtpSender)
            // This is where the magic happens - adding track triggers native AudioRecord initialization
            Log.d(TAG, "🎤 Adding track to PeerConnection: $pc")
            rtpSender = pc?.addTrack(audioTrack)
            Log.d(TAG, "✅ RtpSender created: $rtpSender")
            
            isActive = true
            Log.d(TAG, "✅ Microphone track added to PeerConnection (renegotiation needed)")
            
            return true // Success - caller must trigger renegotiation

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start microphone: ${e.message}", e)
            stopMicrophoneInternal() // Clean up any partially created resources
            return false
        }
    }

    /**
     * Stop microphone streaming
     * 
     * CRITICAL CLEANUP ORDER:
     * 1. Disable track (stop sending audio data)
     * 2. Dispose AudioTrack (releases native AudioRecord - THIS IS KEY!)
     * 3. Dispose AudioSource (releases audio capture resources)
     * 4. Remove RtpSender from PeerConnection (updates SDP)
     * 
     * WHY THIS ORDER MATTERS:
     * - Disposing AudioTrack BEFORE removing from PC ensures native AudioRecord is released
     * - This allows subsequent MIC_ON commands to reinitialize the hardware properly
     * - Wrong order = native AudioRecord stays locked = subsequent starts fail
     * 
     * Returns:
     * - true: Track removed successfully, renegotiation needed
     * - false: Microphone not active
     */
    fun stopMicrophone(): Boolean {
        if (!isActive) {
            Log.w(TAG, "⚠️ Microphone not active")
            return false // Nothing to stop
        }

        try {
            stopMicrophoneInternal() // Perform cleanup in correct order
            Log.d(TAG, "✅ Microphone stopped (renegotiation needed)")
            return true // Success - caller must trigger renegotiation

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop microphone: ${e.message}", e)
            stopMicrophoneInternal() // Force cleanup even on error
            return false
        }
    }
    
    /**
     * Internal cleanup logic with CRITICAL ordering
     * 
     * ORDER MATTERS:
     * 1. Disable → Stop audio data flow
     * 2. Dispose Track → Release native AudioRecord (THIS IS THE KEY FIX!)
     * 3. Dispose Source → Release audio capture resources
     * 4. Remove from PC → Update SDP for renegotiation
     * 
     * This order ensures native AudioRecord is fully released before renegotiation,
     * allowing subsequent MIC_ON commands to reinitialize the hardware properly.
     */
    private fun stopMicrophoneInternal() {
        try {
            // Step 1: Disable track (stop sending audio data)
            audioTrack?.setEnabled(false)
            Log.d(TAG, "🔇 Audio track disabled")
            
            // Step 2: Dispose AudioTrack FIRST (releases native AudioRecord)
            // THIS IS THE CRITICAL FIX - disposing before removing from PC ensures
            // native AudioRecord is fully released and can be reinitialized on next MIC_ON
            audioTrack?.dispose()
            Log.d(TAG, "🗑️ AudioTrack disposed (native AudioRecord released)")
            audioTrack = null
            
            // Step 3: Dispose AudioSource (releases audio capture resources)
            audioSource?.dispose()
            Log.d(TAG, "🗑️ AudioSource disposed")
            audioSource = null
            
            // Step 4: Remove from PeerConnection LAST (updates SDP for renegotiation)
            rtpSender?.let { sender ->
                pc?.removeTrack(sender)
                Log.d(TAG, "🗑️ RtpSender removed from PeerConnection")
            }
            rtpSender = null
            
            isActive = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in stopMicrophoneInternal: ${e.message}", e)
            // Force cleanup even on error to prevent resource leaks
            audioTrack = null
            audioSource = null
            rtpSender = null
            isActive = false
        }
    }

    /**
     * Cleanup all resources (called on connection close)
     * Ensures no resources are leaked when connection is terminated
     */
    fun cleanup() {
        try {
            Log.d(TAG, "🧹 Cleaning up microphone resources...")
            stopMicrophoneInternal()
            Log.d(TAG, "✅ Microphone cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during cleanup: ${e.message}", e)
        }
    }
    
    fun isActive(): Boolean = isActive
}
