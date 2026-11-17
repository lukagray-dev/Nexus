package com.nexus.child.components.mic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.AudioTrack

/**
 * Interface defining Parent Audio Loudspeaker management.
 * Facilitates unit testing and separation of concerns.
 */
interface IParentAudioSpeakerManager {
    fun onRemoteAudioTrackAdded(track: AudioTrack)
    fun enableMaxVolumePlayback(): Boolean
    fun restoreNormalVolume(): Boolean
    fun cleanup()
    fun isMaxVolumeActive(): Boolean
}

/**
 * ParentAudioSpeakerManager manages loudspeaker audio routing and hardware volume boost
 * when the Desktop Parent speaks to the Child device.
 *
 * Hey friend! Here is how incoming voice playback works under the hood:
 * 1. WebRTC receives incoming RTP audio packets from Desktop Parent.
 * 2. WebRtcManager passes the remote AudioTrack to onRemoteAudioTrackAdded().
 * 3. When Desktop un-mutes ("PARENT_AUDIO_UNMUTE"), enableMaxVolumePlayback():
 *    - Backs up the child user's current music and voice call volume levels.
 *    - Switches AudioManager mode to MODE_IN_COMMUNICATION and routes output to speakerphone.
 *      (Uses setCommunicationDevice on API 31+ and isSpeakerphoneOn for backwards compatibility).
 *    - Cranks STREAM_MUSIC and STREAM_VOICE_CALL up to maximum hardware volume.
 *    - Enables the remote AudioTrack so native WebRTC C++ AudioDeviceModule outputs
 *      the live speech out of the loud bottom speaker.
 * 4. When Desktop mutes ("PARENT_AUDIO_MUTE"), restoreNormalVolume():
 *    - Mutes the remote AudioTrack so no background hiss or noise leaks out.
 *    - Restores the saved normal volume levels and audio mode gracefully.
 */
class ParentAudioSpeakerManager(
    private val context: Context
) : IParentAudioSpeakerManager {

    companion object {
        private const val TAG = "ParentAudioSpeakerMgr"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var remoteAudioTrack: AudioTrack? = null

    @Volatile
    private var isVolumeBoosted: Boolean = false

    // State preservation variables
    private var savedMusicVolume: Int = 0
    private var savedVoiceCallVolume: Int = 0
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn: Boolean = false

    /**
     * Stores the incoming WebRTC remote AudioTrack from the parent connection.
     * Sets its enabled status according to whether voice playback is currently active.
     */
    override fun onRemoteAudioTrackAdded(track: AudioTrack) {
        Log.d(TAG, "Remote audio track attached: id=${track.id()}")
        remoteAudioTrack = track
        // Keep track enabled only if parent is actively speaking
        track.setEnabled(isVolumeBoosted)
    }

    /**
     * Boosts volume to maximum and routes audio to loudspeaker.
     *
     * @return true if hardware was configured successfully; false otherwise.
     */
    override fun enableMaxVolumePlayback(): Boolean {
        Log.d(TAG, "Enabling maximum volume loudspeaker playback for parent intercom...")

        val am = audioManager
        if (am == null) {
            Log.e(TAG, "AudioManager is null! Cannot configure loudspeaker.")
            return false
        }

        try {
            // Only back up previous states if we are not already boosted
            if (!isVolumeBoosted) {
                savedMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                savedVoiceCallVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                savedAudioMode = am.mode
                @Suppress("DEPRECATION")
                savedSpeakerphoneOn = am.isSpeakerphoneOn

                Log.d(TAG, "Saved audio state: musicVol=$savedMusicVolume, voiceVol=$savedVoiceCallVolume, mode=$savedAudioMode, speaker=$savedSpeakerphoneOn")
            }

            // Set communication mode to take control of audio routing
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // Route audio through the loud external speaker (not earpiece)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = am.availableCommunicationDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    am.setCommunicationDevice(speakerDevice)
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }

            // Maximize volume on both music and voice call streams
            val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)

            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceVol, 0)

            isVolumeBoosted = true

            // Enable playback on the remote track
            remoteAudioTrack?.let { track ->
                track.setEnabled(true)
                Log.d(TAG, "Remote AudioTrack enabled for playback: id=${track.id()}")
            } ?: Log.w(TAG, "Remote AudioTrack not yet attached; playback will engage once track arrives.")

            Log.d(TAG, "Loudspeaker configured at MAX volume (music=$maxMusicVol, voice=$maxVoiceVol)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure maximum volume loudspeaker: ${e.message}", e)
            return false
        }
    }

    /**
     * Restores normal volume levels, speakerphone, and audio mode.
     * Mutes incoming audio track.
     *
     * @return true if restoration completed cleanly; false otherwise.
     */
    override fun restoreNormalVolume(): Boolean {
        Log.d(TAG, "Restoring normal volume levels and muting remote audio track...")

        // Disable track first so audio stops playing immediately
        remoteAudioTrack?.setEnabled(false)

        val am = audioManager
        if (am == null) {
            isVolumeBoosted = false
            return false
        }

        try {
            if (isVolumeBoosted) {
                // Restore saved volume levels
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceCallVolume, 0)

                // Restore speakerphone routing and audio mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = savedSpeakerphoneOn
                }
                am.mode = savedAudioMode

                isVolumeBoosted = false
                Log.d(TAG, "Restored audio state: musicVol=$savedMusicVolume, voiceVol=$savedVoiceCallVolume, mode=$savedAudioMode, speaker=$savedSpeakerphoneOn")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore normal volume: ${e.message}", e)
            isVolumeBoosted = false
            return false
        }
    }

    /**
     * Releases references and restores normal volume if currently boosted.
     */
    override fun cleanup() {
        Log.d(TAG, "cleanup() invoked on ParentAudioSpeakerManager.")
        if (isVolumeBoosted) {
            restoreNormalVolume()
        }
        remoteAudioTrack = null
    }

    /**
     * Returns whether max volume boost is currently active.
     */
    override fun isMaxVolumeActive(): Boolean = isVolumeBoosted
}
