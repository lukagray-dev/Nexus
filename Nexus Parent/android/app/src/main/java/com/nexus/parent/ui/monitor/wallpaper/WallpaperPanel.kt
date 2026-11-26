package com.nexus.parent.ui.monitor.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.WallpaperTarget
import com.nexus.parent.state.WallpaperUiState
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.monitor.MonitorImageHelpers
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * WallpaperPanel.kt - Mobile HUD Wallpaper Preview & Push Control (`wallpaper.ts`).
 */
@Composable
fun WallpaperPanel(
    state: WallpaperUiState,
    onPreviewTap: () -> Unit,
    onSelectTarget: (WallpaperTarget) -> Unit,
    onPushWallpaperClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBytes = state.activePreviewBytes()
    val previewBitmap = remember(activeBytes) {
        MonitorImageHelpers.decodeImageBitmapFromBytes(activeBytes)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phone Frame HUD Preview
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(340.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(NexusColors.SurfaceSidebar)
                .border(2.dp, NexusColors.BorderMedium, RoundedCornerShape(26.dp))
                .clickable(enabled = !state.isFetching && !state.isPushing, onClick = onPreviewTap),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null && !state.isFetching) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Child ${state.target.label} wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isFetching) {
                        SpinningLoaderIcon(size = 24.dp)
                        Text(
                            text = "Loading Wallpaper...",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextSecondary
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image),
                            contentDescription = null,
                            tint = if (state.hasPreviewLoaded) NexusColors.TextPrimary else NexusColors.TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (state.hasPreviewLoaded) {
                                "${state.target.label} Snapshot Ready"
                            } else {
                                "Active Wallpaper"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap to load current wallpaper from child",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Top speaker notch overlaid cleanly on top of preview
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .width(56.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NexusColors.BorderMedium)
            )
        }

        // Target Selector (Home / Lock / Both)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WallpaperTarget.entries.forEach { target ->
                val active = state.target == target
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (active) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                        .clickable { onSelectTarget(target) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = target.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary
                    )
                }
            }
        }

        // Push Wallpaper CTA + Upload Progress Bar
        TacticalPrimaryButton(
            text = if (state.isPushing) "Pushing... ${state.pushProgressPercent}%" else "Push Wallpaper to Device",
            iconRes = R.drawable.ic_image,
            enabled = !state.isPushing,
            onClick = onPushWallpaperClick,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.isPushing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NexusColors.SurfaceCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((state.pushProgressPercent / 100f).coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(NexusColors.AccentWhite)
                )
            }
        }
    }
}
