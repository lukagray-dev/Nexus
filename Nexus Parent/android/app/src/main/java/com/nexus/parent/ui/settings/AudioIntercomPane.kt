package com.nexus.parent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.LoudspeakerVolume
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * AudioIntercomPane.kt - Tab 2 of Settings Console (`audio-voice-intercom/`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioIntercomPane(
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.SurfaceCard)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Default Intercom Voice Preset",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Applied automatically when speaking over the Push-to-Talk intercom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoicePresetId.entries.forEach { preset ->
                        val active = settings.defaultVoicePreset == preset
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                                .background(if (active) NexusColors.AccentWhite else NexusColors.SurfaceSidebar)
                                .border(
                                    NexusDimens.BorderHairline,
                                    if (active) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                                    RoundedCornerShape(NexusDimens.RadiusCapsule)
                                )
                                .clickable {
                                    onUpdateSettings { it.copy(defaultVoicePreset = preset) }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = preset.iconRes),
                                contentDescription = null,
                                tint = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        SettingsSectionGroup(title = "Child Hardware Audio") {
            SettingsToggleRow(
                title = "Noise Suppression & Echo Cancellation",
                subtitle = "Filters ambient feedback during two-way voice intercom sessions.",
                checked = settings.noiseSuppression,
                onCheckedChange = { v -> onUpdateSettings { it.copy(noiseSuppression = v) } }
            )

            SettingsSegmentedRow(
                title = "Child Loudspeaker Target Volume",
                subtitle = "Volume level enforced on child device speaker during Push-to-Talk.",
                options = LoudspeakerVolume.entries,
                selected = settings.loudspeakerVolume,
                labelProvider = { it.label },
                onSelect = { vol -> onUpdateSettings { it.copy(loudspeakerVolume = vol) } }
            )
        }
    }
}
