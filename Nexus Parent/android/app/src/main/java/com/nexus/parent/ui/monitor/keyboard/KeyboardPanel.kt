package com.nexus.parent.ui.monitor.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.KeyboardUiItem
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * KeyboardPanel.kt - Keystroke & Input Auditor UI (`right-sidebar/keyboard/keyboard.ts`).
 */
@Composable
fun KeyboardPanel(
    monitoringState: MonitoringState,
    searchQuery: String,
    items: List<KeyboardUiItem>,
    onSearchChange: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val q = searchQuery.trim().lowercase()
    val filtered = if (q.isEmpty()) {
        items
    } else {
        items.filter {
            it.appName.lowercase().contains(q) || it.text.lowercase().contains(q)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TacticalSearchField(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search keystrokes or apps..."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME KEYSTROKE CAPTURE • ${items.size} LOGGED",
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextMuted
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusColors.TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(4.dp))
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_keyboard,
                title = if (items.isEmpty()) "No Keystroke Activity Recorded" else "No Matching Keystrokes",
                description = if (items.isNotEmpty()) {
                    "Try searching for a different keyword or app name."
                } else if (monitoringState == MonitoringState.LIVE) {
                    "Listening for debounced text input across active applications."
                } else {
                    "Tap the Play button in the bottom dock to start real-time keyboard auditing."
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { record ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_keyboard),
                                    contentDescription = null,
                                    tint = NexusColors.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = record.appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NexusColors.TextPrimary
                                )
                            }
                            Text(
                                text = record.relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                        }

                        if (!record.fieldHint.isNullOrBlank()) {
                            Text(
                                text = record.fieldHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                                .background(NexusColors.CanvasBlack)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = record.text,
                                style = NexusTypography.MonoValue,
                                color = NexusColors.TextPrimary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TacticalBadgePill(text = record.inputType)
                            if (record.isPassword) {
                                TacticalBadgePill(
                                    text = "PASSWORD",
                                    textColor = NexusColors.DangerRed,
                                    backgroundColor = NexusColors.DangerRedSubtle,
                                    borderColor = NexusColors.DangerRedBorder
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
