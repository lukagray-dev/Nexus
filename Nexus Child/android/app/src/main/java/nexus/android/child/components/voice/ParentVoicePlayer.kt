package nexus.android.child.components.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Handles volume control when parent unmutes/mutes their audio
 * Maximizes volume when parent unmutes, restores when parent mutes
 */
class ParentVoicePlayer(context: Context
) {
    private var audioManager: AudioManager? = null
    private var originalVolume: Int = 0
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var isVolumeMaximized = false

    companion object {
        private const val TAG = "ParentVoicePlayer"
    }

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        Log.d(TAG, "🔊 ParentVoicePlayer initialized (local; independent of offer/connection)")
    }

    /**
     * Maximize volume when parent unmutes
     */
    fun startReceiving() {
        try {
            audioManager?.let { am ->
                // Only save original settings if not already maximized
                if (!isVolumeMaximized) {
                    originalVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    originalRingerMode = am.ringerMode
                    Log.d(TAG, "💾 Saved original volume: $originalVolume, ringer mode: $originalRingerMode")
                }
                
                // Set to maximum volume
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                
                // Unmute and set to normal mode
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                @Suppress("DEPRECATION")
                am.setStreamMute(AudioManager.STREAM_MUSIC, false)
                
                isVolumeMaximized = true
                
                Log.d(TAG, "🔊 Volume maximized: $maxVolume (original was $originalVolume)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to maximize volume: ${e.message}", e)
        }
    }

    /**
     * Restore volume when parent mutes
     */
    fun stopReceiving() {
        try {
            if (isVolumeMaximized) {
                restoreAudioSettings()
                isVolumeMaximized = false
                Log.d(TAG, "🔇 Volume restored to $originalVolume")
            } else {
                Log.w(TAG, "Volume not maximized, nothing to restore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restore volume: ${e.message}", e)
        }
    }

    /**
     * Restore original audio settings
     */
    private fun restoreAudioSettings() {
        try {
            audioManager?.let { am ->
                am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                am.ringerMode = originalRingerMode
                Log.d(TAG, "🔄 Audio settings restored: Volume=$originalVolume, Ringer mode=$originalRingerMode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to restore audio settings: ${e.message}", e)
        }
    }

    fun cleanup() {
        if (isVolumeMaximized) {
            stopReceiving()
        }
    }
}
