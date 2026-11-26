package com.nexus.parent.ui.settings.maincontent.surveillancedvr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.components.TacticalSwitch
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * HardwareAccelerationSetting.kt - Matches Desktop `surveillance-dvr/hardware-acceleration.ts`.
 *
 * Hey friend! Controls whether incoming WebRTC video streams decode via hardware `MediaCodec` +
 * `H.264` priority (`true`) or CPU software decoders (`SoftwareVideoDecoderFactory` + `VP8`
 * priority, `false`). If toggled while already connected, renders an inline Reconnect prompt so
 * the user can immediately renegotiate the active peer session.
 */
@Composable
fun HardwareAccelerationSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    isReconnectPending: Boolean = false,
    onReconnectNow: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(
                NexusDimens.BorderHairline,
                if (isReconnectPending) NexusColors.WarningAmberBorder else NexusColors.BorderSubtle,
                RoundedCornerShape(NexusDimens.RadiusMd)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "GPU Video Hardware Acceleration",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Uses MediaCodec GPU decoding and H.264 priority; disable to force CPU software decoding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }
            TacticalSwitch(
                checked = checked,
                onCheckedChange = { value ->
                    onUpdateSettings { it.copy(hardwareAcceleration = value) }
                    onShowToast(
                        if (value) {
                            "GPU hardware acceleration enabled"
                        } else {
                            "GPU hardware acceleration disabled (software decoder mode)"
                        }
                    )
                },
                modifier = Modifier.testTag("setting_hw_accel_switch")
            )
        }

        if (isReconnectPending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                    .background(NexusColors.WarningAmberSubtle)
                    .border(
                        NexusDimens.BorderHairline,
                        NexusColors.WarningAmberBorder,
                        RoundedCornerShape(NexusDimens.RadiusSm)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reconnect session to apply decoder change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.WarningAmber,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                TacticalSecondaryButton(
                    text = "Reconnect Now",
                    iconRes = R.drawable.ic_refresh_cw,
                    tintColor = NexusColors.WarningAmber,
                    borderColor = NexusColors.WarningAmberBorder,
                    onClick = onReconnectNow,
                    modifier = Modifier.testTag("setting_hw_accel_reconnect_btn")
                )
            }
        }
    }
}
