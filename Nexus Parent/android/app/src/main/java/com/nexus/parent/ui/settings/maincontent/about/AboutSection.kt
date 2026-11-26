package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.ParentUiState

/**
 * AboutSection.kt - Tab 5 of Settings Console (`about/about.ts`).
 *
 * Hey friend! Mirrors Desktop's 5-group About page:
 * 1. Application Identity & Releases (`ParentIdentityCard`)
 * 2. System & Runtime Environment (`RuntimeEnvironmentCard`)
 * 3. Transport, Security & Session Telemetry (`TransportStorageCard`)
 * 4. Credits, License & Links (`CreditsLinksCard`)
 * 5. Local Data & Danger Zone (`WorkspaceResetCard`)
 */
@Composable
fun AboutSection(
    state: ParentUiState,
    onCopySystemInfo: (String) -> Unit,
    onCopyValue: (label: String, value: String) -> Unit,
    onOpenDownloadsFolder: () -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onRestartToUpdate: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearWorkspaceHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ParentIdentityCard(
            state = state,
            onCopySystemInfo = onCopySystemInfo,
            onCheckUpdatesNow = onCheckUpdatesNow,
            onRestartToUpdate = onRestartToUpdate
        )
        RuntimeEnvironmentCard(
            state = state,
            onCopyValue = onCopyValue,
            onOpenDownloadsFolder = onOpenDownloadsFolder
        )
        TransportStorageCard(state = state)
        CreditsLinksCard(onOpenUrl = onOpenUrl)
        WorkspaceResetCard(onClearWorkspaceHistory = onClearWorkspaceHistory)
    }
}
