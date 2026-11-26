package com.nexus.parent.ui.monitor.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.WellbeingBucket
import com.nexus.parent.state.WellbeingCategoryFilter
import com.nexus.parent.state.WellbeingRange
import com.nexus.parent.state.WellbeingUiState
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * WellbeingPanel.kt - Digital Wellbeing Monitor UI (`right-sidebar/wellbeing/wellbeing.ts`).
 */
@Composable
fun WellbeingPanel(
    state: WellbeingUiState,
    onSelectRange: (WellbeingRange) -> Unit,
    onRefreshClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (WellbeingCategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = state.searchQuery.trim().lowercase()
    val filteredApps = state.apps.filter { app ->
        val catOk = state.categoryFilter == WellbeingCategoryFilter.ALL || app.category == state.categoryFilter
        val queryOk = q.isEmpty() ||
            app.appName.lowercase().contains(q) ||
            app.packageName.lowercase().contains(q)
        catOk && queryOk
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Controls & KPI Summary Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                        .background(NexusColors.SurfaceCard)
                        .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    WellbeingRange.entries.forEach { range ->
                        val active = state.range == range
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (active) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                                .clickable { onSelectRange(range) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary
                            )
                        }
                    }
                }

                TacticalSecondaryButton(
                    text = "Refresh",
                    iconRes = R.drawable.ic_refresh_cw,
                    onClick = onRefreshClick
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                        .background(NexusColors.DangerRedSubtle)
                        .border(NexusDimens.BorderHairline, NexusColors.DangerRedBorder, RoundedCornerShape(NexusDimens.RadiusMd))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.DangerRed
                    )
                }
            }

            // 2x2 KPI Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WellbeingKpiCard("SCREEN TIME", state.totalScreenTime, Modifier.weight(1f))
                WellbeingKpiCard("UNLOCKS", state.unlockCount.toString(), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WellbeingKpiCard("LAUNCHES", state.totalLaunches.toString(), Modifier.weight(1f))
                WellbeingKpiCard(
                    label = if (state.syncedLabel != null) "ACTIVE (${state.syncedLabel})" else "ACTIVE APP",
                    value = state.activeAppName,
                    modifier = Modifier.weight(1f)
                )
            }

            // Activity Histogram
            WellbeingHistogram(buckets = state.buckets)

            TacticalSearchField(
                query = state.searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search apps or packages..."
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WellbeingCategoryFilter.entries.forEach { cat ->
                    val isSelected = state.categoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                            .background(if (isSelected) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                            .border(
                                NexusDimens.BorderHairline,
                                if (isSelected) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                                RoundedCornerShape(NexusDimens.RadiusCapsule)
                            )
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) NexusColors.TextInverse else NexusColors.TextSecondary
                        )
                    }
                }
            }
        }

        // 2. App Breakdown List
        if (filteredApps.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_activity,
                title = when {
                    !state.errorMessage.isNullOrBlank() -> "Usage Access Required"
                    state.apps.isNotEmpty() -> "No Matching Apps"
                    else -> "No Wellbeing Telemetry"
                },
                description = state.errorMessage
                    ?: if (state.apps.isNotEmpty()) {
                        "Try clearing the search query or switching category filters."
                    } else {
                        "Tap the Play button in the bottom dock to sync screen time, device unlocks, and per-app usage histograms."
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
                items(filteredApps, key = { it.packageName }) { app ->
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
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${app.durationLabel} (${app.percent}%)",
                                style = NexusTypography.MonoBadge,
                                color = NexusColors.TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NexusColors.SurfacePressed)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((app.percent / 100f).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(NexusColors.AccentWhite)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WellbeingKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = NexusTypography.SectionHeader,
            color = NexusColors.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = NexusColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WellbeingHistogram(buckets: List<WellbeingBucket>) {
    val bars = if (buckets.isEmpty()) List(12) { 0.08f } else buckets.map { it.normalizedValue }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = bars.size.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
            bars.forEachIndexed { idx, value ->
                val h = (size.height * value.coerceIn(0.08f, 1f))
                val x = idx * (barWidth + gap)
                val y = size.height - h
                drawRoundRect(
                    color = if (buckets.isEmpty()) NexusColors.SurfacePressed else NexusColors.AccentWhite,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
        }
    }
}
