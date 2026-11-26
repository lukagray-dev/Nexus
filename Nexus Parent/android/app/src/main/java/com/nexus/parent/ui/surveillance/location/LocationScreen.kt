package com.nexus.parent.ui.surveillance.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.LocationUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.components.TacticalCard
import com.nexus.parent.ui.surveillance.video.PausedStreamOverlay
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * LocationScreen.kt - Tactical Geolocation Tracking View matching Desktop `location.ts`.
 *
 * Hey friend! Hosts a full-viewport dark tactical map canvas (ready for osmdroid Esri Satellite /
 * CartoDB Dark Matter tile binding), floating telemetry card, paused pill, and bottom floating
 * control bar (Start/Stop Tracking, Center Target, Map Layer Toggle).
 */
@Composable
fun LocationScreen(
    state: LocationUiState,
    onToggleTracking: () -> Unit,
    onCenterTarget: () -> Unit,
    onToggleLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTracking = state.state == MonitoringState.LIVE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
    ) {
        // 1. Tactical Map Viewport Placeholder Canvas (ready for osmdroid MapView binding)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 48.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = NexusColors.BorderSubtle,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = NexusColors.BorderSubtle,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            if (isTracking) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = NexusColors.LiveGreenSubtle,
                    radius = 74.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = NexusColors.LiveGreenBorder,
                    radius = 74.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = NexusColors.LiveGreen,
                    radius = 8.dp.toPx(),
                    center = center
                )
            }
        }

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
                            color = if (isTracking) NexusColors.LiveGreen else NexusColors.TextMuted,
                            isPulsing = isTracking,
                            size = 7.dp
                        )
                        Text(
                            text = if (isTracking) "Live Tracking" else "Paused",
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

                Text(
                    text = state.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

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
                message = "Location Tracking Paused • Tap Start to Begin",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 4. Floating Bottom Control Pills
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 14.dp)
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
                        .background(if (isTracking) NexusColors.DangerRedSubtle else NexusColors.AccentWhite)
                        .border(
                            NexusDimens.BorderHairline,
                            if (isTracking) NexusColors.DangerRedBorder else NexusColors.AccentWhite,
                            RoundedCornerShape(NexusDimens.RadiusCapsule)
                        )
                        .clickable(onClick = onToggleTracking)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (isTracking) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = null,
                        tint = if (isTracking) NexusColors.DangerRed else NexusColors.TextInverse,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isTracking) "Stop Tracking" else "Start Live Tracking",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isTracking) NexusColors.TextPrimary else NexusColors.TextInverse
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onCenterTarget),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crosshair),
                        contentDescription = "Center target",
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NexusColors.SurfaceCard)
                        .clickable(onClick = onToggleLayer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_compass),
                        contentDescription = "Switch map layer",
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
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
