package com.nexus.parent.data.mic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

data class AudioRouteApplyResult(
    val fellBackToDefault: Boolean = false
)

/**
 * ParentAudioRouteManager.kt - Parent phone VoIP audio output routing for Mic Listen & Speak.
 *
 * Hey friend! Unlike Desktop browsers (`setSinkId()`), Android routes WebRTC voice audio through
 * `AudioManager.MODE_IN_COMMUNICATION`. Supporting explicit user choice lets a parent listen
 * discreetly on the **Phone Earpiece** in public, broadcast loudly via **Built-in Speaker**,
 * or stay hands-free via **Bluetooth / Wired / USB Headsets**, with automatic fallback to System
 * Default (`Auto`) whenever a previously chosen external accessory is unplugged.
 */
class ParentAudioRouteManager(
    context: Context,
    private val catalog: AudioDeviceCatalog? = null
) {

    companion object {
        private const val TAG = "ParentAudioRouteMgr"
    }

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private var isEngaged = false
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn = false
    private var lastRequestedOutputId = AudioDeviceMapper.DEFAULT_DEVICE_ID

    @Synchronized
    fun engageVoiceRoute(outputDeviceId: String = AudioDeviceMapper.DEFAULT_DEVICE_ID): AudioRouteApplyResult {
        val am = audioManager ?: return AudioRouteApplyResult()
        if (!isEngaged) {
            savedMode = am.mode
            @Suppress("DEPRECATION")
            savedSpeakerphoneOn = am.isSpeakerphoneOn
            isEngaged = true
        }
        lastRequestedOutputId = outputDeviceId
        return applyOutputRouteInternal(am, outputDeviceId)
    }

    @Synchronized
    fun applyOutputRoute(outputDeviceId: String): AudioRouteApplyResult {
        lastRequestedOutputId = outputDeviceId
        if (!isEngaged) return AudioRouteApplyResult()
        val am = audioManager ?: return AudioRouteApplyResult()
        return applyOutputRouteInternal(am, outputDeviceId)
    }

    @Synchronized
    fun refreshActiveRouteIfNeeded(): AudioRouteApplyResult {
        if (!isEngaged) return AudioRouteApplyResult()
        val am = audioManager ?: return AudioRouteApplyResult()
        return applyOutputRouteInternal(am, lastRequestedOutputId)
    }

    @Synchronized
    fun releaseVoiceRoute() {
        if (!isEngaged) return
        val am = audioManager ?: return

        try {
            stopLegacyBluetoothSco(am)
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

    private fun applyOutputRouteInternal(am: AudioManager, rawDeviceId: String): AudioRouteApplyResult {
        val deviceId = rawDeviceId.trim().ifEmpty { AudioDeviceMapper.DEFAULT_DEVICE_ID }
        var fellBack = false

        runCatching {
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            when (deviceId) {
                AudioDeviceMapper.DEFAULT_DEVICE_ID -> applyAutoDefaultRoute(am)
                AudioDeviceMapper.BUILTIN_SPEAKER_ID -> applySpeakerRoute(am)
                AudioDeviceMapper.BUILTIN_EARPIECE_ID -> applyEarpieceRoute(am)
                else -> {
                    val resolved = catalog?.resolveOutputDevice(deviceId, preferVoiceProfile = true)
                    if (resolved == null) {
                        Log.w(TAG, "Saved output device '$deviceId' disconnected; falling back to default.")
                        fellBack = true
                        applyAutoDefaultRoute(am)
                    } else {
                        applySpecificExternalRoute(am, resolved)
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to configure audio route cleanly: ${err.message}")
        }

        return AudioRouteApplyResult(fellBackToDefault = fellBack)
    }

    private fun applyAutoDefaultRoute(am: AudioManager) {
        stopLegacyBluetoothSco(am)
        if (hasConnectedHeadsetOrBluetooth(am)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val external = am.availableCommunicationDevices.firstOrNull { isExternalCommType(it.type) }
                if (external != null) {
                    am.setCommunicationDevice(external)
                } else {
                    am.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        } else {
            applySpeakerRoute(am)
        }
    }

    private fun applySpeakerRoute(am: AudioManager) {
        stopLegacyBluetoothSco(am)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                am.setCommunicationDevice(speaker)
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
        }
    }

    private fun applyEarpieceRoute(am: AudioManager) {
        stopLegacyBluetoothSco(am)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece = am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            if (earpiece != null) {
                am.setCommunicationDevice(earpiece)
            } else {
                am.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
    }

    private fun applySpecificExternalRoute(am: AudioManager, target: AudioDeviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetKey = AudioDeviceMapper.buildStableDeviceId(
                RawAudioDescriptor(
                    type = target.type,
                    productName = target.productName?.toString().orEmpty(),
                    address = runCatching { target.address }.getOrDefault(""),
                    isSink = true
                )
            )
            val commTarget = am.availableCommunicationDevices.firstOrNull { dev ->
                dev.id == target.id ||
                    AudioDeviceMapper.buildStableDeviceId(
                        RawAudioDescriptor(
                            type = dev.type,
                            productName = dev.productName?.toString().orEmpty(),
                            address = runCatching { dev.address }.getOrDefault(""),
                            isSink = true
                        )
                    ) == targetKey
            } ?: target
            am.setCommunicationDevice(commTarget)
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            if (target.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                target.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            ) {
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
                @Suppress("DEPRECATION")
                runCatching { am.startBluetoothSco() }
            } else {
                stopLegacyBluetoothSco(am)
            }
        }
    }

    private fun stopLegacyBluetoothSco(am: AudioManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            if (am.isBluetoothScoOn) {
                @Suppress("DEPRECATION")
                runCatching { am.stopBluetoothSco() }
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = false
            }
        }
    }

    private fun isExternalCommType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
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
}
