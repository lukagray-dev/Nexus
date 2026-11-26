package com.nexus.parent.data

import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.state.LoudspeakerVolume
import com.nexus.parent.state.ScreenQuality
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeviceStorageTest.kt - Verifies device history deduplication, cached telemetry persistence,
 * and SettingsValues round-trip serialization using `InMemoryStorageStore`.
 */
class DeviceStorageTest {

    @Test
    fun `saves multiple paired devices deduplicated in most-recent order`() {
        val storage = DeviceStorage(InMemoryStorageStore())

        val dev1 = DeviceRecord(
            rawId = "111122223333",
            formattedId = "1111 2222 3333",
            name = "Kid Phone A",
            model = "Pixel 7",
            lastSyncedLabel = "Online",
            lastConnectedAt = 1000L,
            telemetry = DeviceTelemetry(batteryPercent = 55, isStealthMode = true)
        )
        val dev2 = DeviceRecord(
            rawId = "444455556666",
            formattedId = "4444 5555 6666",
            name = "Kid Tablet B",
            model = "Galaxy Tab",
            lastSyncedLabel = "Online",
            lastConnectedAt = 2000L,
            telemetry = DeviceTelemetry(batteryPercent = 92)
        )

        storage.savePairedDevice(dev1)
        storage.savePairedDevice(dev2)
        // Update dev1 so it jumps to index 0 without creating duplicates
        val dev1Updated = dev1.copy(
            lastConnectedAt = 3000L,
            telemetry = dev1.telemetry.copy(batteryPercent = 49)
        )
        val history = storage.savePairedDevice(dev1Updated)

        assertEquals(2, history.size)
        assertEquals("111122223333", history[0].rawId)
        assertEquals(49, history[0].telemetry.batteryPercent)
        assertTrue(history[0].telemetry.isStealthMode)
        assertEquals("444455556666", history[1].rawId)
        assertEquals("111122223333", storage.loadLastConnectedDevice()?.rawId)

        storage.clearPairedDevicesHistory()
        assertNull(storage.loadLastConnectedDevice())
        assertTrue(storage.loadPairedDevicesHistory().isEmpty())
    }

    @Test
    fun `persists and restores all SettingsValues fields deterministically`() {
        val storage = DeviceStorage(InMemoryStorageStore())
        val custom = SettingsValues(
            defaultVoicePreset = VoicePresetId.ROBOT_FEMALE,
            noiseSuppression = false,
            loudspeakerVolume = LoudspeakerVolume.NORMAL,
            screenQuality = ScreenQuality.P540,
            preferredCameraLens = CameraLens.FRONT,
            dvrBufferDuration = DvrDuration.M60,
            dvrClipFormat = ClipFormat.WEBM,
            hardwareAcceleration = false,
            downloadsDirectoryLabel = "Internal/Documents/Evidence",
            downloadsTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FEvidence",
            askBeforeDownload = true,
            autoConnectLastDevice = true,
            keepScreenAwake = false,
            alertLowBattery = false,
            alertChildDisconnected = false,
            alertChildActiveCall = false
        )

        storage.saveSettings(custom)
        val loaded = storage.loadSettings()

        assertEquals(custom, loaded)
    }
}
