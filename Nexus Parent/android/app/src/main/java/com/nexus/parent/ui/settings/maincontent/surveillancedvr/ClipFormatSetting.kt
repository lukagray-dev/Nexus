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
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsSegmentedRow(
        title = "DVR Clip Export Container",
        subtitle = "Format used when saving downloaded video clips via Save Clip.",
        options = ClipFormat.entries,
        selected = selectedFormat,
        labelProvider = { it.displayLabel },
        onSelect = { format ->
            onUpdateSettings { it.copy(dvrClipFormat = format) }
            onShowToast("DVR clip export container set to ${format.label}")
        }
    )
}
