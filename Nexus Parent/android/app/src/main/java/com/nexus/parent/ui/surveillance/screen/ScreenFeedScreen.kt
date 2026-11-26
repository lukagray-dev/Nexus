package com.nexus.parent.ui.surveillance.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ScreenFeedUiState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.surveillance.video.LiveFeedPlayerBar
import com.nexus.parent.ui.surveillance.video.NexusVideoSurface
import com.nexus.parent.ui.surveillance.video.PausedStreamOverlay
import com.nexus.parent.ui.theme.NexusColors
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * ScreenFeedScreen.kt - Full-viewport Screen Mirroring Surveillance UI (`screen.ts`).
 */
@Composable
fun ScreenFeedScreen(
    state: ScreenFeedUiState,
    onPlayPauseClick: () -> Unit,
    onRotateClick: () -> Unit,
    onScrubDvr: (Float) -> Unit,
    onJumpToLive: () -> Unit,
    onSaveClipClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
    ) {
        NexusVideoSurface(
            videoTrack = videoTrack,
            eglContext = eglContext,
            rotationDegrees = state.rotationDegrees,
            modifier = Modifier.fillMaxSize()
        )

        if (state.state == MonitoringState.LIVE && videoTrack == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_monitor),
                    contentDescription = null,
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Live Screen Mirror Surface Ready (${state.quality.label} • ${state.rotationDegrees}°)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextSecondary
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = if (state.isFullscreen) 14.dp else 72.dp, start = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TacticalBadgePill(
                text = state.quality.label,
                textColor = NexusColors.TextSecondary,
                backgroundColor = NexusColors.SurfaceSidebar.copy(alpha = 0.88f)
            )
            TacticalBadgePill(
                text = if (state.isSystemAudioEnabled) "SYSTEM AUDIO: OPUS" else "AUDIO MUTED",
                textColor = NexusColors.TextMuted,
                backgroundColor = NexusColors.SurfaceSidebar.copy(alpha = 0.88f)
            )
        }

        if (state.state != MonitoringState.LIVE) {
            PausedStreamOverlay(
                message = "Feed Paused • Tap Play to Start Screen Mirror",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LiveFeedPlayerBar(
            state = state.state,
            dvrProgress = state.dvrProgress,
            isAtLiveEdge = state.isAtLiveEdge,
            elapsedLabel = state.elapsedLabel,
            rotationDegrees = state.rotationDegrees,
            isFullscreen = state.isFullscreen,
            lensLabel = null,
            onPlayPauseClick = onPlayPauseClick,
            onJumpToLiveClick = onJumpToLive,
            onScrub = onScrubDvr,
            onRotateClick = onRotateClick,
            onSaveClipClick = onSaveClipClick,
            onFullscreenClick = onToggleFullscreen,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
