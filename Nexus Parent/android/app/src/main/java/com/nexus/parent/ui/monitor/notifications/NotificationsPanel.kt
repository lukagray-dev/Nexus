package com.nexus.parent.ui.monitor.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.NotificationFilter
import com.nexus.parent.state.NotificationUiItem
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalModalDialog
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * NotificationsPanel.kt - Notification Interceptor UI (`right-sidebar/notification/notification.ts`).
 */
@Composable
fun NotificationsPanel(
    monitoringState: MonitoringState,
    searchQuery: String,
    activeFilter: NotificationFilter,
    items: List<NotificationUiItem>,
    selectedNotification: NotificationUiItem?,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (NotificationFilter) -> Unit,
    onSelectNotification: (NotificationUiItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = searchQuery.trim().lowercase()
    val filtered = items.filter { item ->
        val catOk = activeFilter == NotificationFilter.ALL || item.category == activeFilter
        val queryOk = q.isEmpty() ||
            item.appName.lowercase().contains(q) ||
            item.title.lowercase().contains(q) ||
            item.body.lowercase().contains(q)
        catOk && queryOk
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TacticalSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Search notifications...",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NotificationFilter.entries.forEach { cat ->
                val count = if (cat == NotificationFilter.ALL) items.size else items.count { it.category == cat }
                val isSelected = activeFilter == cat
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .background(if (isSelected) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                        .border(
                            NexusDimens.BorderHairline,
                            if (isSelected) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                            RoundedCornerShape(NexusDimens.RadiusCapsule)
                        )
                        .clickable { onSelectFilter(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NexusColors.TextInverse else NexusColors.TextSecondary
                    )
                    Text(
                        text = count.toString(),
                        style = NexusTypography.MonoBadge,
                        color = if (isSelected) NexusColors.TextInverse else NexusColors.TextMuted
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_bell,
                title = "No Notifications",
                description = if (monitoringState == MonitoringState.LIVE) {
                    "Listening for status bar notifications in real time."
                } else {
                    "Tap the Play button in the bottom dock to sync active status bar alerts and stream live notifications."
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.key }) { notif ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onSelectNotification(notif) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TacticalBadgePill(text = notif.appName.uppercase())
                            Text(
                                text = notif.relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                        }
                        Text(
                            text = notif.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = notif.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (selectedNotification != null) {
        TacticalModalDialog(
            title = selectedNotification.appName,
            onDismiss = { onSelectNotification(null) },
            footer = {
                TacticalPrimaryButton(text = "Close", onClick = { onSelectNotification(null) })
            }
        ) {
            Text(
                text = selectedNotification.title,
                style = MaterialTheme.typography.titleMedium,
                color = NexusColors.TextPrimary
            )
            Text(
                text = "${selectedNotification.packageName} • ${selectedNotification.fullDateTime}",
                style = MaterialTheme.typography.labelSmall,
                color = NexusColors.TextMuted
            )
            if (!selectedNotification.subText.isNullOrBlank()) {
                TacticalBadgePill(text = selectedNotification.subText)
            }
            Text(
                text = selectedNotification.bigText ?: selectedNotification.body,
                style = MaterialTheme.typography.bodyMedium,
                color = NexusColors.TextSecondary
            )
            if (selectedNotification.messages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedNotification.messages.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
