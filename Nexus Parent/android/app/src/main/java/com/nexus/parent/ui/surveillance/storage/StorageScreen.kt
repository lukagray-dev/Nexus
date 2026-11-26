package com.nexus.parent.ui.surveillance.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.nexus.parent.state.StorageCategory
import com.nexus.parent.state.StorageFileItem
import com.nexus.parent.state.StorageUiState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalModalDialog
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * StorageScreen.kt - Remote Storage Explorer UI matching Desktop `storage.ts`.
 */
@Composable
fun StorageScreen(
    state: StorageUiState,
    onRefreshClick: () -> Unit,
    onParentDirClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (StorageCategory) -> Unit,
    onSelectItem: (StorageFileItem?) -> Unit,
    onDownloadItem: (StorageFileItem) -> Unit,
    onDeleteItem: (StorageFileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredItems = state.items.filter { item ->
        val matchesCat = state.selectedCategory == StorageCategory.ALL || item.category == state.selectedCategory
        val q = state.searchQuery.trim().lowercase()
        val matchesQuery = q.isEmpty() || item.name.lowercase().contains(q)
        matchesCat && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
            .statusBarsPadding()
            .padding(top = 64.dp)
            .navigationBarsPadding()
    ) {
        // 1. Navigation & Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.breadcrumbs.forEachIndexed { idx, crumb ->
                    if (idx > 0) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = NexusColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = crumb,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (idx == state.breadcrumbs.lastIndex) NexusColors.TextPrimary else NexusColors.TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onBreadcrumbClick(idx) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TacticalSecondaryButton(
                    text = "Up",
                    iconRes = R.drawable.ic_arrow_left,
                    enabled = state.breadcrumbs.size > 1,
                    onClick = onParentDirClick
                )
                TacticalSecondaryButton(
                    text = "Refresh",
                    iconRes = R.drawable.ic_refresh_cw,
                    onClick = onRefreshClick
                )
            }
        }

        // 2. Search Filter
        TacticalSearchField(
            query = state.searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Filter files by name or type...",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // 3. Category Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StorageCategory.entries.forEach { cat ->
                val isSelected = state.selectedCategory == cat
                val count = if (cat == StorageCategory.ALL) {
                    state.items.size
                } else {
                    state.items.count { it.category == cat }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .background(if (isSelected) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                        .border(
                            NexusDimens.BorderHairline,
                            if (isSelected) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                            RoundedCornerShape(NexusDimens.RadiusCapsule)
                        )
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

        // 4. Storage Usage Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusColors.SurfaceSidebar)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORAGE USAGE • ${state.storageUsageText}",
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextSecondary
                )
                Text(
                    text = "${filteredItems.size} items",
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NexusColors.SurfacePressed)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.storageUsedPercent.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(NexusColors.AccentWhite)
                )
            }
        }

        HorizontalDivider(color = NexusColors.BorderSubtle)

        // 5. File List / Empty State
        if (filteredItems.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_hard_drive,
                title = if (state.items.isEmpty()) "Remote Storage Standby" else "No Matching Files",
                description = if (state.items.isEmpty()) {
                    "Directory entries, thumbnails, and 64KB file streams appear here once synchronized with the child device."
                } else {
                    "Try clearing the search query or switching the category filter."
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredItems, key = { it.path }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onSelectItem(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file_text
                                ),
                                contentDescription = null,
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.sizeLabel} • ${item.modifiedLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusColors.TextMuted
                                )
                            }
                        }
                        TacticalBadgePill(text = item.category.label.uppercase())
                    }
                }
            }
        }
    }

    // 6. File Inspector Dialog
    val selected = state.selectedItem
    if (selected != null) {
        TacticalModalDialog(
            title = selected.name,
            onDismiss = { onSelectItem(null) },
            footer = {
                TacticalSecondaryButton(
                    text = "Delete",
                    iconRes = R.drawable.ic_trash,
                    tintColor = NexusColors.DangerRed,
                    borderColor = NexusColors.DangerRedBorder,
                    onClick = {
                        onDeleteItem(selected)
                        onSelectItem(null)
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                TacticalPrimaryButton(
                    text = "Download",
                    iconRes = R.drawable.ic_download,
                    onClick = {
                        onDownloadItem(selected)
                        onSelectItem(null)
                    }
                )
            }
        ) {
            Text(
                text = "Path: ${selected.path}",
                style = NexusTypography.MonoValue,
                color = NexusColors.TextSecondary
            )
            Text(
                text = "Size: ${selected.sizeLabel} • Modified: ${selected.modifiedLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted
            )
        }
    }
}
