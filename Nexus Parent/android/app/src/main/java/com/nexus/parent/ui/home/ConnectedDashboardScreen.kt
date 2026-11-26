package com.nexus.parent.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * ConnectedDashboardScreen.kt - Phase 2 Connected Information Dashboard (`connection-state.ts`).
 *
 * Hey friend! Rendered once a child phone is connected and no feature is currently open.
 * Combines Desktop's monochrome hero ring & status copy with legacy Android's animated
 * swipe-right drawer prompt (rendered with a Lucide vector arrow, zero emojis).
 */
@Composable
fun ConnectedDashboardScreen(
    device: DeviceRecord?,
    onOpenDrawerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "swipeHint")
    val arrowSlide by transition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowSlide"
    )
    val arrowAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(NexusColors.SurfaceSidebar)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_nexus_parent),
                    contentDescription = "Nexus Parent",
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (device != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .background(NexusColors.SurfaceCard)
                        .border(NexusDimens.BorderHairline, NexusColors.LiveGreenBorder, RoundedCornerShape(NexusDimens.RadiusCapsule))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPulseDot(color = NexusColors.LiveGreen, size = 7.dp)
                    Text(
                        text = "${device.name} • ${device.telemetry.batteryPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = "Device Connected & Monitoring Ready",
                style = MaterialTheme.typography.headlineMedium,
                color = NexusColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your child's device is synchronized over encrypted WebRTC. Open the navigation drawer to start live surveillance, inspect logs, or control device features.",
                style = MaterialTheme.typography.bodyMedium,
                color = NexusColors.TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Swipe Cue + Tap-to-Open Drawer Pill (modernized from legacy arrow_gesture)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.SurfaceCard)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                    .clickable(onClick = onOpenDrawerClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = NexusColors.TextPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .offset { IntOffset(arrowSlide.dp.roundToPx(), 0) }
                        .alpha(arrowAlpha)
                )
                Text(
                    text = "Swipe right or tap to browse monitoring features",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextSecondary
                )
            }
        }
    }
}
