package com.nexus.parent.ui.settings.maincontent.paireddevices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * DeviceTelemetryCard.kt - 27-field live hardware, OS, power, storage, network, and posture
 * telemetry inspector matching Desktop `paired-devices/device-card.ts`.
 *
 * Hey friend! When inspected for an offline device from history, the card gently lowers its
 * contrast while keeping every last-known field readable so parents always know the child phone's
 * last synced battery, storage, OS, and security posture.
 */
@Composable
fun DeviceTelemetryCard(
    device: DeviceRecord,
    connectionPhase: ConnectionPhase,
    isActiveDevice: Boolean,
    modifier: Modifier = Modifier
) {
    val isOnline = isActiveDevice && connectionPhase == ConnectionPhase.CONNECTED
    val isReconnecting = isActiveDevice && connectionPhase == ConnectionPhase.RECONNECTING
    val isConnecting = isActiveDevice && connectionPhase == ConnectionPhase.CONNECTING
    val contentAlpha = if (isOnline) 1f else 0.64f
    val t = device.telemetry

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Device Header Summary Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_nexus_child),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = NexusColors.TextPrimary
                    )
                    Text(
                        text = "${device.formattedId} • ${device.lastSyncedLabel}",
                        style = NexusTypography.MonoBadge,
                        color = NexusColors.TextMuted
                    )
                }
            }
            TacticalBadgePill(
                text = when {
                    isOnline -> "ONLINE"
                    isReconnecting -> "RECONNECTING"
                    isConnecting -> "CONNECTING"
                    else -> "OFFLINE"
                },
                textColor = when {
                    isOnline -> NexusColors.LiveGreen
                    isReconnecting -> NexusColors.WarningAmber
                    isConnecting -> NexusColors.TextPrimary
                    else -> NexusColors.TextMuted
                }
            )
        }

        // Complete 27-field Hardware, OS, Battery, Memory, Storage, Radio & Posture Grid
        TelemetryTileRow(
            leftLabel = "OS VERSION",
            leftValue = "Android ${t.androidVersion} (API ${t.sdkInt})",
            rightLabel = "HARDWARE",
            rightValue = "${t.brand} ${device.model}"
        )
        TelemetryTileRow(
            leftLabel = "DISPLAY",
            leftValue = "${if (t.isScreenOn) "Awake" else "Screen Off"} (${t.screenResolution})",
            rightLabel = "CHILD APP VERSION",
            rightValue = t.appVersion
        )
        TelemetryTileRow(
            leftLabel = "BATTERY & CHARGING",
            leftValue = "${t.batteryPercent}% • ${if (t.isCharging) "Charging (${t.chargingSource})" else t.chargingSource}",
            rightLabel = "THERMAL & SAVER",
            rightValue = "${"%.1f".format(t.batteryTemperatureC)} C • ${if (t.isPowerSaveMode) "Battery Saver On" else "Normal"}"
        )
        TelemetryTileRow(
            leftLabel = "SYSTEM RAM",
            leftValue = t.ramUsageText,
            rightLabel = "INTERNAL STORAGE",
            rightValue = t.storageUsageText
        )
        TelemetryTileRow(
            leftLabel = "NETWORK & CARRIER",
            leftValue = "${t.networkState} • ${t.simCarrier}",
            rightLabel = "RINGER & UPTIME",
            rightValue = "${t.ringerMode} • ${t.deviceUptimeLabel}"
        )
        TelemetryTileRow(
            leftLabel = "SECURITY GUARDS",
            leftValue = t.securityGuardsText,
            rightLabel = "SURVIVAL PERMISSIONS",
            rightValue = "${t.survivalPermissionsText} • ${if (t.batteryOptimizationIgnored) "Doze Exempt" else "Restricted"}"
        )
    }
}

@Composable
private fun TelemetryTileRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryTile(leftLabel, leftValue, Modifier.weight(1f))
        TelemetryTile(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = NexusTypography.SectionHeader, color = NexusColors.TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextPrimary)
    }
}
