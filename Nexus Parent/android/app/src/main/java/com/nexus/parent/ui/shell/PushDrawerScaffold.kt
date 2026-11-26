package com.nexus.parent.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * PushDrawerScaffold.kt - ChatGPT-style push navigation drawer inherited from legacy Nexus Parent.
 *
 * Hey friend! Unlike Android's default overlay drawer that dims and covers the screen, this
 * custom scaffold slides the left drawer in while smoothly pushing the entire main viewport
 * to the right (`translationX = drawerWidth * progress`). Swiping anywhere on the main surface
 * or tapping the pushed viewport when open smoothly opens or closes the drawer.
 */
@Composable
fun PushDrawerScaffold(
    isDrawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    swipeEnabled: Boolean = true,
    drawerContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BackHandler(enabled = isDrawerOpen) {
        onDrawerOpenChange(false)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
    ) {
        val drawerWidthDp = minOf(maxWidth * NexusDimens.DrawerWidthFraction, NexusDimens.DrawerMaxWidth)
        val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
        val progress = remember { Animatable(if (isDrawerOpen) 1f else 0f) }

        LaunchedEffect(isDrawerOpen) {
            progress.animateTo(
                targetValue = if (isDrawerOpen) 1f else 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }

        val dragState = rememberDraggableState { delta ->
            if (!swipeEnabled || drawerWidthPx <= 0f) return@rememberDraggableState
            scope.launch {
                val next = (progress.value + delta / drawerWidthPx).coerceIn(0f, 1f)
                progress.snapTo(next)
            }
        }

        // 1. Main Content Surface (pushed right as drawer opens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((drawerWidthPx * progress.value).roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = swipeEnabled,
                    onDragStopped = { velocity ->
                        val shouldOpen = when {
                            velocity > 420f -> true
                            velocity < -420f -> false
                            else -> progress.value >= 0.45f
                        }
                        if (shouldOpen != isDrawerOpen) {
                            onDrawerOpenChange(shouldOpen)
                        } else {
                            scope.launch {
                                progress.animateTo(
                                    targetValue = if (shouldOpen) 1f else 0f,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                )
        ) {
            mainContent()

            // Tap-to-close overlay when drawer is open (transparent, zero dim scrim)
            if (isDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onDrawerOpenChange(false) }
                        )
                )
            }
        }

        // 2. Left Push Drawer (slides in from -drawerWidthPx to 0)
        Box(
            modifier = Modifier
                .width(drawerWidthDp)
                .fillMaxHeight()
                .offset { IntOffset((-drawerWidthPx * (1f - progress.value)).roundToInt(), 0) }
                .background(NexusColors.SurfaceSidebar)
        ) {
            drawerContent()
        }
    }
}
