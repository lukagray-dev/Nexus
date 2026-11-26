package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.runtime.Composable
import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSegmentedRow

/**
 * CameraLensSetting.kt - Matches Desktop `surveillance-dvr/camera-lens.ts`.
 */
@Composable
fun CameraLensSetting(
    selectedLens: CameraLens,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSegmentedRow(
        title = "Preferred Default Camera Lens",
        subtitle = "Sensor selected initially when Camera Feed starts.",
        options = CameraLens.entries,
        selected = selectedLens,
        labelProvider = { it.label },
        onSelect = { lens ->
            onUpdateSettings { it.copy(preferredCameraLens = lens) }
        }
    )
}
