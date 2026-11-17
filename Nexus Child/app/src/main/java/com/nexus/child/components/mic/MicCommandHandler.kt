package com.nexus.child.components.mic

import android.util.Log

/**
 * MicCommandHandler processes DataChannel control commands for:
 * 1. Child-to-Parent Microphone monitoring (listening to child environment).
 * 2. Parent-to-Child Voice Intercom (parent speaking through child loudspeaker at max volume).
 *
 * Hey friend! Here is how the bidirectional command cycle works:
 *
 * --- CHILD MICROPHONE MONITORING ---
 * 1. Desktop Parent sends "MIC_ON" across 'nexus-control' DataChannel.
 * 2. We verify runtime audio recording permissions and start MicrophoneController.
 * 3. We trigger WebRTC renegotiation (SDP Offer role reversal) to start sending audio.
 * 4. Desktop replies with "RENEGOTIATION_COMPLETE", and we send "MIC_STARTED".
 * 5. For "MIC_OFF", audio tracks are disposed, and "MIC_STOPPED" confirms shutdown.
 *
 * --- PARENT VOICE INTERCOM ---
 * 1. Desktop Parent clicks Speak ("PARENT_AUDIO_UNMUTE" or alias "PARENT_MIC_ON").
 * 2. ParentAudioSpeakerManager boosts volume to MAX, enables speakerphone, and un-mutes
 *    the remote AudioTrack.
 * 3. We reply with "PARENT_AUDIO_UNMUTED" so Desktop flips loading spinner to active Speak state.
 * 4. When Parent clicks again ("PARENT_AUDIO_MUTE" or alias "PARENT_MIC_OFF"),
 *    ParentAudioSpeakerManager restores previous normal volumes and mutes remote track.
 * 5. We reply with "PARENT_AUDIO_MUTED" so Desktop returns to idle state.
 */
class MicCommandHandler(
    private val micController: IMicrophoneController,
    private val parentAudioSpeakerManager: IParentAudioSpeakerManager,
    private val renegotiationTrigger: (onComplete: () -> Unit) -> Unit,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "MicCommandHandler"

        // Child Mic Monitoring Commands & Responses
        const val CMD_MIC_ON = "MIC_ON"
        const val CMD_MIC_OFF = "MIC_OFF"
        const val CMD_RENEGOTIATION_COMPLETE = "RENEGOTIATION_COMPLETE"

        const val RESP_MIC_STARTED = "MIC_STARTED"
        const val RESP_MIC_STOPPED = "MIC_STOPPED"
        const val RESP_MIC_ERROR_PREFIX = "MIC_ERROR: "

        // Parent Voice Intercom Commands & Responses
        const val CMD_PARENT_AUDIO_UNMUTE = "PARENT_AUDIO_UNMUTE"
        const val CMD_PARENT_MIC_ON = "PARENT_MIC_ON"
        const val CMD_PARENT_AUDIO_MUTE = "PARENT_AUDIO_MUTE"
        const val CMD_PARENT_MIC_OFF = "PARENT_MIC_OFF"

        const val RESP_PARENT_AUDIO_UNMUTED = "PARENT_AUDIO_UNMUTED"
        const val RESP_PARENT_AUDIO_MUTED = "PARENT_AUDIO_MUTED"
        const val RESP_PARENT_AUDIO_ERROR_PREFIX = "PARENT_AUDIO_ERROR: "
    }

    // Stores the pending callback invoked when Desktop sends RENEGOTIATION_COMPLETE
    @Volatile
    private var pendingRenegotiationCallback: (() -> Unit)? = null

    /**
     * Inspects an incoming DataChannel message and executes the corresponding action
     * if it matches a microphone or intercom command.
     *
     * @param command Raw message received over 'nexus-control'
     * @return true if the command was recognized and handled; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_MIC_ON -> {
                handleMicOn()
                true
            }
            CMD_MIC_OFF -> {
                handleMicOff()
                true
            }
            CMD_RENEGOTIATION_COMPLETE -> {
                handleRenegotiationComplete()
                true
            }
            CMD_PARENT_AUDIO_UNMUTE, CMD_PARENT_MIC_ON -> {
                handleParentAudioUnmute()
                true
            }
            CMD_PARENT_AUDIO_MUTE, CMD_PARENT_MIC_OFF -> {
                handleParentAudioMute()
                true
            }
            else -> false
        }
    }

    /**
     * Handles starting the child microphone and beginning renegotiation.
     */
    private fun handleMicOn() {
        Log.d(TAG, "Processing command: $CMD_MIC_ON")

        // Step 1: Ensure RECORD_AUDIO permission is granted
        if (!micController.hasRecordAudioPermission()) {
            Log.e(TAG, "Microphone permission is missing on Child device!")
            dataSender("$RESP_MIC_ERROR_PREFIX Missing microphone permission")
            return
        }

        // Step 2: If already active, immediately acknowledge with MIC_STARTED
        if (micController.isActive()) {
            Log.d(TAG, "Microphone is already active. Acknowledging with $RESP_MIC_STARTED.")
            dataSender(RESP_MIC_STARTED)
            return
        }

        // Step 3: Initialize native audio pipeline and attach AudioTrack to PeerConnection
        val started = micController.startMicrophone()
        if (!started) {
            Log.e(TAG, "Failed to start microphone hardware!")
            dataSender("$RESP_MIC_ERROR_PREFIX Failed to initialize audio pipeline")
            return
        }

        // Step 4: Register completion callback and initiate role-reversal renegotiation
        pendingRenegotiationCallback = {
            Log.d(TAG, "Renegotiation fully confirmed. Transmitting $RESP_MIC_STARTED...")
            dataSender(RESP_MIC_STARTED)
        }

        try {
            Log.d(TAG, "Triggering WebRTC renegotiation offer creation...")
            renegotiationTrigger {
                // If the trigger completes synchronously or locally, check callback
                pendingRenegotiationCallback?.invoke()
                pendingRenegotiationCallback = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger renegotiation: ${e.message}", e)
            micController.stopMicrophone()
            pendingRenegotiationCallback = null
            dataSender("$RESP_MIC_ERROR_PREFIX Renegotiation failure: ${e.message}")
        }
    }

    /**
     * Handles stopping the child microphone and beginning renegotiation without the audio track.
     */
    private fun handleMicOff() {
        Log.d(TAG, "Processing command: $CMD_MIC_OFF")

        // If already idle, immediately acknowledge with MIC_STOPPED
        if (!micController.isActive()) {
            Log.d(TAG, "Microphone is already idle. Acknowledging with $RESP_MIC_STOPPED.")
            dataSender(RESP_MIC_STOPPED)
            return
        }

        // Step 1: Stop microphone and release native AudioRecord hardware driver
        val stopped = micController.stopMicrophone()
        if (!stopped) {
            Log.e(TAG, "Failed to stop microphone cleanly.")
        }

        // Step 2: Register completion callback and initiate role-reversal renegotiation
        pendingRenegotiationCallback = {
            Log.d(TAG, "Renegotiation fully confirmed. Transmitting $RESP_MIC_STOPPED...")
            dataSender(RESP_MIC_STOPPED)
        }

        try {
            Log.d(TAG, "Triggering WebRTC renegotiation offer creation without audio track...")
            renegotiationTrigger {
                pendingRenegotiationCallback?.invoke()
                pendingRenegotiationCallback = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger renegotiation for mic off: ${e.message}", e)
            pendingRenegotiationCallback = null
            dataSender(RESP_MIC_STOPPED) // Best-effort fallback
        }
    }

    /**
     * Invoked when Desktop Parent signals RENEGOTIATION_COMPLETE over DataChannel.
     */
    private fun handleRenegotiationComplete() {
        Log.d(TAG, "Received $CMD_RENEGOTIATION_COMPLETE from Parent.")
        val callback = pendingRenegotiationCallback
        pendingRenegotiationCallback = null
        callback?.invoke()
    }

    /**
     * Handles un-muting parent audio: activates maximum volume loudspeaker output
     * and sends confirmation to parent.
     */
    private fun handleParentAudioUnmute() {
        Log.d(TAG, "Processing command: $CMD_PARENT_AUDIO_UNMUTE")
        val success = parentAudioSpeakerManager.enableMaxVolumePlayback()
        if (success) {
            Log.d(TAG, "Loudspeaker playback ready. Transmitting $RESP_PARENT_AUDIO_UNMUTED...")
            dataSender(RESP_PARENT_AUDIO_UNMUTED)
        } else {
            Log.e(TAG, "Failed to enable max volume playback!")
            dataSender("$RESP_PARENT_AUDIO_ERROR_PREFIX Failed to configure loudspeaker")
        }
    }

    /**
     * Handles muting parent audio: restores normal device volume and audio routing,
     * and sends confirmation to parent.
     */
    private fun handleParentAudioMute() {
        Log.d(TAG, "Processing command: $CMD_PARENT_AUDIO_MUTE")
        val success = parentAudioSpeakerManager.restoreNormalVolume()
        if (success) {
            Log.d(TAG, "Normal volume restored. Transmitting $RESP_PARENT_AUDIO_MUTED...")
            dataSender(RESP_PARENT_AUDIO_MUTED)
        } else {
            Log.w(TAG, "Volume restoration completed with warnings. Still acknowledging $RESP_PARENT_AUDIO_MUTED.")
            dataSender(RESP_PARENT_AUDIO_MUTED)
        }
    }

    /**
     * Invoked when the WebRTC peer connection or DataChannel disconnects.
     * Automatically terminates microphone capture and restores device audio volume
     * so the child's microphone does not remain running and loudspeaker does not stay at max volume.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Executing fail-safe audio and microphone teardown...")
        pendingRenegotiationCallback = null

        if (micController.isActive()) {
            Log.d(TAG, "Child microphone was actively streaming during disconnect. Stopping hardware...")
            micController.stopMicrophone()
        }

        if (parentAudioSpeakerManager.isMaxVolumeActive()) {
            Log.d(TAG, "Loudspeaker max volume boost was active during disconnect. Restoring normal volume...")
            parentAudioSpeakerManager.restoreNormalVolume()
        }
    }

    /**
     * Releases microphone and speaker resources, cancelling any pending callbacks.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() invoked on MicCommandHandler.")
        pendingRenegotiationCallback = null
        micController.cleanup()
        parentAudioSpeakerManager.cleanup()
    }
}
