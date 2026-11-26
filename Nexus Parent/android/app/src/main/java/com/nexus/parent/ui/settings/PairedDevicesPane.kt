package com.nexus.parent.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * PairedDevicesPane.kt - Tab 4 of Settings Console (`paired-devices/`).
 *
 * Hey friend! Displays the paired child device's full hardware/OS/power/memory/posture
 * telemetry grid. When offline, renders in muted grayed-out contrast preserving cached values.
 */
@Composable
fun PairedDevicesPane(
    connectionPhase: ConnectionPhase,
    device: DeviceRecord?
) {
    if (device == null) {
        TacticalEmptyState(
            iconRes = R.drawable.ic_devices,
            title = "No Paired Child Device",
            description = "Pair a 12-digit Child ID to inspect live hardware, power, storage, and survival posture telemetry."
        )
        return
    }

    val isOnline = connectionPhase == ConnectionPhase.CONNECTED
    val isReconnecting = connectionPhase == ConnectionPhase.RECONNECTING
    val contentAlpha = if (isOnline) 1f else 0.58f
    val t = device.telemetry

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device Header Card
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
                Column {
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
                    else -> "OFFLINE"
                },
                textColor = when {
                    isOnline -> NexusColors.LiveGreen
                    isReconnecting -> NexusColors.WarningAmber
                    else -> NexusColors.TextMuted
                }
            )
        }

        TelemetryTileRow("OS", "Android ${t.androidVersion} (API ${t.sdkInt})", "HARDWARE", "${t.brand} ${device.model}")
        TelemetryTileRow("DISPLAY", "${if (t.isScreenOn) "Awake" else "Screen Off"} (${t.screenResolution})", "APP VERSION", t.appVersion)
        TelemetryTileRow(
            "BATTERY",
            "${t.batteryPercent}% • ${t.chargingSource}",
            "THERMAL / POWER",
            "${t.batteryTemperatureC} C • ${if (t.isPowerSaveMode) "Saver On" else "Normal"}"
        )
        TelemetryTileRow("SYSTEM RAM", t.ramUsageText, "INTERNAL STORAGE", t.storageUsageText)
        TelemetryTileRow("NETWORK / SIM", "${t.networkState} • ${t.simCarrier}", "RINGER / UPTIME", "${t.ringerMode} • ${t.deviceUptimeLabel}")
        TelemetryTileRow("SECURITY GUARDS", t.securityGuardsText, "SURVIVAL PERMISSIONS", t.survivalPermissionsText)
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
