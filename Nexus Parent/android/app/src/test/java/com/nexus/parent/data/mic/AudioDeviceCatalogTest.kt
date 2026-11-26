package com.nexus.parent.data.mic

import android.media.AudioDeviceInfo
import com.nexus.parent.state.AudioDeviceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AudioDeviceCatalogTest.kt - Verifies stable audio device ID generation, Bluetooth profile
 * deduplication, display labels, and disconnected-accessory availability detection.
 */
class AudioDeviceCatalogTest {

    @Test
    fun `maps inputs and outputs into stable deduplicated options`() {
        val snapshot = AudioDeviceMapper.mapDescriptors(
            listOf(
                RawAudioDescriptor(AudioDeviceInfo.TYPE_BUILTIN_MIC, "Pixel", "", isSink = false),
                RawAudioDescriptor(AudioDeviceInfo.TYPE_USB_HEADSET, "Rode NT-USB", "", isSink = false),
                RawAudioDescriptor(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "WH-1000XM5", "AA:BB:CC:11:22:33", isSink = true),
                // A2DP descriptor for the same Bluetooth headset shares the same stable output key
                RawAudioDescriptor(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "WH-1000XM5", "AA:BB:CC:11:22:33", isSink = true),
                RawAudioDescriptor(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "Pixel", "", isSink = true),
                RawAudioDescriptor(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel", "", isSink = true),
                // Telephony sink should be excluded
                RawAudioDescriptor(AudioDeviceInfo.TYPE_TELEPHONY, "Modem", "", isSink = true)
            )
        )

        assertEquals(2, snapshot.inputs.size)
        assertEquals("builtin_mic", snapshot.inputs[0].id)
        assertEquals("Phone Microphone", snapshot.inputs[0].label)
        assertEquals("usb_mic:rode_nt_usb", snapshot.inputs[1].id)

        assertEquals(3, snapshot.outputs.size)
        assertEquals("bt_out:aa:bb:cc:11:22:33", snapshot.outputs[0].id)
        assertEquals("Bluetooth (WH-1000XM5)", snapshot.outputs[0].label)
        assertEquals("builtin_earpiece", snapshot.outputs[1].id)
        assertEquals("Phone Earpiece", snapshot.outputs[1].label)
        assertEquals("builtin_speaker", snapshot.outputs[2].id)
        assertEquals("Built-in Speaker", snapshot.outputs[2].label)
    }

    @Test
    fun `detects when saved external accessory is disconnected`() {
        val currentOutputs = listOf(
            AudioDeviceOption("builtin_earpiece", "Phone Earpiece"),
            AudioDeviceOption("builtin_speaker", "Built-in Speaker")
        )

        assertTrue(AudioDeviceMapper.isSavedDeviceAvailable("default", currentOutputs))
        assertTrue(AudioDeviceMapper.isSavedDeviceAvailable("builtin_earpiece", currentOutputs))
        assertFalse(AudioDeviceMapper.isSavedDeviceAvailable("bt_out:aa:bb:cc:11:22:33", currentOutputs))
    }
}
