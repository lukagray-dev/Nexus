package com.nexus.parent.ui.surveillance.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.nexus.parent.state.CameraFeedUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.surveillance.video.DvrPlaybackTextureView
import com.nexus.parent.ui.surveillance.video.LiveFeedPlayerBar
import com.nexus.parent.ui.surveillance.video.NexusVideoSurface
import com.nexus.parent.ui.surveillance.video.PausedStreamOverlay
import com.nexus.parent.ui.theme.NexusColors
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * CameraFeedScreen.kt - Full-viewport Camera Surveillance UI matching Desktop `camera.ts`.
 *
 * Hey friend! Renders incoming WebRTC camera frames inside `NexusVideoSurface` (TextureView-backed
 * so all Compose overlays & 90-degree rotations work with zero window-hole artifacts), plus the
 * YouTube-style DVR timeline, Rear/Front lens switcher, and clip export trigger.
 */
@Composable
fun CameraFeedScreen(
    state: CameraFeedUiState,
    onPlayPauseClick: () -> Unit,
    onRotateClick: () -> Unit,
    onSwitchLensClick: () -> Unit,
    onScrubDvr: (Float) -> Unit,
    onJumpToLive: () -> Unit,
    onSaveClipClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    onDvrSegmentPositionMs: (Int) -> Unit = {},
    onDvrSegmentCompleted: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
    ) {
        // 1. Live WebRTC TextureView Surface (active when at live edge)
        if (state.isAtLiveEdge) {
            NexusVideoSurface(
                videoTrack = videoTrack,
                eglContext = eglContext,
                rotationDegrees = state.rotationDegrees,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DvrPlaybackTextureView(
                segmentPath = state.dvrActiveSegmentPath,
                offsetMs = state.dvrSegmentOffsetMs,
                seekTriggerId = state.dvrSeekTriggerId,
                rotationDegrees = state.rotationDegrees,
                onSegmentPositionMs = onDvrSegmentPositionMs,
                onSegmentCompleted = onDvrSegmentCompleted,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Placeholder visual indicator when LIVE in UI-only preview without an active track
        if (state.state == MonitoringState.LIVE && videoTrack == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = null,
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Live Camera Surface Ready (${state.lens.label} • ${state.rotationDegrees}°)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextSecondary
                )
            }
        }

        // 3. Top Lens Telemetry Pill (below floating capsule bar when not fullscreen)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = if (state.isFullscreen) 14.dp else 72.dp, start = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TacticalBadgePill(
                text = "LENS: ${state.lens.label.uppercase()}",
                textColor = NexusColors.TextSecondary,
                backgroundColor = NexusColors.SurfaceSidebar.copy(alpha = 0.88f)
            )
            TacticalBadgePill(
                text = "H.264 / SRTP",
                textColor = NexusColors.TextMuted,
                backgroundColor = NexusColors.SurfaceSidebar.copy(alpha = 0.88f)
            )
        }

        // 4. Paused Stream Overlay (kept upright outside rotatable video surface)
        if (state.state != MonitoringState.LIVE) {
            PausedStreamOverlay(
                message = "Feed Paused • Tap Play to Start Stream",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 5. Floating Bottom Player & DVR Controls Bar
        LiveFeedPlayerBar(
            state = state.state,
            dvrProgress = state.dvrProgress,
            isAtLiveEdge = state.isAtLiveEdge,
            elapsedLabel = state.elapsedLabel,
            rotationDegrees = state.rotationDegrees,
            isFullscreen = state.isFullscreen,
            lensLabel = state.lens.label,
            isLensSwitching = state.isSwitchingLens,
            isSaveClipEnabled = state.hasDvrClips && !state.isExportingClip,
            isExportingClip = state.isExportingClip,
            onPlayPauseClick = onPlayPauseClick,
            onJumpToLiveClick = onJumpToLive,
            onScrub = onScrubDvr,
            onRotateClick = onRotateClick,
            onLensSwitchClick = onSwitchLensClick,
            onSaveClipClick = onSaveClipClick,
            onFullscreenClick = onToggleFullscreen,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
