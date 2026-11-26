package com.nexus.parent.ui.settings.maincontent.audiointercom

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * VoicePresetSetting.kt - Matches Desktop `audio-voice-intercom/voice-preset.ts`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePresetSetting(
    selectedPreset: VoicePresetId,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onShowToast: (String) -> Unit = {}
) {
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
                val active = selectedPreset == preset
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
                            onShowToast("Intercom voice set to ${preset.displayName}")
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
