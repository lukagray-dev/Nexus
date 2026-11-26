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
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSegmentedRow(
        title = "Screen Mirror Quality",
        subtitle = "Resolution preset requested from child MediaProjection stream.",
        options = ScreenQuality.entries,
        selected = selectedQuality,
        labelProvider = { it.label },
        onSelect = { quality ->
            onUpdateSettings { it.copy(screenQuality = quality) }
        }
    )
}
