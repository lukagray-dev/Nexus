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
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsSegmentedRow(
        title = "Rolling DVR Memory Retention",
        subtitle = "Timeline depth retained during live Camera and Screen sessions for instant rewinds.",
        options = DvrDuration.entries,
        selected = selectedDuration,
        labelProvider = { it.displayLabel },
        onSelect = { duration ->
            onUpdateSettings { it.copy(dvrBufferDuration = duration) }
            onShowToast("Rolling DVR retention set to ${duration.label.replace("m", " minutes")}")
        }
    )
}
