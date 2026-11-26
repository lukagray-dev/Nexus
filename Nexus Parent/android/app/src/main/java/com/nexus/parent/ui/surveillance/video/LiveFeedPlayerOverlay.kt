package com.nexus.parent.ui.surveillance.video

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography
import kotlin.math.roundToInt

/**
 * LiveFeedPlayerOverlay.kt - Shared YouTube-style DVR scrubber, paused pill, and bottom player
 * controls bar shared by both Camera Feed and Screen Feed.
 */
@Composable
fun PausedStreamOverlay(
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = R.drawable.ic_pause
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NexusDimens.RadiusCapsule),
        color = NexusColors.SurfaceSidebar.copy(alpha = 0.92f),
        border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = NexusColors.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextSecondary
            )
        }
    }
}

@Composable
fun LiveFeedPlayerBar(
    state: MonitoringState,
    dvrProgress: Float,
    isAtLiveEdge: Boolean,
    elapsedLabel: String,
    rotationDegrees: Int,
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onJumpToLiveClick: () -> Unit,
    onScrub: (Float) -> Unit,
    onRotateClick: () -> Unit,
    onSaveClipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
    lensLabel: String? = null,
    onLensSwitchClick: (() -> Unit)? = null
) {
    val isLive = state == MonitoringState.LIVE
    val isBusy = state == MonitoringState.STARTING || state == MonitoringState.STOPPING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(NexusDimens.RadiusLg),
        color = NexusColors.SurfaceSidebar.copy(alpha = 0.94f),
        border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // YouTube-style DVR Seek Track
            DvrTimelineBar(
                progress = dvrProgress,
                enabled = isLive,
                onScrub = onScrub
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Play/Pause + Live Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedActionButton(
                        onClick = onPlayPauseClick,
                        enabled = !isBusy
                    ) {
                        when {
                            isBusy -> SpinningLoaderIcon(size = 15.dp)
                            isLive -> Icon(
                                painter = painterResource(id = R.drawable.ic_pause),
                                contentDescription = "Pause stream",
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            else -> Icon(
                                painter = painterResource(id = R.drawable.ic_play),
                                contentDescription = "Start stream",
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (isLive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                                .background(NexusColors.SurfaceCard)
                                .border(
                                    NexusDimens.BorderHairline,
                                    if (isAtLiveEdge) NexusColors.DangerRedBorder else NexusColors.BorderSubtle,
                                    RoundedCornerShape(NexusDimens.RadiusSm)
                                )
                                .clickable(onClick = onJumpToLiveClick)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPulseDot(
                                color = if (isAtLiveEdge) NexusColors.DangerRed else NexusColors.TextMuted,
                                isPulsing = isAtLiveEdge,
                                size = 7.dp
                            )
                            Text(
                                text = if (isAtLiveEdge) "Live" else "DVR",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextPrimary
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                            Text(
                                text = elapsedLabel,
                                style = NexusTypography.MonoBadge,
                                color = NexusColors.TextSecondary
                            )
                        }
                    }
                }

                // Right: Rotate, Optional Lens, Save Clip, Fullscreen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeedActionButton(onClick = onRotateClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rotate_cw),
                            contentDescription = "Rotate feed",
                            tint = NexusColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${rotationDegrees}°",
                            style = NexusTypography.MonoBadge,
                            color = NexusColors.TextSecondary
                        )
                    }

                    if (lensLabel != null && onLensSwitchClick != null) {
                        FeedActionButton(onClick = onLensSwitchClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera),
                                contentDescription = "Switch lens",
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lensLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextPrimary
                            )
                        }
                    }

                    FeedActionButton(
                        onClick = onSaveClipClick,
                        enabled = isLive
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = "Save DVR clip",
                            tint = if (isLive) NexusColors.TextPrimary else NexusColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    FeedActionButton(onClick = onFullscreenClick) {
                        Icon(
                            painter = painterResource(
                                id = if (isFullscreen) R.drawable.ic_close else R.drawable.ic_maximize
                            ),
                            contentDescription = "Toggle fullscreen",
                            tint = NexusColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DvrTimelineBar(
    progress: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit
) {
    val density = LocalDensity.current
    val thumbSizePx = with(density) { 12.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onScrub((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onScrub((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val clamped = progress.coerceIn(0f, 1f)

        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NexusColors.SurfacePressed)
        )
        // Active fill
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (enabled) NexusColors.DangerRed else NexusColors.TextMuted)
        )
        // Scrubber thumb
        if (enabled) {
            val offsetX = ((trackWidthPx - thumbSizePx) * clamped).roundToInt().coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX, 0) }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(NexusColors.TextPrimary)
            )
        }
    }
}

@Composable
private fun FeedActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}
