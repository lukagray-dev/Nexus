package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.runtime.Composable
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSegmentedRow

/**
 * ClipFormatSetting.kt - Matches Desktop `surveillance-dvr/clip-format.ts`.
 */
@Composable
fun ClipFormatSetting(
    selectedFormat: ClipFormat,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSegmentedRow(
        title = "Export Clip Format",
        subtitle = "Container format used by Save Clip exports.",
        options = ClipFormat.entries,
        selected = selectedFormat,
        labelProvider = { it.label },
        onSelect = { format ->
            onUpdateSettings { it.copy(dvrClipFormat = format) }
        }
    )
}
