package com.nexus.parent.ui.surveillance.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.LocationUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.components.TacticalCard
import com.nexus.parent.ui.surveillance.video.PausedStreamOverlay
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * LocationScreen.kt - Tactical Geolocation Tracking View matching Desktop `location.ts`.
 *
 * Hey friend! Hosts a full-viewport `NexusOsmMapView` (Esri Satellite Imagery + Reference
 * Boundaries or CartoDB Dark Matter Streets), floating telemetry card, loading spinner on
 * Start/Stop until Child confirms, and floating bottom control capsule (Start/Stop, Center
 * Target, Layer Toggle, and Zoom +/-).
 */
@Composable
fun LocationScreen(
    state: LocationUiState,
    onToggleTracking: () -> Unit,
    onCenterTarget: () -> Unit,
    onToggleLayer: () -> Unit,
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {}
) {
    val isTracking = state.state == MonitoringState.LIVE
    val isBusy = state.state == MonitoringState.STARTING || state.state == MonitoringState.STOPPING

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
    ) {
        // 1. Live Interactive Multi-Layer Map (Esri World Imagery & CartoDB Dark Streets)
        NexusOsmMapView(
            mapLayer = state.mapLayer,
            latitude = state.latitude,
            longitude = state.longitude,
            accuracyMeters = state.accuracyMeters,
            isTracking = isTracking,
            centerRequestToken = state.centerRequestToken,
            zoomDeltaRequestId = state.zoomDeltaRequestId,
            zoomDeltaSteps = state.zoomDeltaSteps,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Telemetry Card (Top, below Capsule TopBar)
        TacticalCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 68.dp, start = 12.dp, end = 12.dp),
            containerColor = NexusColors.SurfaceSidebar.copy(alpha = 0.94f),
            borderColor = NexusColors.BorderMedium
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        StatusPulseDot(
                            color = when {
                                isTracking -> NexusColors.LiveGreen
                                isBusy -> NexusColors.TextPrimary
                                else -> NexusColors.TextMuted
                            },
                            isPulsing = isTracking || isBusy,
                            size = 7.dp
                        )
                        Text(
                            text = when (state.state) {
                                MonitoringState.LIVE -> "Live Tracking"
                                MonitoringState.STARTING -> "Connecting..."
                                MonitoringState.STOPPING -> "Stopping..."
                                MonitoringState.IDLE -> "Paused"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusColors.TextPrimary
                        )
                    }
                    Text(
                        text = "${state.mapLayer.label} • ${state.lastPingLabel}",
                        style = NexusTypography.MonoBadge,
                        color = NexusColors.TextMuted
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.DangerRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = state.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TelemetryMiniMetric("COORDS", state.coordinatesLabel, Modifier.weight(1.4f))
                    TelemetryMiniMetric("ACCURACY", state.accuracyLabel, Modifier.weight(0.8f))
                    TelemetryMiniMetric("VELOCITY", state.velocityLabel, Modifier.weight(0.8f))
                }
            }
        }

        // 3. Paused Pill Overlay
        if (!isTracking) {
            PausedStreamOverlay(
                message = when (state.state) {
                    MonitoringState.STARTING -> "Starting Child GPS Tracker..."
                    else -> "Location Tracking Paused • Tap Start to Begin"
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 4. Floating Bottom Control Pills
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(NexusDimens.RadiusCapsule),
            color = NexusColors.SurfaceSidebar.copy(alpha = 0.95f),
            border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .background(
                            when {
                                isTracking -> NexusColors.DangerRedSubtle
                                isBusy -> NexusColors.SurfaceCard
                                else -> NexusColors.AccentWhite
                            }
                        )
                        .border(
                            NexusDimens.BorderHairline,
                            when {
                                isTracking -> NexusColors.DangerRedBorder
                                isBusy -> NexusColors.BorderMedium
                                else -> NexusColors.AccentWhite
                            },
                            RoundedCornerShape(NexusDimens.RadiusCapsule)
                        )
                        .clickable(enabled = !isBusy, onClick = onToggleTracking)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isBusy -> SpinningLoaderIcon(size = 13.dp, tint = NexusColors.TextPrimary)
                        isTracking -> Icon(
                            painter = painterResource(id = R.drawable.ic_pause),
                            contentDescription = null,
                            tint = NexusColors.DangerRed,
                            modifier = Modifier.size(13.dp)
                        )
                        else -> Icon(
                            painter = painterResource(id = R.drawable.ic_play),
                            contentDescription = null,
                            tint = NexusColors.TextInverse,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = when (state.state) {
                            MonitoringState.LIVE -> "Stop Tracking"
                            MonitoringState.STARTING -> "Starting GPS..."
                            MonitoringState.STOPPING -> "Stopping GPS..."
                            MonitoringState.IDLE -> "Start Live Tracking"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isTracking || isBusy -> NexusColors.TextPrimary
                            else -> NexusColors.TextInverse
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onCenterTarget),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crosshair),
                        contentDescription = "Center target",
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onToggleLayer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_compass),
                        contentDescription = "Switch map layer",
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onZoomIn),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelLarge,
                        color = NexusColors.TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onZoomOut),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.labelLarge,
                        color = NexusColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = NexusTypography.SectionHeader,
            color = NexusColors.TextMuted
        )
        Text(
            text = value,
            style = NexusTypography.MonoValue,
            color = NexusColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
