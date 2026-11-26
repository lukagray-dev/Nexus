package com.nexus.child.components.mic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.AudioTrack
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Target loudspeaker volume policy applied on the child device during Parent Voice Intercom.
 *
 * - [MAX]: Forces STREAM_MUSIC & STREAM_VOICE_CALL to 100% hardware maximum.
 * - [HIGH]: Forces STREAM_MUSIC & STREAM_VOICE_CALL to 85% of hardware maximum.
 * - [NORMAL]: Preserves the child device's current volume levels without overriding them.
 */
enum class LoudspeakerVolumeMode(val wireValue: String) {
    MAX("max"),
    HIGH("high"),
    NORMAL("normal");

    companion object {
        fun fromCommandArg(raw: String?): LoudspeakerVolumeMode {
            return when (raw?.trim()?.lowercase()) {
                "high", "85" -> HIGH
                "normal", "preserve", "preserve_level" -> NORMAL
                else -> MAX
            }
        }
    }
}

/**
 * Computes the target Android AudioManager stream index for [mode] given [maxVolume],
 * returning `null` when [mode] is [LoudspeakerVolumeMode.NORMAL] (preserve existing level).
 */
internal fun computeTargetStreamVolume(maxVolume: Int, mode: LoudspeakerVolumeMode): Int? {
    if (maxVolume <= 0) return null
    return when (mode) {
        LoudspeakerVolumeMode.MAX -> maxVolume
        LoudspeakerVolumeMode.HIGH -> max(1, (maxVolume * 0.85f).roundToInt().coerceAtMost(maxVolume))
        LoudspeakerVolumeMode.NORMAL -> null
    }
}

/**
 * Interface defining Parent Audio Loudspeaker management.
 * Facilitates unit testing and separation of concerns.
 */
interface IParentAudioSpeakerManager {
    fun onRemoteAudioTrackAdded(track: AudioTrack)
    fun enableMaxVolumePlayback(): Boolean
    fun enableLoudspeakerPlayback(mode: LoudspeakerVolumeMode = getTargetVolumeMode()): Boolean =
        enableMaxVolumePlayback()
    fun setTargetVolumeMode(mode: LoudspeakerVolumeMode): Boolean = true
    fun getTargetVolumeMode(): LoudspeakerVolumeMode = LoudspeakerVolumeMode.MAX
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
 * 3. When Desktop un-mutes ("PARENT_AUDIO_UNMUTE[:max|high|normal]"), enableLoudspeakerPlayback():
 *    - Backs up the child user's current music and voice call volume levels.
 *    - Switches AudioManager mode to MODE_IN_COMMUNICATION and routes output to speakerphone.
 *      (Uses setCommunicationDevice on API 31+ and isSpeakerphoneOn for backwards compatibility).
 *    - Applies 100% (max), 85% (high), or preserves existing stream volume (normal).
 *    - Enables the remote AudioTrack so native WebRTC C++ AudioDeviceModule outputs
 *      the live speech out of the loud bottom speaker.
 * 4. When Desktop mutes ("PARENT_AUDIO_MUTE"), restoreNormalVolume():
 *    - Mutes the remote AudioTrack so no background hiss or noise leaks out.
 *    - Restores any overridden volume levels, speaker routing, and audio mode gracefully.
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

    @Volatile
    private var targetVolumeMode: LoudspeakerVolumeMode = LoudspeakerVolumeMode.MAX

    @Volatile
    private var didOverrideStreamVolumes: Boolean = false

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

    override fun getTargetVolumeMode(): LoudspeakerVolumeMode = targetVolumeMode

    /**
     * Updates the preferred loudspeaker target volume mode and applies it immediately
     * if intercom loudspeaker playback is currently active.
     */
    override fun setTargetVolumeMode(mode: LoudspeakerVolumeMode): Boolean {
        targetVolumeMode = mode
        Log.d(TAG, "Loudspeaker target volume mode updated to: $mode")
        if (!isVolumeBoosted) {
            return true
        }
        return applyStreamVolumesForMode(mode)
    }

    /**
     * Legacy entry point that engages loudspeaker playback at 100% MAX volume.
     */
    override fun enableMaxVolumePlayback(): Boolean {
        return enableLoudspeakerPlayback(LoudspeakerVolumeMode.MAX)
    }

    /**
     * Routes incoming parent speech to the external loudspeaker using the requested volume policy.
     *
     * @return true if hardware was configured successfully; false otherwise.
     */
    override fun enableLoudspeakerPlayback(mode: LoudspeakerVolumeMode): Boolean {
        Log.d(TAG, "Enabling loudspeaker playback for parent intercom (mode=$mode)...")
        targetVolumeMode = mode

        val am = audioManager
        if (am == null) {
            Log.e(TAG, "AudioManager is null! Cannot configure loudspeaker.")
            return false
        }

        try {
            // Only back up previous states if we are not already active
            if (!isVolumeBoosted) {
                savedMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                savedVoiceCallVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                savedAudioMode = am.mode
                @Suppress("DEPRECATION")
                savedSpeakerphoneOn = am.isSpeakerphoneOn
                didOverrideStreamVolumes = false

                Log.d(
                    TAG,
                    "Saved audio state: musicVol=$savedMusicVolume, voiceVol=$savedVoiceCallVolume, mode=$savedAudioMode, speaker=$savedSpeakerphoneOn"
                )
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

            applyStreamVolumesForMode(mode)
            isVolumeBoosted = true

            // Enable playback on the remote track
            remoteAudioTrack?.let { track ->
                track.setEnabled(true)
                Log.d(TAG, "Remote AudioTrack enabled for playback: id=${track.id()}")
            } ?: Log.w(TAG, "Remote AudioTrack not yet attached; playback will engage once track arrives.")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure loudspeaker playback: ${e.message}", e)
            return false
        }
    }

    /**
     * Applies stream volumes for [mode], or restores saved volumes when switching to [LoudspeakerVolumeMode.NORMAL].
     */
    private fun applyStreamVolumesForMode(mode: LoudspeakerVolumeMode): Boolean {
        val am = audioManager ?: return false
        return try {
            val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val targetMusic = computeTargetStreamVolume(maxMusicVol, mode)
            val targetVoice = computeTargetStreamVolume(maxVoiceVol, mode)

            if (targetMusic != null && targetVoice != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetMusic, 0)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVoice, 0)
                didOverrideStreamVolumes = true
                Log.d(TAG, "Loudspeaker configured ($mode): music=$targetMusic/$maxMusicVol, voice=$targetVoice/$maxVoiceVol")
            } else {
                // Preserve Level: if volumes were overridden earlier in this session, restore them
                if (didOverrideStreamVolumes) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceCallVolume, 0)
                    didOverrideStreamVolumes = false
                }
                Log.d(TAG, "Loudspeaker configured ($mode): preserving device stream volume levels")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply stream volume for $mode: ${e.message}", e)
            false
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
            didOverrideStreamVolumes = false
            return false
        }

        try {
            if (isVolumeBoosted) {
                if (didOverrideStreamVolumes) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceCallVolume, 0)
                    didOverrideStreamVolumes = false
                }

                // Restore speakerphone routing and audio mode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = savedSpeakerphoneOn
                }
                am.mode = savedAudioMode

                isVolumeBoosted = false
                Log.d(
                    TAG,
                    "Restored audio state: musicVol=$savedMusicVolume, voiceVol=$savedVoiceCallVolume, mode=$savedAudioMode, speaker=$savedSpeakerphoneOn"
                )
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore normal volume: ${e.message}", e)
            isVolumeBoosted = false
            didOverrideStreamVolumes = false
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
     * Returns whether loudspeaker playback is currently active.
     */
    override fun isMaxVolumeActive(): Boolean = isVolumeBoosted
}
