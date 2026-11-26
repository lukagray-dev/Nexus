package com.nexus.parent.ui.monitor.calllog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.CallFilter
import com.nexus.parent.state.CallLogUiItem
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * CallLogPanel.kt - Call Log Monitor UI matching Desktop `right-sidebar/calllog/calllog.ts`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallLogPanel(
    monitoringState: MonitoringState,
    activeFilter: CallFilter,
    expandedCallId: String?,
    items: List<CallLogUiItem>,
    onSelectFilter: (CallFilter) -> Unit,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = if (activeFilter == CallFilter.ALL) {
        items
    } else {
        items.filter { it.filterType == activeFilter }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CallFilter.entries.forEach { filter ->
                val count = if (filter == CallFilter.ALL) items.size else items.count { it.filterType == filter }
                val isSelected = activeFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .background(if (isSelected) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                        .border(
                            NexusDimens.BorderHairline,
                            if (isSelected) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                            RoundedCornerShape(NexusDimens.RadiusCapsule)
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NexusColors.TextInverse else NexusColors.TextSecondary
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_phone_call,
                title = "No Call History",
                description = if (monitoringState == MonitoringState.LIVE) {
                    "Listening for calls in real time. Incoming and outgoing calls will appear here automatically."
                } else {
                    "Tap the Play button in the bottom dock to fetch latest 100 call records and begin live monitoring."
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
                items(filtered, key = { it.id }) { call ->
                    val isMissed = call.filterType == CallFilter.MISSED || call.filterType == CallFilter.DECLINED
                    val iconRes = when (call.filterType) {
                        CallFilter.INCOMING -> R.drawable.ic_phone_incoming
                        CallFilter.OUTGOING -> R.drawable.ic_phone_outgoing
                        else -> R.drawable.ic_phone_missed
                    }
                    val isExpanded = expandedCallId == call.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onToggleExpand(call.id) }
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = if (isMissed) NexusColors.DangerRed else NexusColors.TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = call.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NexusColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${call.number} • ${call.numberType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusColors.TextMuted
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = call.relativeTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusColors.TextMuted
                                )
                                TacticalBadgePill(
                                    text = call.durationLabel,
                                    textColor = if (isMissed) NexusColors.DangerRed else NexusColors.TextSecondary
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (call.simSlot.isNotBlank()) {
                                        TacticalBadgePill(text = call.simSlot)
                                    }
                                    if (!call.location.isNullOrBlank()) {
                                        TacticalBadgePill(text = call.location)
                                    }
                                    if (call.features.any { it.equals("VIDEO", ignoreCase = true) }) {
                                        TacticalBadgePill(text = "VIDEO")
                                    }
                                    if (call.features.any { it.equals("HD_VOICE", ignoreCase = true) }) {
                                        TacticalBadgePill(text = "HD")
                                    }
                                    if (call.directionLabel.isNotBlank()) {
                                        TacticalBadgePill(text = call.directionLabel.uppercase())
                                    }
                                }
                                Text(
                                    text = "${call.fullDateTime} • ${call.durationSeconds}s (${call.durationLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusColors.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
