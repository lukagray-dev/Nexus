package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * SafetyAlertsSetting.kt - Matches Desktop `general-system/desktop-alerts.ts`.
 */
@Composable
fun SafetyAlertsSetting(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsSectionGroup(title = "Notifications & Safety Alerts") {
        SettingsToggleRow(
            title = "Active Cellular Call Alert",
            subtitle = "Show floating banner with 1-click Mic Listen when child enters a call.",
            checked = settings.alertChildActiveCall,
            onCheckedChange = { v -> onUpdateSettings { it.copy(alertChildActiveCall = v) } }
        )
        SettingsToggleRow(
            title = "Intruder Selfie Alerts",
            subtitle = "Notify immediately when failed App Lock PIN attempts trigger a front selfie.",
            checked = settings.alertIntruderSelfie,
            onCheckedChange = { v -> onUpdateSettings { it.copy(alertIntruderSelfie = v) } }
        )
        SettingsToggleRow(
            title = "Low Battery Alerts",
            subtitle = "Alert when child device battery drops below 15%.",
            checked = settings.alertLowBattery,
            onCheckedChange = { v -> onUpdateSettings { it.copy(alertLowBattery = v) } }
        )
        SettingsToggleRow(
            title = "Child Disconnection Alerts",
            subtitle = "Notify if WebRTC link or Firebase presence drops unexpectedly.",
            checked = settings.alertChildDisconnected,
            onCheckedChange = { v -> onUpdateSettings { it.copy(alertChildDisconnected = v) } }
        )
    }
}
