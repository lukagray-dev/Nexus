package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * AutoConnectLastDeviceSetting.kt - Matches Desktop `general-system/auto-connect-last-device.ts`.
 */
@Composable
fun AutoConnectLastDeviceSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsToggleRow(
        title = "Auto-Connect Last Paired Device",
        subtitle = "Automatically resume WebRTC session with the last child ID at app launch.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(autoConnectLastDevice = value) }
        }
    )
}
