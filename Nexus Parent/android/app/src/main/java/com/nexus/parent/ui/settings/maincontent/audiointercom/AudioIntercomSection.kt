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
import com.nexus.parent.state.AudioDeviceOption
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup

/**
 * AudioIntercomSection.kt - Tab 2 of Settings Console matching Desktop
 * `audio-voice-intercom/audio-voice-intercom.ts` 1:1.
 */
@Composable
fun AudioIntercomSection(
    settings: SettingsValues,
    availableInputs: List<AudioDeviceOption> = emptyList(),
    availableOutputs: List<AudioDeviceOption> = emptyList(),
    hasRecordAudioPermission: Boolean = true,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onDetectOrRefreshInputs: () -> Unit = {},
    onRefreshOutputs: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Voice Changer Engine") {
            VoicePresetSetting(
                selectedPreset = settings.defaultVoicePreset,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
            LoudspeakerVolumeSetting(
                selectedVolume = settings.loudspeakerVolume,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
        }

        SettingsSectionGroup(title = "Hardware Routing & DSP") {
            InputDeviceSetting(
                selectedDeviceId = settings.inputDeviceId,
                availableInputs = availableInputs,
                hasRecordPermission = hasRecordAudioPermission,
                onUpdateSettings = onUpdateSettings,
                onDetectOrRefresh = onDetectOrRefreshInputs,
                onShowToast = onShowToast
            )
            OutputDeviceSetting(
                selectedDeviceId = settings.outputDeviceId,
                availableOutputs = availableOutputs,
                onUpdateSettings = onUpdateSettings,
                onRefreshOutputs = onRefreshOutputs,
                onShowToast = onShowToast
            )
            NoiseSuppressionSetting(
                checked = settings.noiseSuppression,
                onUpdateSettings = onUpdateSettings,
                onShowToast = onShowToast
            )
        }
    }
}
