package com.nexus.parent.ui.monitor.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nexus.parent.state.SmsUiItem
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalModalDialog
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * SmsPanel.kt - SMS Monitor UI matching Desktop `right-sidebar/sms/sms.ts`.
 */
@Composable
fun SmsPanel(
    monitoringState: MonitoringState,
    searchQuery: String,
    items: List<SmsUiItem>,
    selectedSms: SmsUiItem?,
    onSearchChange: (String) -> Unit,
    onSelectSms: (SmsUiItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = searchQuery.trim().lowercase()
    val filtered = if (q.isEmpty()) {
        items
    } else {
        items.filter {
            it.senderName.lowercase().contains(q) ||
                it.address.lowercase().contains(q) ||
                it.body.lowercase().contains(q)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TacticalSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Search SMS messages...",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )

        if (filtered.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_message_square,
                title = if (items.isEmpty()) "No SMS Messages" else "No Search Matches",
                description = when {
                    items.isNotEmpty() -> "No SMS messages matched \"$searchQuery\"."
                    monitoringState == MonitoringState.LIVE ->
                        "Listening for SMS in real time. Incoming and outgoing text messages will appear here automatically."
                    else ->
                        "Tap the Play button in the bottom dock to fetch latest 100 SMS messages and begin live monitoring."
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
                items(filtered, key = { it.id }) { sms ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onSelectSms(sms) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sms.senderName.ifBlank { sms.address },
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = sms.relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                        }
                        Text(
                            text = sms.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TacticalBadgePill(
                                text = if (sms.isIncoming) "INCOMING" else "OUTGOING",
                                textColor = if (sms.isIncoming) NexusColors.LiveGreen else NexusColors.TextSecondary
                            )
                            if (sms.isUnread && sms.isIncoming) {
                                TacticalBadgePill(text = "UNREAD", textColor = NexusColors.WarningAmber)
                            }
                            if (sms.simSlot.isNotBlank()) {
                                TacticalBadgePill(text = sms.simSlot)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedSms != null) {
        val statusLabel = if (selectedSms.isIncoming) "Delivered to Child" else "Sent from Child"
        val simLabel = selectedSms.simSlot.ifBlank { "Default SIM" }
        TacticalModalDialog(
            title = selectedSms.senderName.ifBlank { selectedSms.address },
            onDismiss = { onSelectSms(null) },
            footer = {
                TacticalPrimaryButton(text = "Close", onClick = { onSelectSms(null) })
            }
        ) {
            Text(
                text = selectedSms.address,
                style = MaterialTheme.typography.labelSmall,
                color = NexusColors.TextMuted
            )
            Text(
                text = selectedSms.body,
                style = MaterialTheme.typography.bodyMedium,
                color = NexusColors.TextPrimary
            )
            Text(
                text = "Received: ${selectedSms.fullDateTime}",
                style = MaterialTheme.typography.labelSmall,
                color = NexusColors.TextMuted
            )
            Text(
                text = "Status & SIM: $statusLabel • $simLabel",
                style = MaterialTheme.typography.labelSmall,
                color = NexusColors.TextMuted
            )
        }
    }
}
