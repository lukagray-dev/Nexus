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
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsSegmentedRow(
        title = "Preferred Default Camera Lens",
        subtitle = "Initial optical sensor engaged when starting live camera surveillance.",
        options = CameraLens.entries,
        selected = selectedLens,
        labelProvider = { it.settingsLabel },
        onSelect = { lens ->
            onUpdateSettings { it.copy(preferredCameraLens = lens) }
            onShowToast("Preferred default camera lens set to ${lens.settingsLabel}")
        }
    )
}
