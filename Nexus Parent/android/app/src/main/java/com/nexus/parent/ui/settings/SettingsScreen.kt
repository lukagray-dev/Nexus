package com.nexus.parent.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.SettingsTabId
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.settings.maincontent.SettingsMainContent
import com.nexus.parent.ui.settings.sidebar.SettingsSidebarPillStrip
import com.nexus.parent.ui.settings.sidebar.SettingsSidebarRail
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * SettingsScreen.kt - 5-Category Settings Console matching Desktop `settings/settings.ts`.
 *
 * Hey friend! Notice how clean this file is now that every tab lives in its own folder under
 * `ui/settings/maincontent/` and every setting lives in its own modular file, matching Desktop 1:1.
 */
@Composable
fun SettingsScreen(
    state: ParentUiState,
    onSelectTab: (SettingsTabId) -> Unit,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onBrowseDownloadsFolder: () -> Unit = {},
    hasRecordAudioPermission: Boolean = true,
    onDetectOrRefreshInputs: () -> Unit = {},
    onRefreshOutputs: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onSelectPairedDevice: (String) -> Unit,
    onClearSelectedPairedDevice: () -> Unit,
    onConnectPairedDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onCancelReconnect: () -> Unit,
    onResetDefaults: () -> Unit,
    onClearWorkspaceHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isSettingsOpen) return

    val devicesBadge = when {
        state.connectionPhase == ConnectionPhase.CONNECTED -> "Online"
        state.connectionPhase == ConnectionPhase.RECONNECTING -> "Reconnecting"
        state.pairedDevicesHistory.isNotEmpty() -> state.pairedDevicesHistory.size.toString()
        state.lastPairedDevice != null -> "Offline"
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(10.dp),
            shape = RoundedCornerShape(NexusDimens.RadiusXl),
            color = NexusColors.CanvasElevated,
            border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = null,
                            tint = NexusColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Nexus Parent Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexusColors.TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .background(NexusColors.SurfaceSidebar)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close settings",
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = NexusColors.BorderSubtle)

                // Adaptive Body (Side-by-side Rail on tablets vs Pill Strip on phones)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWide = maxWidth >= 600.dp

                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            SettingsSidebarRail(
                                activeTab = state.activeSettingsTab,
                                devicesBadge = devicesBadge,
                                onSelectTab = onSelectTab
                            )
                            VerticalDivider(color = NexusColors.BorderSubtle)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(NexusColors.CanvasElevated)
                            ) {
                                SettingsMainContent(
                                    state = state,
                                    onUpdateSettings = onUpdateSettings,
                                    onBrowseDownloadsFolder = onBrowseDownloadsFolder,
                                    hasRecordAudioPermission = hasRecordAudioPermission,
                                    onDetectOrRefreshInputs = onDetectOrRefreshInputs,
                                    onRefreshOutputs = onRefreshOutputs,
                                    onShowToast = onShowToast,
                                    onSelectPairedDevice = onSelectPairedDevice,
                                    onClearSelectedPairedDevice = onClearSelectedPairedDevice,
                                    onConnectPairedDevice = onConnectPairedDevice,
                                    onDisconnectDevice = onDisconnectDevice,
                                    onCancelReconnect = onCancelReconnect,
                                    onClearWorkspaceHistory = onClearWorkspaceHistory
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SettingsSidebarPillStrip(
                                activeTab = state.activeSettingsTab,
                                devicesBadge = devicesBadge,
                                onSelectTab = onSelectTab
                            )
                            HorizontalDivider(color = NexusColors.BorderSubtle)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(NexusColors.CanvasElevated)
                            ) {
                                SettingsMainContent(
                                    state = state,
                                    onUpdateSettings = onUpdateSettings,
                                    onBrowseDownloadsFolder = onBrowseDownloadsFolder,
                                    hasRecordAudioPermission = hasRecordAudioPermission,
                                    onDetectOrRefreshInputs = onDetectOrRefreshInputs,
                                    onRefreshOutputs = onRefreshOutputs,
                                    onShowToast = onShowToast,
                                    onSelectPairedDevice = onSelectPairedDevice,
                                    onClearSelectedPairedDevice = onClearSelectedPairedDevice,
                                    onConnectPairedDevice = onConnectPairedDevice,
                                    onDisconnectDevice = onDisconnectDevice,
                                    onCancelReconnect = onCancelReconnect,
                                    onClearWorkspaceHistory = onClearWorkspaceHistory
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = NexusColors.BorderSubtle)

                // Footer: Reset Defaults + Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TacticalSecondaryButton(
                        text = "Reset Defaults",
                        iconRes = R.drawable.ic_refresh_cw,
                        onClick = onResetDefaults,
                        modifier = Modifier.testTag("settings_reset_btn")
                    )
                    TacticalPrimaryButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.testTag("settings_done_btn")
                    )
                }
            }
        }
    }
}
