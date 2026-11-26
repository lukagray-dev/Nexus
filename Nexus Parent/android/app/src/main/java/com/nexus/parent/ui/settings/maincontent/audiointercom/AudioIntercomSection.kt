package com.nexus.parent.ui.settings.maincontent.audiointercom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup

/**
 * AudioIntercomSection.kt - Tab 2 of Settings Console (`audio-voice-intercom/audio-voice-intercom.ts`).
 */
@Composable
fun AudioIntercomSection(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Voice Changer DSP") {
            VoicePresetSetting(
                selectedPreset = settings.defaultVoicePreset,
                onUpdateSettings = onUpdateSettings
            )
        }

        SettingsSectionGroup(title = "Child Hardware Audio") {
            NoiseSuppressionSetting(
                checked = settings.noiseSuppression,
                onUpdateSettings = onUpdateSettings
            )
            LoudspeakerVolumeSetting(
                selectedVolume = settings.loudspeakerVolume,
                onUpdateSettings = onUpdateSettings
            )
        }
    }
}
