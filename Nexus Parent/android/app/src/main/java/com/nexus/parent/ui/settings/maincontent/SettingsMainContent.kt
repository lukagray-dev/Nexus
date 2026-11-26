package com.nexus.parent.ui.settings.maincontent

import androidx.compose.runtime.Composable
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.SettingsTabId
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.about.AboutSection
import com.nexus.parent.ui.settings.maincontent.audiointercom.AudioIntercomSection
import com.nexus.parent.ui.settings.maincontent.generalsystem.GeneralSystemSection
import com.nexus.parent.ui.settings.maincontent.paireddevices.PairedDevicesSection
import com.nexus.parent.ui.settings.maincontent.surveillancedvr.SurveillanceDvrSection

/**
 * SettingsMainContent.kt - Detail pane router mirroring Desktop `settings/main-content/main-content.ts`.
 */
@Composable
fun SettingsMainContent(
    state: ParentUiState,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onBrowseDownloadsFolder: () -> Unit = {},
    onSelectPairedDevice: (String) -> Unit,
    onClearSelectedPairedDevice: () -> Unit,
    onConnectPairedDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onCancelReconnect: () -> Unit,
    onClearWorkspaceHistory: () -> Unit
) {
    when (state.activeSettingsTab) {
        SettingsTabId.GENERAL -> GeneralSystemSection(
            settings = state.settings,
            onUpdateSettings = onUpdateSettings,
            onBrowseDownloadsFolder = onBrowseDownloadsFolder
        )
        SettingsTabId.AUDIO -> AudioIntercomSection(
            settings = state.settings,
            onUpdateSettings = onUpdateSettings
        )
        SettingsTabId.SURVEILLANCE -> SurveillanceDvrSection(
            settings = state.settings,
            onUpdateSettings = onUpdateSettings
        )
        SettingsTabId.DEVICES -> PairedDevicesSection(
            connectionPhase = state.connectionPhase,
            connectedDevice = state.connectedDevice,
            lastPairedDevice = state.lastPairedDevice,
            pairedDevicesHistory = state.pairedDevicesHistory,
            selectedDeviceId = state.selectedPairedDeviceId,
            connectingFromHistoryId = state.connectingFromHistoryId,
            onSelectDevice = onSelectPairedDevice,
            onClearSelectedDevice = onClearSelectedPairedDevice,
            onConnectDevice = onConnectPairedDevice,
            onDisconnectDevice = onDisconnectDevice,
            onCancelReconnect = onCancelReconnect
        )
        SettingsTabId.ABOUT -> AboutSection(
            onClearWorkspaceHistory = onClearWorkspaceHistory
        )
    }
}
