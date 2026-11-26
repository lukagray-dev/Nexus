package com.nexus.parent.ui.settings.maincontent.audiointercom

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * NoiseSuppressionSetting.kt - Matches Desktop `audio-voice-intercom/noise-suppression.ts`.
 */
@Composable
fun NoiseSuppressionSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsToggleRow(
        title = "Noise Suppression & Echo Cancellation",
        subtitle = "Filters ambient feedback during two-way voice intercom sessions.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(noiseSuppression = value) }
        }
    )
}
