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
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsToggleRow(
        title = "Hardware Noise Suppression & AEC",
        subtitle = "Acoustic echo cancellation, background noise reduction, and auto-gain on parent microphone.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(noiseSuppression = value) }
            onShowToast(
                if (value) {
                    "Hardware noise suppression and AEC enabled"
                } else {
                    "Hardware noise suppression and AEC disabled"
                }
            )
        }
    )
}
