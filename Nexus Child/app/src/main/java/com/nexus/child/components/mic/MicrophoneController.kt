package com.nexus.child.components.mic

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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface defining contract for microphone capture controllers.
 */
interface IMicrophoneController {
    fun hasRecordAudioPermission(): Boolean
    fun startMicrophone(): Boolean
    fun stopMicrophone(): Boolean
    fun cleanup()
    fun isActive(): Boolean
}

/**
 * MicrophoneController manages the native hardware audio capture pipeline on Android.
 *
 * Hey friend! Here is how audio capture works in WebRTC:
 * 1. An AudioSource is created from the device's physical microphone using MediaConstraints.
 * 2. An AudioTrack wraps that audio source into a standard WebRTC media track.
 * 3. The AudioTrack is added to the active PeerConnection, producing an RtpSender.
 * 4. WebRTC encodes the audio data with Opus and streams it over encrypted SRTP to the Parent.
 *
 * CRITICAL LIFECYCLE ORDER (Why order matters during shutdown):
 * When stopping the microphone, Android's native AudioRecord driver MUST be released
 * before removing the track from PeerConnection. If we remove the track first or skip
 * disposing the AudioTrack, the Android OS hardware microphone lock remains held!
 *
 * Strict disposal order:
 * 1. audioTrack.setEnabled(false)    -> Immediately stops audio frame flow
 * 2. audioTrack.dispose()            -> Releases the native AudioRecord hardware driver
 * 3. audioSource.dispose()           -> Frees the native audio capture DSP buffers
 * 4. peerConnection.removeTrack()    -> Detaches the RtpSender for SDP renegotiation
 */
class MicrophoneController(
    private val context: Context,
    private val factoryProvider: () -> PeerConnectionFactory?,
    private val peerConnectionProvider: () -> PeerConnection?
) : IMicrophoneController {

    companion object {
        private const val TAG = "MicrophoneController"
    }

    // Audio constraints configured for real-time surveillance and monitoring:
    // - Echo cancellation: active (prevents feedback if phone speaker is active)
    // - Auto gain control: active (normalizes quiet whispering and loud noises)
    // - Noise suppression: false (vital for monitoring so ambient background sounds are audible)
    // - Highpass filter: true (cuts low rumble and wind vibration)
    // - Typing noise detection: true (reduces finger tap spikes)
    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
    }

    // WebRTC native audio pipeline instances
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var rtpSender: RtpSender? = null

    // Thread-safe active flag tracking if microphone is streaming
    private val isStreaming = AtomicBoolean(false)

    /**
     * Checks whether the app currently holds the runtime RECORD_AUDIO permission.
     */
    override fun hasRecordAudioPermission(): Boolean {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts microphone audio capture and attaches the audio track to the PeerConnection.
     *
     * @return true if track was created and added successfully; false otherwise.
     */
    @Synchronized
    override fun startMicrophone(): Boolean {
        Log.d(TAG, "startMicrophone() invoked. Current streaming state: ${isStreaming.get()}")

        if (isStreaming.get()) {
            Log.w(TAG, "Microphone is already actively streaming. Ignoring start request.")
            return false
        }

        // Verify runtime RECORD_AUDIO permission before touching audio hardware
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "Cannot start microphone: Manifest.permission.RECORD_AUDIO not granted!")
            return false
        }

        val factory = factoryProvider()
        if (factory == null) {
            Log.e(TAG, "Cannot start microphone: PeerConnectionFactory is null.")
            return false
        }

        val pc = peerConnectionProvider()
        if (pc == null) {
            Log.e(TAG, "Cannot start microphone: PeerConnection is null.")
            return false
        }

        try {
            Log.d(TAG, "Step 1: Creating WebRTC AudioSource with surveillance constraints...")
            val source = factory.createAudioSource(audioConstraints)
            if (source == null) {
                Log.e(TAG, "Failed to create AudioSource from PeerConnectionFactory.")
                return false
            }
            audioSource = source

            // Step 2: Create AudioTrack with a timestamped ID to guarantee uniqueness across cycles
            val trackId = "NEXUS_AUDIO_${System.currentTimeMillis()}"
            Log.d(TAG, "Step 2: Creating WebRTC AudioTrack with ID: $trackId...")
            val track = factory.createAudioTrack(trackId, source)
            if (track == null) {
                Log.e(TAG, "Failed to create AudioTrack from AudioSource.")
                source.dispose()
                audioSource = null
                return false
            }
            audioTrack = track

            // Step 3: Enable the audio track so audio buffers begin flowing
            track.setEnabled(true)
            Log.d(TAG, "Step 3: AudioTrack enabled successfully.")

            // Step 4: Add the track to PeerConnection (creates the RtpSender)
            Log.d(TAG, "Step 4: Adding AudioTrack to active PeerConnection...")
            val sender = pc.addTrack(track)
            if (sender == null) {
                Log.e(TAG, "PeerConnection.addTrack returned null RtpSender.")
                stopMicrophoneInternal()
                return false
            }
            rtpSender = sender

            isStreaming.set(true)
            Log.d(TAG, "Microphone audio pipeline initialized and attached. Ready for renegotiation!")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred while starting microphone: ${e.message}", e)
            stopMicrophoneInternal()
            return false
        }
    }

    /**
     * Stops microphone streaming and releases native hardware resources in the exact safe sequence.
     *
     * @return true if track was removed successfully; false if it wasn't running.
     */
    @Synchronized
    override fun stopMicrophone(): Boolean {
        Log.d(TAG, "stopMicrophone() invoked. Current streaming state: ${isStreaming.get()}")

        if (!isStreaming.get()) {
            Log.w(TAG, "Microphone is not currently active. Nothing to stop.")
            return false
        }

        stopMicrophoneInternal()
        Log.d(TAG, "Microphone stopped and hardware resources cleanly released.")
        return true
    }

    /**
     * Executes the strict teardown order required by Android's native AudioRecord driver.
     */
    private fun stopMicrophoneInternal() {
        val pc = peerConnectionProvider()

        try {
            // Step 1: Disable track to halt audio frame transmission immediately
            audioTrack?.setEnabled(false)
            Log.d(TAG, "Cleanup Step 1: AudioTrack disabled.")
        } catch (e: Exception) {
            Log.w(TAG, "Warning disabling AudioTrack: ${e.message}")
        }

        try {
            // Step 2: Dispose AudioTrack FIRST. This explicitly releases the native AudioRecord
            // hardware lock so other apps or subsequent MIC_ON calls can access the mic!
            audioTrack?.dispose()
            Log.d(TAG, "Cleanup Step 2: AudioTrack disposed (native AudioRecord released).")
        } catch (e: Exception) {
            Log.w(TAG, "Warning disposing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }

        try {
            // Step 3: Dispose AudioSource to free audio capture DSP buffers
            audioSource?.dispose()
            Log.d(TAG, "Cleanup Step 3: AudioSource disposed.")
        } catch (e: Exception) {
            Log.w(TAG, "Warning disposing AudioSource: ${e.message}")
        } finally {
            audioSource = null
        }

        try {
            // Step 4: Remove RtpSender from PeerConnection last to update SDP
            rtpSender?.let { sender ->
                pc?.removeTrack(sender)
                Log.d(TAG, "Cleanup Step 4: RtpSender detached from PeerConnection.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning removing track from PeerConnection: ${e.message}")
        } finally {
            rtpSender = null
            isStreaming.set(false)
        }
    }

    /**
     * Complete teardown called when the WebRTC session or activity is destroyed.
     */
    @Synchronized
    override fun cleanup() {
        Log.d(TAG, "cleanup() invoked. Tearing down all microphone resources...")
        stopMicrophoneInternal()
    }

    /**
     * Returns true if the microphone is currently active and streaming.
     */
    override fun isActive(): Boolean = isStreaming.get()
}
