package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * HardwareAccelerationSetting.kt - Matches Desktop `surveillance-dvr/hardware-acceleration.ts`.
 */
@Composable
fun HardwareAccelerationSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsToggleRow(
        title = "Hardware Video Acceleration",
        subtitle = "Prefer H.264/HEVC MediaCodec + EglRenderer GPU decoding on new connections.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(hardwareAcceleration = value) }
        }
    )
}
