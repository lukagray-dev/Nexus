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
import com.nexus.parent.data.monitor.CombinedPattern
import com.nexus.parent.data.monitor.FlashAction
import com.nexus.parent.data.monitor.VibratePattern
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
    onVibrateClick: (VibratePattern) -> Unit,
    onFlashClick: (FlashAction) -> Unit,
    onCombinedClick: (CombinedPattern) -> Unit,
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
                leftLabel = VibratePattern.SHORT.displayLabel,
                leftIcon = R.drawable.ic_vibrate,
                leftDanger = false,
                onLeftClick = { onVibrateClick(VibratePattern.SHORT) },
                rightLabel = VibratePattern.DOUBLE.displayLabel,
                rightIcon = R.drawable.ic_vibrate,
                rightDanger = false,
                onRightClick = { onVibrateClick(VibratePattern.DOUBLE) }
            )
            ActuatorRow(
                leftLabel = VibratePattern.PULSE.displayLabel,
                leftIcon = R.drawable.ic_vibrate,
                leftDanger = false,
                onLeftClick = { onVibrateClick(VibratePattern.PULSE) },
                rightLabel = VibratePattern.SOS.displayLabel,
                rightIcon = R.drawable.ic_vibrate,
                rightDanger = true,
                onRightClick = { onVibrateClick(VibratePattern.SOS) }
            )
        }

        ActuatorSection(title = "CAMERA FLASHLIGHT STROBE") {
            ActuatorRow(
                leftLabel = FlashAction.ON.displayLabel,
                leftIcon = R.drawable.ic_flashlight,
                leftDanger = false,
                onLeftClick = { onFlashClick(FlashAction.ON) },
                rightLabel = FlashAction.OFF.displayLabel,
                rightIcon = R.drawable.ic_flashlight,
                rightDanger = false,
                onRightClick = { onFlashClick(FlashAction.OFF) }
            )
            ActuatorRow(
                leftLabel = FlashAction.STROBE.displayLabel,
                leftIcon = R.drawable.ic_zap,
                leftDanger = false,
                onLeftClick = { onFlashClick(FlashAction.STROBE) },
                rightLabel = FlashAction.BEACON.displayLabel,
                rightIcon = R.drawable.ic_zap,
                rightDanger = true,
                onRightClick = { onFlashClick(FlashAction.BEACON) }
            )
        }

        ActuatorSection(title = "COMBINED VIBRATE & FLASH") {
            ActuatorRow(
                leftLabel = CombinedPattern.ALERT.displayLabel,
                leftIcon = R.drawable.ic_bell,
                leftDanger = false,
                onLeftClick = { onCombinedClick(CombinedPattern.ALERT) },
                rightLabel = CombinedPattern.SOS.displayLabel,
                rightIcon = R.drawable.ic_zap,
                rightDanger = true,
                onRightClick = { onCombinedClick(CombinedPattern.SOS) }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ActuatorButton(
                    label = CombinedPattern.PULSE.displayLabel,
                    iconRes = R.drawable.ic_activity,
                    isDanger = false,
                    onClick = { onCombinedClick(CombinedPattern.PULSE) },
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
