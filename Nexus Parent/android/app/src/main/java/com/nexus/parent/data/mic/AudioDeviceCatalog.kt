package com.nexus.parent.data.mic

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nexus.parent.state.AudioDeviceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RawAudioDescriptor(
    val type: Int,
    val productName: String,
    val address: String,
    val isSink: Boolean
)

data class AudioDevicesSnapshot(
    val inputs: List<AudioDeviceOption> = emptyList(),
    val outputs: List<AudioDeviceOption> = emptyList()
)

/**
 * AudioDeviceMapper - Pure-Kotlin device normalization, deduplication, and stable-ID resolver.
 *
 * Hey friend! Android's `AudioDeviceInfo.id` resets every reboot or USB/Bluetooth replug.
 * Instead of saving ephemeral integers, we build deterministic keys such as `"builtin_mic"`,
 * `"builtin_earpiece"`, `"builtin_speaker"`, `"wired:..."`, `"usb:..."`, and `"bt:..."` so saved
 * headphones or microphones reconnect automatically and degrade safely to `"default"` if absent.
 */
object AudioDeviceMapper {

    const val DEFAULT_DEVICE_ID = "default"
    const val BUILTIN_MIC_ID = "builtin_mic"
    const val BUILTIN_EARPIECE_ID = "builtin_earpiece"
    const val BUILTIN_SPEAKER_ID = "builtin_speaker"

    fun isSupportedInputType(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    private fun isBluetoothAudioType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    fun isSupportedOutputType(type: Int): Boolean {
        if (isBluetoothAudioType(type)) return true
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> true
            else -> false
        }
    }

    fun buildStableDeviceId(raw: RawAudioDescriptor): String {
        val cleanName = raw.productName.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val cleanAddress = raw.address.trim().lowercase()
            .replace(Regex("[^a-z0-9:]+"), "")

        if (!raw.isSink) {
            if (isBluetoothAudioType(raw.type)) {
                val token = cleanAddress.ifBlank { cleanName.ifBlank { "headset" } }
                return "bt_mic:$token"
            }
            return when (raw.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> BUILTIN_MIC_ID
                AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    "wired_mic:${cleanName.ifBlank { "headset" }}"
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET ->
                    "usb_mic:${cleanName.ifBlank { "audio" }}"
                else -> "input_${raw.type}:${cleanName.ifBlank { "device" }}"
            }
        }

        if (isBluetoothAudioType(raw.type)) {
            val token = cleanAddress.ifBlank { cleanName.ifBlank { "device" } }
            return "bt_out:$token"
        }
        return when (raw.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> BUILTIN_SPEAKER_ID
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> BUILTIN_EARPIECE_ID
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                "wired_out:${cleanName.ifBlank { "headset" }}"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET ->
                "usb_out:${cleanName.ifBlank { "audio" }}"
            AudioDeviceInfo.TYPE_HEARING_AID ->
                "ha_out:${cleanAddress.ifBlank { cleanName.ifBlank { "device" } }}"
            else -> "output_${raw.type}:${cleanName.ifBlank { "device" }}"
        }
    }

    fun buildDisplayLabel(raw: RawAudioDescriptor): String {
        val product = raw.productName.trim()
        if (!raw.isSink) {
            if (isBluetoothAudioType(raw.type)) {
                return if (product.isNotBlank()) "Bluetooth Mic ($product)" else "Bluetooth Headset Mic"
            }
            return when (raw.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone Microphone"
                AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    if (product.isNotBlank()) "Wired Headset Mic ($product)" else "Wired Headset Mic"
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET ->
                    if (product.isNotBlank()) "USB Mic ($product)" else "USB Microphone"
                else -> product.ifBlank { "External Microphone" }
            }
        }

        if (isBluetoothAudioType(raw.type)) {
            return if (product.isNotBlank()) "Bluetooth ($product)" else "Bluetooth Audio"
        }
        return when (raw.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                if (product.isNotBlank()) "Wired Headphones ($product)" else "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET ->
                if (product.isNotBlank()) "USB Audio ($product)" else "USB Audio Output"
            AudioDeviceInfo.TYPE_HEARING_AID ->
                if (product.isNotBlank()) "Hearing Aid ($product)" else "Hearing Aid"
            else -> product.ifBlank { "External Output" }
        }
    }

    fun buildKindLabel(type: Int): String {
        if (isBluetoothAudioType(type)) return "Bluetooth"
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Internal"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Loudspeaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
            else -> "Audio"
        }
    }

    fun mapDescriptors(descriptors: List<RawAudioDescriptor>): AudioDevicesSnapshot {
        val inputs = LinkedHashMap<String, AudioDeviceOption>()
        val outputs = LinkedHashMap<String, AudioDeviceOption>()

        for (desc in descriptors) {
            if (!desc.isSink && isSupportedInputType(desc.type)) {
                val id = buildStableDeviceId(desc)
                inputs.putIfAbsent(
                    id,
                    AudioDeviceOption(
                        id = id,
                        label = buildDisplayLabel(desc),
                        kindLabel = buildKindLabel(desc.type)
                    )
                )
            } else if (desc.isSink && isSupportedOutputType(desc.type)) {
                val id = buildStableDeviceId(desc)
                outputs.putIfAbsent(
                    id,
                    AudioDeviceOption(
                        id = id,
                        label = buildDisplayLabel(desc),
                        kindLabel = buildKindLabel(desc.type)
                    )
                )
            }
        }
        return AudioDevicesSnapshot(
            inputs = inputs.values.toList(),
            outputs = outputs.values.toList()
        )
    }

    fun isSavedDeviceAvailable(savedId: String?, options: List<AudioDeviceOption>): Boolean {
        val normalized = savedId?.trim().orEmpty()
        if (normalized.isEmpty() || normalized == DEFAULT_DEVICE_ID) return true
        return options.any { it.id == normalized }
    }
}

/**
 * AudioDeviceCatalog.kt - Enumerates live Android microphones & output speakers and emits
 * instant updates whenever headsets, Bluetooth audio, or USB interfaces connect/disconnect.
 */
class AudioDeviceCatalog(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _snapshot = MutableStateFlow(AudioDevicesSnapshot())
    val snapshot: StateFlow<AudioDevicesSnapshot> = _snapshot.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    init {
        refresh()
        audioManager?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun refresh(): AudioDevicesSnapshot {
        val am = audioManager ?: return AudioDevicesSnapshot()
        val rawInputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.toRawDescriptor(isSink = false) }
        val rawOutputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toRawDescriptor(isSink = true) }
        val next = AudioDeviceMapper.mapDescriptors(rawInputs + rawOutputs)
        _snapshot.value = next
        return next
    }

    fun resolveInputDevice(deviceId: String): AudioDeviceInfo? {
        val normalized = deviceId.trim()
        if (normalized.isEmpty() || normalized == AudioDeviceMapper.DEFAULT_DEVICE_ID) return null
        val am = audioManager ?: return null
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { info ->
                AudioDeviceMapper.buildStableDeviceId(info.toRawDescriptor(isSink = false)) == normalized
            }
    }

    fun resolveOutputDevice(deviceId: String, preferVoiceProfile: Boolean): AudioDeviceInfo? {
        val normalized = deviceId.trim()
        if (normalized.isEmpty() || normalized == AudioDeviceMapper.DEFAULT_DEVICE_ID) return null
        val am = audioManager ?: return null
        val candidates = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { info ->
                AudioDeviceMapper.buildStableDeviceId(info.toRawDescriptor(isSink = true)) == normalized
            }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // Bluetooth headsets often expose both SCO (telephony/mic) and A2DP (high-quality media).
        return if (preferVoiceProfile) {
            candidates.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            } ?: candidates.first()
        } else {
            candidates.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
            } ?: candidates.first()
        }
    }

    fun isInputAvailable(deviceId: String): Boolean {
        return AudioDeviceMapper.isSavedDeviceAvailable(deviceId, _snapshot.value.inputs)
    }

    fun isOutputAvailable(deviceId: String): Boolean {
        return AudioDeviceMapper.isSavedDeviceAvailable(deviceId, _snapshot.value.outputs)
    }

    fun release() {
        runCatching { audioManager?.unregisterAudioDeviceCallback(deviceCallback) }
    }

    private fun AudioDeviceInfo.toRawDescriptor(isSink: Boolean): RawAudioDescriptor {
        val safeAddress = runCatching { address }.getOrDefault("")
        return RawAudioDescriptor(
            type = type,
            productName = productName?.toString().orEmpty(),
            address = safeAddress,
            isSink = isSink
        )
    }
}
