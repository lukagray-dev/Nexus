package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.runtime.Composable
import com.nexus.parent.state.ScreenQuality
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSegmentedRow

/**
 * ScreenQualitySetting.kt - Matches Desktop `surveillance-dvr/screen-quality.ts`.
 */
@Composable
fun ScreenQualitySetting(
    selectedQuality: ScreenQuality,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsSegmentedRow(
        title = "Screen Mirror Quality",
        subtitle = "Higher quality increases detail; lower saves child phone battery.",
        options = ScreenQuality.entries,
        selected = selectedQuality,
        labelProvider = { it.displayLabel },
        onSelect = { quality ->
            onUpdateSettings { it.copy(screenQuality = quality) }
            onShowToast("Screen mirror quality set to ${quality.displayLabel}")
        }
    )
}
