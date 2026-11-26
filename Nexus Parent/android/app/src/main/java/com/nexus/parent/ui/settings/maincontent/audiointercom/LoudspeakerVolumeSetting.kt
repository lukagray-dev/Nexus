package com.nexus.parent.ui.settings.maincontent.audiointercom

import androidx.compose.runtime.Composable
import com.nexus.parent.state.LoudspeakerVolume
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSegmentedRow

/**
 * LoudspeakerVolumeSetting.kt - Matches Desktop `audio-voice-intercom/loudspeaker-volume.ts`.
 */
@Composable
fun LoudspeakerVolumeSetting(
    selectedVolume: LoudspeakerVolume,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSegmentedRow(
        title = "Child Loudspeaker Target Volume",
        subtitle = "Volume level enforced on child device speaker during Push-to-Talk.",
        options = LoudspeakerVolume.entries,
        selected = selectedVolume,
        labelProvider = { it.label },
        onSelect = { volume ->
            onUpdateSettings { it.copy(loudspeakerVolume = volume) }
        }
    )
}
