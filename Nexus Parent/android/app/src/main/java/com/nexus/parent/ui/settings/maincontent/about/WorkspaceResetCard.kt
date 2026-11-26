package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexus.parent.R
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.theme.NexusColors

/**
 * WorkspaceResetCard.kt - Matches Desktop `about/workspace-reset.ts`.
 */
@Composable
fun WorkspaceResetCard(
    onClearWorkspaceHistory: () -> Unit
) {
    SettingsSectionGroup(title = "Workspace Data") {
        TacticalSecondaryButton(
            text = "Clear Paired Device History & Cache",
            iconRes = R.drawable.ic_trash,
            tintColor = NexusColors.DangerRed,
            borderColor = NexusColors.DangerRedBorder,
            onClick = onClearWorkspaceHistory,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
