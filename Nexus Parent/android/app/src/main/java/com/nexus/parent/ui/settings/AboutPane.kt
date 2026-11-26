package com.nexus.parent.ui.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * AboutPane.kt - Tab 5 of Settings Console (`about/`).
 */
@Composable
fun AboutPane(
    onResetWorkspaceToast: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_nexus_parent),
                contentDescription = "Nexus Parent",
                modifier = Modifier.size(40.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Nexus Parent (Android)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "v0.1.0-alpha • Privacy-First P2P Surveillance",
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextMuted
                )
            }
        }

        SettingsSectionGroup(title = "Runtime Environment") {
            AboutSpecRow("UI Framework", "Jetpack Compose + Material 3")
            AboutSpecRow("Video Renderer", "TextureView + EglRenderer (Zero-Blocker)")
            AboutSpecRow("Android Host", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            AboutSpecRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
        }

        SettingsSectionGroup(title = "Transport & Privacy") {
            AboutSpecRow("Media Pipeline", "End-to-End Encrypted WebRTC (SRTP)")
            AboutSpecRow("Control Channel", "SCTP DataChannel (nexus-control)")
            AboutSpecRow("Cloud Media Storage", "Zero (Direct P2P Device-to-Device)")
        }

        SettingsSectionGroup(title = "Workspace Data") {
            TacticalSecondaryButton(
                text = "Clear Cached Local UI State",
                iconRes = R.drawable.ic_trash,
                tintColor = NexusColors.DangerRed,
                borderColor = NexusColors.DangerRedBorder,
                onClick = onResetWorkspaceToast,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AboutSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextMuted)
        Text(text = value, style = NexusTypography.MonoBadge, color = NexusColors.TextPrimary)
    }
}
