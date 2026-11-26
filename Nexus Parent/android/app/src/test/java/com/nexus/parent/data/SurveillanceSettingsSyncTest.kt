package com.nexus.parent.data

import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.ScreenQuality
import com.nexus.parent.state.SettingsValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * SurveillanceSettingsSyncTest.kt - Verifies contract alignment between the 5 Surveillance & DVR
 * settings UI models, wire protocol builders, and Settings persistence.
 */
class SurveillanceSettingsSyncTest {

    @Test
    fun `display labels remain distinct from compact wire labels and persist cleanly`() {
        assertEquals("1080p", ScreenQuality.P1080.label)
        assertEquals("1080p @ 30fps", ScreenQuality.P1080.displayLabel)
        assertEquals("720p @ 30fps", ScreenQuality.P720.displayLabel)
        assertEquals("540p @ 24fps", ScreenQuality.P540.displayLabel)

        assertEquals("Rear", CameraLens.REAR.label)
        assertEquals("Rear Lens", CameraLens.REAR.settingsLabel)
        assertEquals("Front", CameraLens.FRONT.label)
        assertEquals("Front Lens", CameraLens.FRONT.settingsLabel)

        assertEquals("15m", DvrDuration.M15.label)
        assertEquals("15m (~140 MB)", DvrDuration.M15.displayLabel)
        assertEquals("30m (Default)", DvrDuration.M30.displayLabel)
        assertEquals("60m (~560 MB)", DvrDuration.M60.displayLabel)

        assertEquals("MP4 (H.264)", ClipFormat.MP4.displayLabel)
        assertEquals("WebM (VP8)", ClipFormat.WEBM.displayLabel)

        // Verify DeviceStorage preserves all 5 Surveillance & DVR choices across reloads
        val storage = DeviceStorage(InMemoryStorageStore())
        val updated = SettingsValues(
            screenQuality = ScreenQuality.P720,
            preferredCameraLens = CameraLens.FRONT,
            dvrBufferDuration = DvrDuration.M60,
            dvrClipFormat = ClipFormat.WEBM,
            hardwareAcceleration = false
        )
        storage.saveSettings(updated)
        assertEquals(updated, storage.loadSettings())
    }

    @Test
    fun `updating settings while disconnected syncs initial camera lens and screen quality immediately`() {
        val holder = ParentStateHolder()
        assertEquals(CameraLens.REAR, holder.uiState.value.camera.lens)
        assertEquals(ScreenQuality.P1080, holder.uiState.value.screen.quality)

        holder.updateSettings {
            it.copy(
                preferredCameraLens = CameraLens.FRONT,
                screenQuality = ScreenQuality.P540,
                dvrBufferDuration = DvrDuration.M15,
                dvrClipFormat = ClipFormat.WEBM,
                hardwareAcceleration = false
            )
        }

        assertEquals(CameraLens.FRONT, holder.uiState.value.camera.lens)
        assertEquals(ScreenQuality.P540, holder.uiState.value.screen.quality)
        assertEquals(DvrDuration.M15, holder.uiState.value.settings.dvrBufferDuration)
        assertEquals(ClipFormat.WEBM, holder.uiState.value.settings.dvrClipFormat)
        assertNotEquals(true, holder.uiState.value.settings.hardwareAcceleration)
    }
}
