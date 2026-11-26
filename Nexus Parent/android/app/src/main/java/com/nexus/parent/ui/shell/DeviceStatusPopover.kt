package com.nexus.parent.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * DeviceStatusPopover.kt - Live device telemetry card & disconnect action inside the Left Drawer.
 *
 * Hey friend! Mirrors Desktop's `#sidebar-device-dropdown`: displays live battery, network,
 * storage, and status when connected, or reconnection attempt countdown when reconnecting.
 */
@Composable
fun DeviceStatusPopover(
    connectionPhase: ConnectionPhase,
    device: DeviceRecord?,
    reconnectAttempt: Int,
    reconnectMaxAttempts: Int,
    reconnectSecondsRemaining: Int,
    onDisconnect: () -> Unit,
    onCancelReconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryReconnectNow: () -> Unit = {}
) {
    if (device == null) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NexusDimens.RadiusMd),
        color = NexusColors.SurfaceDropdown,
        border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (connectionPhase == ConnectionPhase.RECONNECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusColors.TextPrimary
                    )
                    TacticalBadgePill(
                        text = "Attempt $reconnectAttempt of $reconnectMaxAttempts",
                        textColor = NexusColors.WarningAmber,
                        backgroundColor = NexusColors.WarningAmberSubtle,
                        borderColor = NexusColors.WarningAmberBorder
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                TelemetryRow("Device ID", device.formattedId, isMono = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", style = MaterialTheme.typography.bodySmall, color = NexusColors.TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPulseDot(color = NexusColors.WarningAmber, size = 6.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reconnecting in ${reconnectSecondsRemaining}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.WarningAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = NexusColors.BorderSubtle)
                Spacer(modifier = Modifier.height(8.dp))
                PopoverActionButton(
                    iconRes = R.drawable.ic_refresh_cw,
                    label = "Retry Now",
                    accent = NexusColors.TextPrimary,
                    backgroundColor = NexusColors.SurfaceCard,
                    onClick = onRetryReconnectNow
                )
                Spacer(modifier = Modifier.height(6.dp))
                PopoverActionButton(
                    iconRes = R.drawable.ic_close,
                    label = "Cancel Reconnection",
                    accent = NexusColors.DangerRed,
                    backgroundColor = NexusColors.DangerRedSubtle,
                    onClick = onCancelReconnect
                )
            } else {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "${device.model} • Android ${device.telemetry.androidVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                TelemetryRow(
                    label = "Battery",
                    value = "${device.telemetry.batteryPercent}%${if (device.telemetry.isCharging) " (Charging)" else ""}"
                )
                TelemetryRow("Network", device.telemetry.networkState)
                TelemetryRow("Storage", device.telemetry.storageUsageText)
                TelemetryRow("Status", "Online", valueColor = NexusColors.LiveGreen)

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = NexusColors.BorderSubtle)
                Spacer(modifier = Modifier.height(8.dp))
                PopoverActionButton(
                    iconRes = R.drawable.ic_unlink,
                    label = "Disconnect Device",
                    accent = NexusColors.DangerRed,
                    onClick = onDisconnect
                )
            }
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color = NexusColors.TextPrimary,
    isMono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NexusColors.TextMuted
        )
        Text(
            text = value,
            style = if (isMono) NexusTypography.MonoValue else MaterialTheme.typography.bodySmall,
            color = valueColor
        )
    }
}

@Composable
private fun PopoverActionButton(
    iconRes: Int,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    backgroundColor: Color = NexusColors.DangerRedSubtle
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}
