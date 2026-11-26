package com.nexus.parent.ui.settings.maincontent.about

import android.os.Build
import androidx.compose.runtime.Composable
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.settings.maincontent.SettingsSpecRow

/**
 * RuntimeEnvironmentCard.kt - Matches Desktop `about/runtime-environment.ts`.
 */
@Composable
fun RuntimeEnvironmentCard() {
    SettingsSectionGroup(title = "Runtime Environment") {
        SettingsSpecRow("UI Framework", "Jetpack Compose + Material 3")
        SettingsSpecRow("Video Renderer", "TextureView + EglRenderer (Zero-Blocker)")
        SettingsSpecRow("Android Host", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        SettingsSpecRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
    }
}
