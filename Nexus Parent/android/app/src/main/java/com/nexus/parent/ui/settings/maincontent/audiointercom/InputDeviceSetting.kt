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
 * InputDeviceSetting.kt - Parent Microphone Input Device selector matching Desktop `input-device.ts`.
 *
 * Hey friend! Displays real hardware microphones detected by Android `AudioManager` (`Phone
 * Microphone`, `Wired Headset Mic`, `Bluetooth Headset Mic`, `USB Microphone`) plus `"System
 * Default Input"`. If a saved external microphone is disconnected, an inline warning tells the
 * parent cleanly that System Default capture will be used until reconnected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputDeviceSetting(
    selectedDeviceId: String,
    availableInputs: List<AudioDeviceOption>,
    hasRecordPermission: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onDetectOrRefresh: () -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    val isSavedAvailable = AudioDeviceMapper.isSavedDeviceAvailable(selectedDeviceId, availableInputs)
    val options = buildList {
        add(AudioDeviceOption(id = AudioDeviceMapper.DEFAULT_DEVICE_ID, label = "System Default Input"))
        addAll(availableInputs)
        if (!isSavedAvailable) {
            add(
                AudioDeviceOption(
                    id = selectedDeviceId,
                    label = "Unavailable Saved Microphone"
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
                text = "Microphone Input Device",
                style = MaterialTheme.typography.labelMedium,
                color = NexusColors.TextPrimary
            )
            Text(
                text = "Parent phone or headset microphone used for Push-to-Talk speech broadcast.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted
            )
        }

        if (!isSavedAvailable) {
            Text(
                text = "Saved microphone is disconnected; System Default will be used until reconnected.",
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
                            onUpdateSettings { it.copy(inputDeviceId = option.id) }
                            onShowToast("Microphone input set to ${option.label}")
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("settings_input_device_${option.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
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
            text = if (hasRecordPermission) "Refresh Microphones" else "Detect Devices",
            iconRes = R.drawable.ic_refresh_cw,
            onClick = onDetectOrRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_detect_input_devices_btn")
        )
    }
}
