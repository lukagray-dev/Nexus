package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup

/**
 * SurveillanceDvrSection.kt - Tab 3 of Settings Console (`surveillance-dvr/surveillance-dvr.ts`).
 */
@Composable
fun SurveillanceDvrSection(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Live Video Capture") {
            ScreenQualitySetting(
                selectedQuality = settings.screenQuality,
                onUpdateSettings = onUpdateSettings
            )
            CameraLensSetting(
                selectedLens = settings.preferredCameraLens,
                onUpdateSettings = onUpdateSettings
            )
        }

        SettingsSectionGroup(title = "Rolling DVR & Export") {
            DvrDurationSetting(
                selectedDuration = settings.dvrBufferDuration,
                onUpdateSettings = onUpdateSettings
            )
            ClipFormatSetting(
                selectedFormat = settings.dvrClipFormat,
                onUpdateSettings = onUpdateSettings
            )
            HardwareAccelerationSetting(
                checked = settings.hardwareAcceleration,
                onUpdateSettings = onUpdateSettings
            )
        }
    }
}
