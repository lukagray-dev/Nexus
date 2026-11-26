package com.nexus.parent.ui.settings.maincontent.paireddevices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * PairedDevicesSection.kt - Tab 4 of Settings Console (`paired-devices/paired-devices.ts`).
 *
 * Hey friend! Mirrors Desktop's two-level Paired Devices experience 1:1:
 * - Level 1 (List View): Shows every previously paired Child device with an inline status pill,
 *   model/battery/last-seen summary, and an immediate Connect/Disconnect/Cancel button.
 * - Level 2 (Detail View): Tapping any device row drills into the full 27-field telemetry card
 *   with an "All Paired Devices" back button in the header.
 */
@Composable
fun PairedDevicesSection(
    connectionPhase: ConnectionPhase,
    connectedDevice: DeviceRecord?,
    lastPairedDevice: DeviceRecord?,
    pairedDevicesHistory: List<DeviceRecord>,
    selectedDeviceId: String?,
    connectingFromHistoryId: String?,
    onSelectDevice: (String) -> Unit,
    onClearSelectedDevice: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onCancelReconnect: () -> Unit
) {
    // Merge freshest live device over history list so active telemetry updates reflect immediately
    val mergedDevices = buildMergedDeviceList(
        history = pairedDevicesHistory,
        connectedDevice = connectedDevice,
        lastPairedDevice = lastPairedDevice
    )

    if (mergedDevices.isEmpty()) {
        TacticalEmptyState(
            iconRes = R.drawable.ic_devices,
            title = "No Paired Devices Yet",
            description = "Pair any 12-digit Child ID on the connection screen to save it here for 1-tap reconnect and full 27-field hardware telemetry inspection."
        )
        return
    }

    val selectedDevice = selectedDeviceId?.let { targetId ->
        mergedDevices.firstOrNull { it.rawId == targetId }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedDevice != null) {
            val isThisActive = isDeviceActive(selectedDevice.rawId, connectionPhase, connectedDevice, lastPairedDevice)
            val isThisConnecting = connectingFromHistoryId == selectedDevice.rawId ||
                (isThisActive && connectionPhase == ConnectionPhase.CONNECTING)

            // Detail Header Bar: Back to List + Quick Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                        .background(NexusColors.SurfaceCard)
                        .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                        .clickable(onClick = onClearSelectedDevice)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("paired_devices_back_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "All Paired Devices",
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "All Paired Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusColors.TextPrimary
                    )
                }

                DeviceActionButton(
                    isOnline = isThisActive && connectionPhase == ConnectionPhase.CONNECTED,
                    isReconnecting = isThisActive && connectionPhase == ConnectionPhase.RECONNECTING,
                    isConnecting = isThisConnecting,
                    rawId = selectedDevice.rawId,
                    onConnectDevice = onConnectDevice,
                    onDisconnectDevice = onDisconnectDevice,
                    onCancelReconnect = onCancelReconnect
                )
            }

            DeviceTelemetryCard(
                device = selectedDevice,
                connectionPhase = connectionPhase,
                isActiveDevice = isThisActive
            )
        } else {
            // Level 1: Saved Devices Directory
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PAIRED DEVICES (${mergedDevices.size})",
                    style = NexusTypography.SectionHeader,
                    color = NexusColors.TextMuted
                )
                Text(
                    text = "Tap any device row to inspect full telemetry, or click Connect to pair immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }

            mergedDevices.forEach { item ->
                val isThisActive = isDeviceActive(item.rawId, connectionPhase, connectedDevice, lastPairedDevice)
                val isThisConnecting = connectingFromHistoryId == item.rawId ||
                    (isThisActive && connectionPhase == ConnectionPhase.CONNECTING)
                val isOnline = isThisActive && connectionPhase == ConnectionPhase.CONNECTED
                val isReconnecting = isThisActive && connectionPhase == ConnectionPhase.RECONNECTING

                PairedDeviceListRow(
                    device = item,
                    isOnline = isOnline,
                    isReconnecting = isReconnecting,
                    isConnecting = isThisConnecting,
                    onRowClick = { onSelectDevice(item.rawId) },
                    onConnectDevice = { onConnectDevice(item.rawId) },
                    onDisconnectDevice = onDisconnectDevice,
                    onCancelReconnect = onCancelReconnect
                )
            }
        }
    }
}

@Composable
private fun PairedDeviceListRow(
    device: DeviceRecord,
    isOnline: Boolean,
    isReconnecting: Boolean,
    isConnecting: Boolean,
    onRowClick: () -> Unit,
    onConnectDevice: () -> Unit,
    onDisconnectDevice: () -> Unit,
    onCancelReconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(
                NexusDimens.BorderHairline,
                if (isOnline) NexusColors.LiveGreenBorder else NexusColors.BorderSubtle,
                RoundedCornerShape(NexusDimens.RadiusMd)
            )
            .clickable(onClick = onRowClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("paired_device_row_${device.rawId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_nexus_child),
            contentDescription = null,
            modifier = Modifier.size(30.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = NexusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
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

            Text(
                text = "${device.formattedId} • Android ${device.telemetry.androidVersion} • ${device.telemetry.batteryPercent}%",
                style = NexusTypography.MonoBadge,
                color = NexusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = device.lastSyncedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            DeviceActionButton(
                isOnline = isOnline,
                isReconnecting = isReconnecting,
                isConnecting = isConnecting,
                rawId = device.rawId,
                onConnectDevice = { onConnectDevice() },
                onDisconnectDevice = onDisconnectDevice,
                onCancelReconnect = onCancelReconnect
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Inspect",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.TextMuted
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceActionButton(
    isOnline: Boolean,
    isReconnecting: Boolean,
    isConnecting: Boolean,
    rawId: String,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onCancelReconnect: () -> Unit
) {
    when {
        isOnline -> TacticalSecondaryButton(
            text = "Disconnect",
            iconRes = R.drawable.ic_unlink,
            tintColor = NexusColors.DangerRed,
            borderColor = NexusColors.DangerRedBorder,
            onClick = onDisconnectDevice,
            modifier = Modifier.testTag("paired_device_action_$rawId")
        )
        isReconnecting -> TacticalSecondaryButton(
            text = "Cancel",
            iconRes = R.drawable.ic_close,
            tintColor = NexusColors.WarningAmber,
            borderColor = NexusColors.WarningAmberBorder,
            onClick = onCancelReconnect,
            modifier = Modifier.testTag("paired_device_action_$rawId")
        )
        isConnecting -> TacticalPrimaryButton(
            text = "Connecting...",
            iconRes = R.drawable.ic_link,
            enabled = false,
            onClick = {},
            modifier = Modifier.testTag("paired_device_action_$rawId")
        )
        else -> TacticalPrimaryButton(
            text = "Connect",
            iconRes = R.drawable.ic_link,
            onClick = { onConnectDevice(rawId) },
            modifier = Modifier.testTag("paired_device_action_$rawId")
        )
    }
}

private fun isDeviceActive(
    rawId: String,
    connectionPhase: ConnectionPhase,
    connectedDevice: DeviceRecord?,
    lastPairedDevice: DeviceRecord?
): Boolean {
    if (connectionPhase == ConnectionPhase.DISCONNECTED) return false
    val activeId = connectedDevice?.rawId ?: lastPairedDevice?.rawId
    return activeId == rawId
}

private fun buildMergedDeviceList(
    history: List<DeviceRecord>,
    connectedDevice: DeviceRecord?,
    lastPairedDevice: DeviceRecord?
): List<DeviceRecord> {
    val map = LinkedHashMap<String, DeviceRecord>()
    connectedDevice?.let { map[it.rawId] = it }
    history.forEach { item ->
        map.putIfAbsent(item.rawId, item)
    }
    lastPairedDevice?.let { map.putIfAbsent(it.rawId, it) }
    return map.values.toList()
}
