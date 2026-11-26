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

/**
 * AboutSection.kt - Tab 5 of Settings Console (`about/about.ts`).
 */
@Composable
fun AboutSection(
    onClearWorkspaceHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ParentIdentityCard()
        RuntimeEnvironmentCard()
        TransportStorageCard()
        WorkspaceResetCard(onClearWorkspaceHistory = onClearWorkspaceHistory)
    }
}
