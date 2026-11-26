package com.nexus.parent.ui.monitor.staged

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexus.parent.state.FeatureId
import com.nexus.parent.ui.components.TacticalEmptyState

/**
 * StagedActivityPanel.kt - Industrial "Coming Soon" placeholder view matching Desktop
 * `whatsapp.ts`, `telegram.ts`, `chatgpt.ts`, `claude.ts`, and `grok.ts`.
 */
@Composable
fun StagedActivityPanel(
    feature: FeatureId,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        TacticalEmptyState(
            iconRes = feature.iconRes,
            title = "Coming Soon",
            description = "${feature.title} monitoring is currently under active development and will be available in an upcoming update."
        )
    }
}
