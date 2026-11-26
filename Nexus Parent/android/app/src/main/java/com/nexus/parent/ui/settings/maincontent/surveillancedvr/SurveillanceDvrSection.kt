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
 *
 * Hey friend! Matches Desktop's two-group hierarchy:
 * 1. Live Streaming Fidelity: Screen Mirror Quality, Preferred Camera Lens, Hardware Video Acceleration.
 * 2. Rolling DVR Timeline Buffer: Buffer Memory Retention (`15m`/`30m`/`60m`) and Clip Export Container (`MP4`/`WebM`).
 */
@Composable
fun SurveillanceDvrSection(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    isHardwareAccelReconnectPending: Boolean = false,
    onReconnectForHardwareAccel: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Live Streaming Fidelity") {
            ScreenQualitySetting(
                selectedQuality = settings.screenQuality,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
            CameraLensSetting(
                selectedLens = settings.preferredCameraLens,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
            HardwareAccelerationSetting(
                checked = settings.hardwareAcceleration,
                onUpdateSettings = onUpdateSettings,
                isReconnectPending = isHardwareAccelReconnectPending,
                onReconnectNow = onReconnectForHardwareAccel,
                onShowToast = onShowToast
            )
        }

        SettingsSectionGroup(title = "Rolling DVR Timeline Buffer") {
            DvrDurationSetting(
                selectedDuration = settings.dvrBufferDuration,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
            ClipFormatSetting(
                selectedFormat = settings.dvrClipFormat,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
        }
    }
}
