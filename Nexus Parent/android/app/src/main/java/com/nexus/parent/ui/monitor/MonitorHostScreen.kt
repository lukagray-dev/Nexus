package com.nexus.parent.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * MonitorHostScreen.kt - Shared shell for inspection & hardware control panels
 * (Desktop Right Sidebar equivalent adapted to full-viewport mobile layout).
 *
 * Hey friend! Every monitor panel (SMS, Call Log, Notifications, Keyboard, Wellbeing,
 * App Lock, Wallpaper) mounts inside this shell with a consistent bottom Play/Pause dock,
 * while on-demand panels (Vibrate & Flash, Staged Social/AI) hide the footer dock.
 */
@Composable
fun MonitorHostScreen(
    monitoringState: MonitoringState,
    onToggleMonitoring: () -> Unit,
    modifier: Modifier = Modifier,
    featureTitle: String = "",
    showPlayPauseDock: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLive = monitoringState == MonitoringState.LIVE
    val isBusy = monitoringState == MonitoringState.STARTING || monitoringState == MonitoringState.STOPPING

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
            .statusBarsPadding()
            .padding(top = 64.dp)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = content
        )

        if (showPlayPauseDock) {
            HorizontalDivider(color = NexusColors.BorderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusColors.SurfaceSidebar)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPulseDot(
                        color = when (monitoringState) {
                            MonitoringState.LIVE -> NexusColors.LiveGreen
                            MonitoringState.STARTING, MonitoringState.STOPPING -> NexusColors.TextPrimary
                            MonitoringState.IDLE -> NexusColors.TextMuted
                        },
                        isPulsing = isLive || isBusy,
                        size = 7.dp
                    )
                    Text(
                        text = when (monitoringState) {
                            MonitoringState.LIVE -> "Live Monitoring"
                            MonitoringState.STARTING -> if (featureTitle.isNotBlank()) "Connecting $featureTitle..." else "Connecting..."
                            MonitoringState.STOPPING -> "Stopping..."
                            MonitoringState.IDLE -> "Monitoring Paused"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLive || isBusy) NexusColors.TextPrimary else NexusColors.TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                        .background(if (isLive) NexusColors.LiveGreenSubtle else NexusColors.SurfaceCard)
                        .border(
                            NexusDimens.BorderHairline,
                            if (isLive) NexusColors.LiveGreenBorder else NexusColors.BorderMedium,
                            RoundedCornerShape(NexusDimens.RadiusMd)
                        )
                        .clickable(enabled = !isBusy, onClick = onToggleMonitoring)
                        .testTag("monitor_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isBusy -> SpinningLoaderIcon(size = 16.dp, tint = NexusColors.TextPrimary)
                        isLive -> Icon(
                            painter = painterResource(id = R.drawable.ic_pause),
                            contentDescription = "Pause monitoring",
                            tint = NexusColors.LiveGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        else -> Icon(
                            painter = painterResource(id = R.drawable.ic_play),
                            contentDescription = "Start monitoring",
                            tint = NexusColors.TextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
