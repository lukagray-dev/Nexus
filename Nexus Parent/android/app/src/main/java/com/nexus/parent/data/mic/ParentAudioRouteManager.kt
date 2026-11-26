package com.nexus.parent.data.mic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * ParentAudioRouteManager.kt - Clean Android VoIP audio routing during Mic Listen & Broadcast Intercom.
 *
 * Hey friend! On Android, WebRTC runs in `MODE_IN_COMMUNICATION`, which defaults to the quiet phone
 * earpiece unless told otherwise. This helper automatically engages the phone's loudspeaker when
 * no wired headset or Bluetooth audio device is attached (and respects connected headsets cleanly
 * to prevent echo loops), restoring the phone's prior audio mode as soon as Mic & Speak stop.
 */
class ParentAudioRouteManager(context: Context) {

    companion object {
        private const val TAG = "ParentAudioRouteMgr"
    }

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private var isEngaged = false
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn = false

    @Synchronized
    fun engageVoiceRoute() {
        val am = audioManager ?: return
        if (!isEngaged) {
            savedMode = am.mode
            @Suppress("DEPRECATION")
            savedSpeakerphoneOn = am.isSpeakerphoneOn
            isEngaged = true
        }

        runCatching {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            val hasExternalHeadset = hasConnectedHeadsetOrBluetooth(am)
            if (hasExternalHeadset) {
                Log.d(TAG, "External headset/Bluetooth detected; keeping headset audio route.")
                setSpeakerphoneEnabled(am, enabled = false)
            } else {
                Log.d(TAG, "No headset detected; routing WebRTC audio to built-in speaker.")
                setSpeakerphoneEnabled(am, enabled = true)
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to configure audio route cleanly: ${err.message}")
        }
    }

    @Synchronized
    fun releaseVoiceRoute() {
        if (!isEngaged) return
        val am = audioManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = savedSpeakerphoneOn
            }
            am.mode = savedMode
        } catch (err: Exception) {
            Log.w(TAG, "Unable to restore audio route cleanly: ${err.message}")
        } finally {
            isEngaged = false
        }
    }

    private fun hasConnectedHeadsetOrBluetooth(am: AudioManager): Boolean {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
    }

    private fun setSpeakerphoneEnabled(am: AudioManager, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                }
            } else {
                am.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enabled
        }
    }
}
