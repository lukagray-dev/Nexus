package com.nexus.parent.ui.monitor.vibrateflash

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nexus.parent.R
import com.nexus.parent.state.VibrateFlashUiState
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * VibrateFlashPanel.kt - Remote Hardware Actuator Control UI (`vibrateflash.ts`).
 */
@Composable
fun VibrateFlashPanel(
    state: VibrateFlashUiState,
    onTriggerAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Confirmation Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusPulseDot(
                color = when (state.lastSuccess) {
                    true -> NexusColors.LiveGreen
                    false -> NexusColors.DangerRed
                    null -> NexusColors.TextMuted
                },
                isPulsing = state.lastSuccess != null,
                size = 8.dp
            )
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextPrimary
            )
        }

        ActuatorSection(title = "VIBRATION PATTERNS") {
            ActuatorRow(
                leftLabel = "Short Buzz",
                leftIcon = R.drawable.ic_vibrate,
                leftDanger = false,
                onLeftClick = { onTriggerAction("Vibration: Short Buzz") },
                rightLabel = "Double Tap",
                rightIcon = R.drawable.ic_vibrate,
                rightDanger = false,
                onRightClick = { onTriggerAction("Vibration: Double Tap") }
            )
            ActuatorRow(
                leftLabel = "Pulse Wave",
                leftIcon = R.drawable.ic_vibrate,
                leftDanger = false,
                onLeftClick = { onTriggerAction("Vibration: Pulse Wave") },
                rightLabel = "SOS Pattern",
                rightIcon = R.drawable.ic_vibrate,
                rightDanger = true,
                onRightClick = { onTriggerAction("Vibration: SOS Pattern") }
            )
        }

        ActuatorSection(title = "CAMERA FLASHLIGHT STROBE") {
            ActuatorRow(
                leftLabel = "Flashlight On",
                leftIcon = R.drawable.ic_flashlight,
                leftDanger = false,
                onLeftClick = { onTriggerAction("Flashlight On") },
                rightLabel = "Flashlight Off",
                rightIcon = R.drawable.ic_flashlight,
                rightDanger = false,
                onRightClick = { onTriggerAction("Flashlight Off") }
            )
            ActuatorRow(
                leftLabel = "Strobe (4 Hz)",
                leftIcon = R.drawable.ic_zap,
                leftDanger = false,
                onLeftClick = { onTriggerAction("Flashlight Strobe (4 Hz)") },
                rightLabel = "Emergency Beacon",
                rightIcon = R.drawable.ic_zap,
                rightDanger = true,
                onRightClick = { onTriggerAction("Emergency Beacon") }
            )
        }

        ActuatorSection(title = "COMBINED VIBRATE & FLASH") {
            ActuatorRow(
                leftLabel = "Alert Alarm",
                leftIcon = R.drawable.ic_bell,
                leftDanger = false,
                onLeftClick = { onTriggerAction("Combined Alert Alarm") },
                rightLabel = "Panic SOS",
                rightIcon = R.drawable.ic_zap,
                rightDanger = true,
                onRightClick = { onTriggerAction("Combined Panic SOS") }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ActuatorButton(
                    label = "Pulse Strobe",
                    iconRes = R.drawable.ic_activity,
                    isDanger = false,
                    onClick = { onTriggerAction("Combined Pulse Strobe") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ActuatorSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = NexusTypography.SectionHeader,
            color = NexusColors.TextMuted
        )
        content()
    }
}

@Composable
private fun ActuatorRow(
    leftLabel: String,
    @DrawableRes leftIcon: Int,
    leftDanger: Boolean,
    onLeftClick: () -> Unit,
    rightLabel: String,
    @DrawableRes rightIcon: Int,
    rightDanger: Boolean,
    onRightClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActuatorButton(
            label = leftLabel,
            iconRes = leftIcon,
            isDanger = leftDanger,
            onClick = onLeftClick,
            modifier = Modifier.weight(1f)
        )
        ActuatorButton(
            label = rightLabel,
            iconRes = rightIcon,
            isDanger = rightDanger,
            onClick = onRightClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActuatorButton(
    label: String,
    @DrawableRes iconRes: Int,
    isDanger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(if (isDanger) NexusColors.DangerRedSubtle else NexusColors.SurfaceCard)
            .border(
                NexusDimens.BorderHairline,
                if (isDanger) NexusColors.DangerRedBorder else NexusColors.BorderSubtle,
                RoundedCornerShape(NexusDimens.RadiusMd)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (isDanger) NexusColors.DangerRed else NexusColors.TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NexusColors.TextPrimary
        )
    }
}
