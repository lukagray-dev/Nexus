package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.runtime.Composable
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow

/**
 * KeepScreenAwakeSetting.kt - Matches Desktop `general-system/keep-screen-awake.ts`.
 */
@Composable
fun KeepScreenAwakeSetting(
    checked: Boolean,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    SettingsToggleRow(
        title = "Keep Screen Awake During Live Feeds",
        subtitle = "Prevents device sleep while monitoring Camera, Screen, or Location.",
        checked = checked,
        onCheckedChange = { value ->
            onUpdateSettings { it.copy(keepScreenAwake = value) }
        }
    )
}
