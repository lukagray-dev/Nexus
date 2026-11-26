package com.nexus.parent.ui.surveillance.storage

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.parent.R
import com.nexus.parent.data.storage.StorageProtocol
import com.nexus.parent.state.StorageCategory
import com.nexus.parent.state.StorageFileItem
import com.nexus.parent.state.StorageTransferProgress
import com.nexus.parent.state.StorageUiState
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalModalDialog
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.monitor.MonitorImageHelpers
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * StorageScreen.kt - Live Remote Storage Explorer UI matching Desktop `storage.ts`.
 *
 * Hey friend! Supports every Desktop storage capability adapted cleanly for mobile touchscreens:
 * - Live capacity strip bound to Child's `totalStorageBytes` & `freeStorageBytes`.
 * - Instant cached folder loads + background `STORAGE_CHECK_DIR` validation + `Load More` pagination.
 * - Image/video thumbnail previews in rows + Inspector modal (`View Full Image`, `Download`, `Delete`).
 * - Active 64KB chunk transfer progress bar with one-tap Cancel, full-resolution Image Preview dialog,
 *   and Tactical Delete Confirmation modal before permanently removing any remote file.
 */
@Composable
fun StorageScreen(
    state: StorageUiState,
    onRefreshClick: () -> Unit,
    onParentDirClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (StorageCategory) -> Unit,
    onOpenDirectory: (StorageFileItem) -> Unit,
    onSelectItem: (StorageFileItem?) -> Unit,
    onLoadMoreClick: () -> Unit,
    onLoadFullImage: (StorageFileItem) -> Unit,
    onCloseImagePreview: () -> Unit,
    onDownloadPreviewedImage: () -> Unit,
    onDownloadItem: (StorageFileItem) -> Unit,
    onRequestDelete: (StorageFileItem) -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredItems = remember(state.items, state.selectedCategory, state.searchQuery) {
        val q = state.searchQuery.trim().lowercase()
        state.items.filter { item ->
            val matchesCat = when (state.selectedCategory) {
                StorageCategory.ALL -> true
                else -> !item.isDirectory && item.category == state.selectedCategory
            }
            val matchesQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.extension.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }

    val visibleCategoryTabs = remember {
        StorageCategory.entries.filter { it != StorageCategory.OTHER }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
            .statusBarsPadding()
            .padding(top = 64.dp)
            .navigationBarsPadding()
    ) {
        // 1. Breadcrumbs & Up/Refresh Actions
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
                        text = crumb.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (idx == state.breadcrumbs.lastIndex) NexusColors.TextPrimary else NexusColors.TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onBreadcrumbClick(idx) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("storage_crumb_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TacticalSecondaryButton(
                    text = "Up",
                    iconRes = R.drawable.ic_arrow_left,
                    enabled = state.breadcrumbs.size > 1,
                    onClick = onParentDirClick,
                    modifier = Modifier.testTag("storage_up_btn")
                )
                TacticalSecondaryButton(
                    text = "Refresh",
                    iconRes = R.drawable.ic_refresh_cw,
                    onClick = onRefreshClick,
                    modifier = Modifier.testTag("storage_refresh_btn")
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

        // 3. Category Pills (matching Desktop's 6 categories)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleCategoryTabs.forEach { cat ->
                val isSelected = state.selectedCategory == cat
                val count = if (cat == StorageCategory.ALL) {
                    state.items.size
                } else {
                    state.items.count { !it.isDirectory && it.category == cat }
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
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("storage_cat_${cat.name.lowercase()}"),
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
                    color = NexusColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val totalSuffix = if (state.totalCount > filteredItems.size) " of ${state.totalCount}" else ""
                Text(
                    text = "${filteredItems.size}$totalSuffix items",
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

        // 5. Active File Transfer Progress Card (64KB Chunk Streaming + Cancel)
        val transfer = state.activeTransfer
        if (transfer != null) {
            ActiveTransferStrip(
                transfer = transfer,
                onCancelClick = onCancelTransfer
            )
        }

        HorizontalDivider(color = NexusColors.BorderSubtle)

        // 6. Main Explorer Content: Loading / Error / File Rows / Load More Footer
        when {
            state.isLoadingDirectory -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpinningLoaderIcon(size = 22.dp, tint = NexusColors.TextPrimary)
                        Text(
                            text = "Fetching directory contents from child...",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextSecondary
                        )
                    }
                }
            }

            state.errorMessage != null && filteredItems.isEmpty() -> {
                TacticalEmptyState(
                    iconRes = R.drawable.ic_hard_drive,
                    title = "Directory Access Unavailable",
                    description = state.errorMessage,
                    modifier = Modifier.weight(1f)
                )
            }

            filteredItems.isEmpty() -> {
                TacticalEmptyState(
                    iconRes = R.drawable.ic_hard_drive,
                    title = if (state.items.isEmpty()) "Folder Empty or Standby" else "No Matching Files",
                    description = if (state.items.isEmpty()) {
                        "No entries returned for ${state.currentPath}. Tap Refresh or navigate via breadcrumbs."
                    } else {
                        "Try clearing the search query or switching the category filter."
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredItems, key = { it.path }) { item ->
                        StorageEntryRow(
                            item = item,
                            isSelected = state.selectedItem?.path == item.path,
                            onRowClick = {
                                if (item.isDirectory) {
                                    onOpenDirectory(item)
                                } else {
                                    onSelectItem(item)
                                }
                            },
                            onInspectClick = { onSelectItem(item) },
                            onDownloadClick = { onDownloadItem(item) },
                            onDeleteClick = { onRequestDelete(item) }
                        )
                    }

                    if (state.hasMorePages) {
                        item(key = "__load_more__") {
                            LoadMoreFilesFooter(
                                loadedCount = state.items.size,
                                totalCount = state.totalCount,
                                isLoadingMore = state.isLoadingMore,
                                onLoadMoreClick = onLoadMoreClick
                            )
                        }
                    }
                }
            }
        }
    }

    // 7. File Metadata Inspector Dialog
    val selected = state.selectedItem
    if (selected != null) {
        StorageInspectorDialog(
            item = selected,
            isImagePreviewCached = state.isImagePreviewCached,
            onDismiss = { onSelectItem(null) },
            onLoadFullImage = { onLoadFullImage(selected) },
            onDownloadItem = {
                onDownloadItem(selected)
                onSelectItem(null)
            },
            onRequestDelete = {
                onSelectItem(null)
                onRequestDelete(selected)
            }
        )
    }

    // 8. Tactical Delete Confirmation Modal
    val deleteTarget = state.deleteConfirmTarget
    if (deleteTarget != null) {
        TacticalModalDialog(
            title = "Delete Remote File",
            onDismiss = onDismissDeleteConfirm,
            footer = {
                TacticalSecondaryButton(
                    text = "Cancel",
                    onClick = onDismissDeleteConfirm,
                    modifier = Modifier.testTag("storage_delete_cancel_btn")
                )
                Spacer(modifier = Modifier.width(10.dp))
                TacticalPrimaryButton(
                    text = "Confirm Delete",
                    iconRes = R.drawable.ic_trash,
                    onClick = onConfirmDelete,
                    modifier = Modifier.testTag("storage_delete_confirm_btn")
                )
            }
        ) {
            Text(
                text = "Are you sure you want to permanently delete this item from the child device?",
                style = MaterialTheme.typography.bodyMedium,
                color = NexusColors.TextPrimary
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                    .background(NexusColors.SurfaceCard)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = deleteTarget.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = deleteTarget.path,
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextMuted
                )
            }
            Text(
                text = "This action cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.DangerRed
            )
        }
    }

    // 9. High-Resolution Full Image Preview Modal
    val previewItem = state.imagePreviewItem
    val previewLocalPath = state.imagePreviewLocalPath
    if (previewItem != null && previewLocalPath != null) {
        FullImagePreviewDialog(
            item = previewItem,
            localFilePath = previewLocalPath,
            onDownloadImage = onDownloadPreviewedImage,
            onDismiss = onCloseImagePreview
        )
    }
}

@Composable
private fun ActiveTransferStrip(
    transfer: StorageTransferProgress,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusColors.SurfaceCard)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val verb = if (transfer.purpose == StorageProtocol.PURPOSE_PREVIEW) "Streaming preview" else "Downloading"
                Text(
                    text = "$verb: ${transfer.fileName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${StorageProtocol.formatBytes(transfer.receivedBytes)} / ${StorageProtocol.formatBytes(transfer.totalBytes)} (${transfer.progressPercent}%)",
                    style = NexusTypography.MonoBadge,
                    color = NexusColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TacticalSecondaryButton(
                text = "Cancel",
                iconRes = R.drawable.ic_close,
                tintColor = NexusColors.DangerRed,
                borderColor = NexusColors.DangerRedBorder,
                onClick = onCancelClick,
                modifier = Modifier.testTag("storage_cancel_transfer_btn")
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
                    .fillMaxWidth((transfer.progressPercent / 100f).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(NexusColors.AccentWhite)
            )
        }
    }
}

@Composable
private fun StorageEntryRow(
    item: StorageFileItem,
    isSelected: Boolean,
    onRowClick: () -> Unit,
    onInspectClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val thumbnailBitmap = remember(item.path, item.thumbnailBase64) {
        MonitorImageHelpers.decodeImageBitmapFromBase64(item.thumbnailBase64)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(if (isSelected) NexusColors.SurfacePressed else NexusColors.SurfaceCard)
            .border(
                NexusDimens.BorderHairline,
                if (isSelected) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                RoundedCornerShape(NexusDimens.RadiusMd)
            )
            .clickable(onClick = onRowClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("storage_item_${item.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                    .background(NexusColors.SurfaceSidebar)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = resolveCategoryIcon(item)),
                        contentDescription = null,
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val extLabel = item.extension.ifBlank { if (item.isDirectory) "DIR" else "FILE" }
                Text(
                    text = "$extLabel • ${item.sizeLabel} • ${item.modifiedLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.isDirectory) {
                RowActionIconButton(
                    iconRes = R.drawable.ic_info,
                    contentDescription = "Folder details",
                    tint = NexusColors.TextSecondary,
                    onClick = onInspectClick
                )
            } else {
                RowActionIconButton(
                    iconRes = R.drawable.ic_download,
                    contentDescription = "Download ${item.name}",
                    tint = NexusColors.TextPrimary,
                    onClick = onDownloadClick
                )
            }
            RowActionIconButton(
                iconRes = R.drawable.ic_trash,
                contentDescription = "Delete ${item.name}",
                tint = NexusColors.DangerRed,
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun RowActionIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
            .background(NexusColors.SurfaceSidebar)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun LoadMoreFilesFooter(
    loadedCount: Int,
    totalCount: Int,
    isLoadingMore: Boolean,
    onLoadMoreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                .clickable(enabled = !isLoadingMore, onClick = onLoadMoreClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("storage_load_more_btn"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLoadingMore) {
                SpinningLoaderIcon(size = 14.dp, tint = NexusColors.TextPrimary)
                Text(
                    text = "Loading next page...",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh_cw),
                    contentDescription = null,
                    tint = NexusColors.TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Load More Files (Showing $loadedCount of $totalCount)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun StorageInspectorDialog(
    item: StorageFileItem,
    isImagePreviewCached: Boolean,
    onDismiss: () -> Unit,
    onLoadFullImage: () -> Unit,
    onDownloadItem: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val thumbnailBitmap = remember(item.path, item.thumbnailBase64) {
        MonitorImageHelpers.decodeImageBitmapFromBase64(item.thumbnailBase64)
    }

    TacticalModalDialog(
        title = item.name,
        onDismiss = onDismiss,
        footer = {
            TacticalSecondaryButton(
                text = "Delete",
                iconRes = R.drawable.ic_trash,
                tintColor = NexusColors.DangerRed,
                borderColor = NexusColors.DangerRedBorder,
                onClick = onRequestDelete,
                modifier = Modifier.testTag("inspector_delete_btn")
            )
            if (!item.isDirectory) {
                Spacer(modifier = Modifier.width(8.dp))
                TacticalPrimaryButton(
                    text = "Download",
                    iconRes = R.drawable.ic_download,
                    onClick = onDownloadItem,
                    modifier = Modifier.testTag("inspector_download_btn")
                )
            }
        }
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.CanvasBlack)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            )
        }

        if (!item.isDirectory && item.category == StorageCategory.IMAGE) {
            TacticalSecondaryButton(
                text = if (isImagePreviewCached) "View Full Image (Cached)" else "View Full Image",
                iconRes = R.drawable.ic_eye,
                onClick = onLoadFullImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspector_view_full_image_btn")
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InspectorMetaRow("Type", if (item.isDirectory) "Folder" else item.extension.ifBlank { "File" })
            InspectorMetaRow("Size", if (item.isDirectory) "--" else "${item.sizeLabel} (${item.rawBytes} bytes)")
            InspectorMetaRow("Modified", item.modifiedLabel)
            InspectorMetaRow("MIME Type", item.mimeType)
            InspectorMetaRow("Path", item.path)
        }
    }
}

@Composable
private fun InspectorMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexusColors.TextMuted,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            style = NexusTypography.MonoBadge,
            color = NexusColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FullImagePreviewDialog(
    item: StorageFileItem,
    localFilePath: String,
    onDownloadImage: () -> Unit,
    onDismiss: () -> Unit
) {
    var decodedBitmap by remember(localFilePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(localFilePath) {
        decodedBitmap = withContext(Dispatchers.IO) {
            decodeSampledFileBitmap(File(localFilePath), maxDimension = 2048)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(NexusDimens.RadiusXl),
            color = NexusColors.CanvasElevated,
            border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = NexusColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.sizeLabel} • Full-Resolution Cached Preview",
                            style = NexusTypography.MonoBadge,
                            color = NexusColors.TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .background(NexusColors.SurfaceSidebar)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close preview",
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(NexusColors.CanvasBlack)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = decodedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SpinningLoaderIcon(size = 24.dp, tint = NexusColors.TextPrimary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TacticalSecondaryButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TacticalPrimaryButton(
                        text = "Download Image",
                        iconRes = R.drawable.ic_download,
                        onClick = onDownloadImage,
                        modifier = Modifier.testTag("preview_download_image_btn")
                    )
                }
            }
        }
    }
}

private fun resolveCategoryIcon(item: StorageFileItem): Int {
    if (item.isDirectory) return R.drawable.ic_folder
    return when (item.category) {
        StorageCategory.IMAGE -> R.drawable.ic_image
        StorageCategory.VIDEO -> R.drawable.ic_video
        StorageCategory.AUDIO -> R.drawable.ic_audio_lines
        StorageCategory.DOCUMENT -> R.drawable.ic_file_text
        StorageCategory.ARCHIVE, StorageCategory.OTHER, StorageCategory.ALL -> R.drawable.ic_file
    }
}

private fun decodeSampledFileBitmap(file: File, maxDimension: Int): ImageBitmap? {
    if (!file.exists()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return@runCatching null

        var inSampleSize = 1
        while (width / inSampleSize > maxDimension || height / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
    }.getOrNull()
}
