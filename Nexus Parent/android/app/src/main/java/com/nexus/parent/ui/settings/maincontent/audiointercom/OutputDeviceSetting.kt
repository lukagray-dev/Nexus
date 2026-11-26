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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.data.mic.AudioDeviceMapper
import com.nexus.parent.state.AudioDeviceOption
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * OutputDeviceSetting.kt - Monitoring Output Speaker selector matching Desktop `output-device.ts`.
 *
 * Hey friend! Selects which local output hardware (`Phone Earpiece` for discreet public listening,
 * `Built-in Speaker`, `Wired Headphones`, `USB Audio`, or `Bluetooth`) plays both Child Mic audio
 * and Screen System Audio (`OpusAudioPlayer`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputDeviceSetting(
    selectedDeviceId: String,
    availableOutputs: List<AudioDeviceOption>,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onRefreshOutputs: () -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    val isSavedAvailable = AudioDeviceMapper.isSavedDeviceAvailable(selectedDeviceId, availableOutputs)
    val options = buildList {
        add(AudioDeviceOption(id = AudioDeviceMapper.DEFAULT_DEVICE_ID, label = "System Default Output"))
        addAll(availableOutputs)
        if (!isSavedAvailable) {
            add(
                AudioDeviceOption(
                    id = selectedDeviceId,
                    label = "Unavailable Saved Speaker"
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Monitoring Output Speaker",
                style = MaterialTheme.typography.labelMedium,
                color = NexusColors.TextPrimary
            )
            Text(
                text = "Speaker, earpiece, or headset used to listen to child microphone and screen system audio.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted
            )
        }

        if (!isSavedAvailable) {
            Text(
                text = "Saved speaker is disconnected; System Default Output will be used until reconnected.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.WarningAmber
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val active = selectedDeviceId == option.id
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
                            onUpdateSettings { it.copy(outputDeviceId = option.id) }
                            onShowToast("Monitoring output set to ${option.label}")
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("settings_output_device_${option.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_audio_lines),
                        contentDescription = null,
                        tint = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary
                    )
                }
            }
        }

        TacticalSecondaryButton(
            text = "Refresh Output Devices",
            iconRes = R.drawable.ic_refresh_cw,
            onClick = onRefreshOutputs,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_refresh_output_devices_btn")
        )
    }
}
