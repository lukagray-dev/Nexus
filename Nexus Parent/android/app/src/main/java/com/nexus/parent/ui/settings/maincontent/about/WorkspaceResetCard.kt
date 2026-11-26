package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography
import kotlinx.coroutines.delay

/**
 * WorkspaceResetCard.kt - Two-step armed Danger Zone workspace reset card matching Desktop
 * `about/workspace-reset.ts`.
 *
 * Hey friend! Requiring two taps within 4 seconds prevents accidental wipes while allowing parents
 * to disconnect active sessions, purge paired device history, and restore all factory defaults.
 */
@Composable
fun WorkspaceResetCard(
    onClearWorkspaceHistory: () -> Unit
) {
    var isArmed by remember { mutableStateOf(false) }

    LaunchedEffect(isArmed) {
        if (isArmed) {
            delay(4000L)
            isArmed = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "LOCAL DATA & DANGER ZONE",
            style = NexusTypography.SectionHeader,
            color = NexusColors.DangerRed
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.DangerRedBorder, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Clear Local Parent Data & Reset Workspace",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Disconnects active session, clears paired device history, resets voice preset, and restores all factory defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }

            if (isArmed) {
                TacticalPrimaryButton(
                    text = "Confirm Full Reset?",
                    iconRes = R.drawable.ic_trash,
                    onClick = {
                        isArmed = false
                        onClearWorkspaceHistory()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_reset_workspace_btn")
                )
            } else {
                TacticalSecondaryButton(
                    text = "Reset Workspace",
                    iconRes = R.drawable.ic_trash,
                    tintColor = NexusColors.DangerRed,
                    borderColor = NexusColors.DangerRedBorder,
                    onClick = { isArmed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_reset_workspace_btn")
                )
            }
        }
    }
}
