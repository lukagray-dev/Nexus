package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * AskBeforeDownloadSetting.kt - Matches Desktop `general-system/ask-before-download.ts`.
 */
@Composable
fun AskBeforeDownloadSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsToggleRow(
        title = "Ask Before Download",
        subtitle = "Prompt for folder selection before saving remote files or DVR clips.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(askBeforeDownload = value) }
        }
    )
}
