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
    hasRecordAudioPermission: Boolean = true,
    onDetectOrRefreshInputs: () -> Unit = {},
    onRefreshOutputs: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onReconnectForHardwareAccel: () -> Unit = {},
    onCheckUpdatesNow: () -> Unit = {},
    onRestartToUpdate: () -> Unit = {},
    onCopySystemInfo: (String) -> Unit = {},
    onCopyAboutValue: (label: String, value: String) -> Unit = { _, _ -> },
    onOpenDownloadsFolder: () -> Unit = {},
    onOpenExternalUrl: (String) -> Unit = {},
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
            appUpdate = state.appUpdate,
            onUpdateSettings = onUpdateSettings,
            onBrowseDownloadsFolder = onBrowseDownloadsFolder,
            onCheckUpdatesNow = onCheckUpdatesNow,
            onRestartToUpdate = onRestartToUpdate,
            onShowToast = onShowToast
        )
        SettingsTabId.AUDIO -> AudioIntercomSection(
            settings = state.settings,
            availableInputs = state.availableAudioInputs,
            availableOutputs = state.availableAudioOutputs,
            hasRecordAudioPermission = hasRecordAudioPermission,
            onUpdateSettings = onUpdateSettings,
            onDetectOrRefreshInputs = onDetectOrRefreshInputs,
            onRefreshOutputs = onRefreshOutputs,
            onShowToast = onShowToast
        )
        SettingsTabId.SURVEILLANCE -> SurveillanceDvrSection(
            settings = state.settings,
            onUpdateSettings = onUpdateSettings,
            isHardwareAccelReconnectPending = state.connectionPhase == com.nexus.parent.state.ConnectionPhase.CONNECTED &&
                state.sessionHardwareAcceleration != null &&
                state.sessionHardwareAcceleration != state.settings.hardwareAcceleration,
            onReconnectForHardwareAccel = onReconnectForHardwareAccel,
            onShowToast = onShowToast
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
            state = state,
            onCopySystemInfo = onCopySystemInfo,
            onCopyValue = onCopyAboutValue,
            onOpenDownloadsFolder = onOpenDownloadsFolder,
            onCheckUpdatesNow = onCheckUpdatesNow,
            onRestartToUpdate = onRestartToUpdate,
            onOpenUrl = onOpenExternalUrl,
            onClearWorkspaceHistory = onClearWorkspaceHistory
        )
    }
}
