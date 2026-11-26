package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography
import com.nexus.parent.webrtc.AppConfig

/**
 * TransportStorageCard.kt - Live WebRTC P2P transport, Firebase auth, measured storage footprint,
 * and active session summary matching Desktop `about/transport-storage.ts`.
 */
@Composable
fun TransportStorageCard(state: ParentUiState) {
    val projectId = AppConfig.Firebase.PROJECT_ID.ifBlank { "Unconfigured" }
    val authText = "$projectId • ${if (state.isFirebaseSignedIn) "Signed In" else "Standby"}"
    val storageText = "${state.localStorageMetrics.formattedSize} (${state.localStorageMetrics.entryCount} keys)"

    val (sessionBadge, badgeColor, sessionDesc) = when (state.connectionPhase) {
        ConnectionPhase.CONNECTED -> {
            val dev = state.connectedDevice
            val desc = if (dev != null) {
                "Live P2P link with ${dev.name} (${dev.formattedId})"
            } else {
                "Live encrypted P2P session active"
            }
            Triple("Connected", NexusColors.LiveGreen, desc)
        }
        ConnectionPhase.CONNECTING -> Triple(
            "Connecting",
            NexusColors.TextPrimary,
            state.connectingProgressMessage ?: "Establishing P2P WebRTC connection"
        )
        ConnectionPhase.RECONNECTING -> {
            val target = state.lastPairedDevice
            val desc = if (target != null) {
                "Attempting recovery with ${target.name} (${state.reconnectAttempt}/${state.reconnectMaxAttempts})"
            } else {
                "Attempting WebRTC peer recovery"
            }
            Triple("Reconnecting", NexusColors.WarningAmber, desc)
        }
        ConnectionPhase.DISCONNECTED -> {
            val last = state.lastPairedDevice
            val desc = if (last != null) {
                "Last paired device: ${last.name} (${last.formattedId})"
            } else {
                "No child device currently linked"
            }
            Triple("Standby", NexusColors.TextMuted, desc)
        }
    }

    SettingsSectionGroup(title = "Transport, Security & Session Telemetry") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportTile("P2P ENCRYPTION", "DTLS-SRTP / SCTP", Modifier.weight(1f))
            TransportTile("ICE DISCOVERY", "Non-Trickle (${AppConfig.WebRtc.STUN_SERVERS.size} STUN)", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportTile("SIGNALING AUTH", authText, Modifier.weight(1f))
            TransportTile("LOCAL STORAGE", storageText, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Active Parent Session & Remembered Device",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = sessionDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }
            TacticalBadgePill(
                text = sessionBadge,
                textColor = badgeColor,
                backgroundColor = NexusColors.SurfaceSidebar
            )
        }
    }
}

@Composable
private fun TransportTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = NexusTypography.SectionHeader, color = NexusColors.TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextPrimary)
    }
}
