package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.BuildConfig
import com.nexus.parent.R
import com.nexus.parent.data.update.AppUpdateProtocol
import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 *ParentIdentityCard.kt - Application identity, live version/release state, Copy System Info,
 * and Check Updates / Restart to update actions matching Desktop `about/parent-identity.ts`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentIdentityCard(
    state: ParentUiState,
    onCopySystemInfo: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onRestartToUpdate: () -> Unit
) {
    val update = state.appUpdate
    val badgeText = AppUpdateProtocol.formatUpdateStatusBadge(update)
    val isReady = update.status == AppUpdateStatusKind.READY
    val isBusy = update.status == AppUpdateStatusKind.CHECKING ||
        update.status == AppUpdateStatusKind.DOWNLOADING
    val isError = update.status == AppUpdateStatusKind.ERROR

    SettingsSectionGroup(title = "Application Identity & Releases") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_nexus_parent),
                    contentDescription = "Nexus Parent",
                    modifier = Modifier.size(42.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Nexus Parent",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexusColors.TextPrimary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        TacticalBadgePill(
                            text = "v${update.currentVersion}",
                            textColor = NexusColors.TextSecondary,
                            backgroundColor = NexusColors.SurfaceSidebar,
                            modifier = Modifier.testTag("about_version_badge")
                        )
                        TacticalBadgePill(
                            text = badgeText,
                            textColor = when {
                                isReady -> NexusColors.LiveGreen
                                isError -> NexusColors.DangerRed
                                isBusy -> NexusColors.WarningAmber
                                else -> NexusColors.TextSecondary
                            },
                            backgroundColor = NexusColors.SurfaceSidebar,
                            borderColor = when {
                                isReady -> NexusColors.LiveGreenBorder
                                isError -> NexusColors.DangerRedBorder
                                else -> NexusColors.BorderSubtle
                            },
                            modifier = Modifier.testTag("about_update_badge")
                        )
                    }
                    Text(
                        text = "${BuildConfig.APPLICATION_ID} • Zero-Cloud P2P Parental Console",
                        style = NexusTypography.MonoBadge,
                        color = NexusColors.TextSecondary
                    )
                    Text(
                        text = "Encrypted direct WebRTC device surveillance, live telemetry, and remote parental controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TacticalSecondaryButton(
                    text = "Copy System Info",
                    iconRes = R.drawable.ic_file_text,
                    onClick = { onCopySystemInfo(buildParentDiagnosticsSummary(state)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("about_copy_info_btn")
                )

                if (isReady) {
                    TacticalPrimaryButton(
                        text = "Restart to update",
                        iconRes = R.drawable.ic_refresh_cw,
                        onClick = onRestartToUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("about_check_updates_btn")
                    )
                } else {
                    TacticalPrimaryButton(
                        text = if (isBusy) badgeText else "Check Updates",
                        iconRes = R.drawable.ic_refresh_cw,
                        enabled = !isBusy,
                        onClick = onCheckUpdatesNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("about_check_updates_btn")
                    )
                }
            }
        }
    }
}

fun buildParentDiagnosticsSummary(state: ParentUiState): String {
    val peerLine = when (state.connectionPhase) {
        ConnectionPhase.CONNECTED -> state.connectedDevice?.let { "Connected (${it.name} / ${it.formattedId})" }
            ?: "Connected"
        ConnectionPhase.CONNECTING -> "Connecting"
        ConnectionPhase.RECONNECTING -> "Reconnecting (${state.reconnectAttempt}/${state.reconnectMaxAttempts})"
        ConnectionPhase.DISCONNECTED -> "Standby"
    }
    val updatesPath = state.appUpdate.updatesDirPath.ifBlank { "files/updates" }
    val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    return listOf(
        "Application: Nexus Parent Android (${BuildConfig.APPLICATION_ID})",
        "Version: v${state.appUpdate.currentVersion} (code ${BuildConfig.VERSION_CODE})",
        "Update State: ${AppUpdateProtocol.formatUpdateStatusBadge(state.appUpdate)}",
        "Android OS: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}, $abi)",
        "Device Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        "CPU Cores: ${Runtime.getRuntime().availableProcessors()}",
        "Hardware Acceleration: ${if (state.settings.hardwareAcceleration) "Enabled" else "Disabled"}",
        "Active Peer: $peerLine",
        "Local Storage: ${state.localStorageMetrics.formattedSize} (${state.localStorageMetrics.entryCount} keys)",
        "Downloads Dir: ${state.settings.downloadsDirectoryLabel}",
        "Staged Updates Dir: $updatesPath"
    ).joinToString("\n")
}
