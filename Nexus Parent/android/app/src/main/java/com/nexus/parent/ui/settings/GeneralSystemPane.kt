package com.nexus.parent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * GeneralSystemPane.kt - Tab 1 of Settings Console (`general-system/`).
 */
@Composable
fun GeneralSystemPane(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Connection & Display") {
            SettingsToggleRow(
                title = "Auto-Connect Last Paired Device",
                subtitle = "Automatically resume WebRTC session with the last child ID at app launch.",
                checked = settings.autoConnectLastDevice,
                onCheckedChange = { v -> onUpdateSettings { it.copy(autoConnectLastDevice = v) } }
            )
            SettingsToggleRow(
                title = "Keep Screen Awake During Live Feeds",
                subtitle = "Prevents device sleep while monitoring Camera, Screen, or Location.",
                checked = settings.keepScreenAwake,
                onCheckedChange = { v -> onUpdateSettings { it.copy(keepScreenAwake = v) } }
            )
        }

        SettingsSectionGroup(title = "Downloads & Storage") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.SurfaceCard)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Default Downloads Directory",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = settings.downloadsDirectoryLabel,
                    style = NexusTypography.MonoValue,
                    color = NexusColors.TextSecondary
                )
            }
            SettingsToggleRow(
                title = "Ask Before Download",
                subtitle = "Prompt for folder selection before saving remote files or DVR clips.",
                checked = settings.askBeforeDownload,
                onCheckedChange = { v -> onUpdateSettings { it.copy(askBeforeDownload = v) } }
            )
        }

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
}
