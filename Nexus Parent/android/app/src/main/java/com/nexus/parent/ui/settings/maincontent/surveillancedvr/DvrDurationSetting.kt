package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.runtime.Composable
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSegmentedRow

/**
 * DvrDurationSetting.kt - Matches Desktop `surveillance-dvr/dvr-duration.ts`.
 */
@Composable
fun DvrDurationSetting(
    selectedDuration: DvrDuration,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSegmentedRow(
        title = "Rolling DVR Buffer Duration",
        subtitle = "Time-shift history window retained during live Camera and Screen sessions.",
        options = DvrDuration.entries,
        selected = selectedDuration,
        labelProvider = { it.label },
        onSelect = { duration ->
            onUpdateSettings { it.copy(dvrBufferDuration = duration) }
        }
    )
}
