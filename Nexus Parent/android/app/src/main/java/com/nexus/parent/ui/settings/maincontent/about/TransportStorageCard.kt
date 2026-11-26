package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.runtime.Composable
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.settings.maincontent.SettingsSpecRow

/**
 * TransportStorageCard.kt - Matches Desktop `about/transport-storage.ts`.
 */
@Composable
fun TransportStorageCard() {
    SettingsSectionGroup(title = "Transport & Privacy") {
        SettingsSpecRow("Media Pipeline", "End-to-End Encrypted WebRTC (SRTP)")
        SettingsSpecRow("Control Channel", "SCTP DataChannel (nexus-control)")
        SettingsSpecRow("ICE Mode", "Non-Trickle GATHER_ONCE (Signaling RTDB)")
        SettingsSpecRow("Cloud Media Storage", "Zero (Direct P2P Device-to-Device)")
    }
}
