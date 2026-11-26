package com.nexus.parent.ui.surveillance.mic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.MicUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.VoicePresetId
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography
import kotlin.math.roundToInt

/**
 * FloatingMicOverlay.kt - Compact Draggable Microphone & Voice Intercom HUD (`mic.ts`).
 *
 * Hey friend! Just like Desktop, the Microphone Monitor floats seamlessly on top of ANY view
 * (Dashboard, Camera, Screen, Location, Storage, or Monitor Panels) without resetting the
 * underlying screen! Users can drag it by its grip header, toggle live environmental listening,
 * trigger Push-to-Talk speaking, and switch between all 10 real-time voice changer presets.
 */
@Composable
fun FloatingMicOverlay(
    state: MicUiState,
    onClose: () -> Unit,
    onToggleListen: () -> Unit,
    onToggleSpeak: () -> Unit,
    onToggleVoiceMenu: () -> Unit,
    onSelectVoice: (VoicePresetId) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isOverlayOpen) return

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val panelWidthDp = 296.dp
        val panelWidthPx = with(density) { panelWidthDp.toPx() }
        val estimatedHeightPx = with(density) { 170.dp.toPx() }

        var offsetX by remember { mutableFloatStateOf((parentWidthPx - panelWidthPx - 24f).coerceAtLeast(16f)) }
        var offsetY by remember { mutableFloatStateOf((parentHeightPx - estimatedHeightPx - 140f).coerceAtLeast(80f)) }
        var initialized by remember { mutableStateOf(false) }

        if (!initialized && parentWidthPx > 0f && parentHeightPx > 0f) {
            offsetX = (parentWidthPx - panelWidthPx - 24f).coerceAtLeast(16f)
            offsetY = (parentHeightPx - estimatedHeightPx - 140f).coerceAtLeast(80f)
            initialized = true
        }

        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetX.coerceIn(8f, (parentWidthPx - panelWidthPx - 8f).coerceAtLeast(8f)).roundToInt(),
                        offsetY.coerceIn(72f, (parentHeightPx - estimatedHeightPx - 16f).coerceAtLeast(72f)).roundToInt()
                    )
                }
                .width(panelWidthDp)
                .testTag("floating_mic_hud"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice Changer Drop-Up Menu (opens cleanly above the floating panel)
            AnimatedVisibility(visible = state.isVoiceMenuExpanded) {
                VoicePresetDropdown(
                    selectedVoice = state.selectedVoice,
                    onSelectVoice = onSelectVoice
                )
            }

            // Main Floating Mic Panel Card
            Surface(
                shape = RoundedCornerShape(NexusDimens.RadiusLg),
                color = NexusColors.SurfaceSidebar.copy(alpha = 0.96f),
                border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium),
                shadowElevation = 14.dp
            ) {
                Column {
                    // Draggable Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexusColors.SurfaceCard)
                            .pointerInput(parentWidthPx, parentHeightPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX = (offsetX + dragAmount.x).coerceIn(
                                        8f,
                                        (parentWidthPx - panelWidthPx - 8f).coerceAtLeast(8f)
                                    )
                                    offsetY = (offsetY + dragAmount.y).coerceIn(
                                        72f,
                                        (parentHeightPx - estimatedHeightPx - 16f).coerceAtLeast(72f)
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_grip_horizontal),
                                contentDescription = "Drag microphone panel",
                                tint = NexusColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Microphone",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusColors.TextPrimary
                            )
                            if (state.listenState == MonitoringState.LIVE) {
                                StatusPulseDot(color = NexusColors.LiveGreen, size = 6.dp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClose)
                                .testTag("mic_hud_close_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close microphone panel",
                                tint = NexusColors.TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = NexusColors.BorderSubtle)

                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 36-Bar Audio Spectrum Visualizer (White while listening, Green while speaking)
                        SpectrumVisualizer(
                            bars = state.spectrumBars,
                            isListening = state.listenState == MonitoringState.LIVE,
                            isSpeaking = state.speakState == MonitoringState.LIVE
                        )

                        // Controls Row: Listen Play/Pause + Split Speak / Voice Changer
                        val isListenBusy =
                            state.listenState == MonitoringState.STARTING ||
                                state.listenState == MonitoringState.STOPPING
                        val isSpeakBusy =
                            state.speakState == MonitoringState.STARTING ||
                                state.speakState == MonitoringState.STOPPING
                        val isSpeaking = state.speakState == MonitoringState.LIVE

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Listen Toggle Button (intermediate SpinningLoaderIcon until Child confirms)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                                    .background(
                                        if (state.listenState == MonitoringState.LIVE) {
                                            NexusColors.LiveGreenSubtle
                                        } else {
                                            NexusColors.SurfaceCard
                                        }
                                    )
                                    .border(
                                        NexusDimens.BorderHairline,
                                        if (state.listenState == MonitoringState.LIVE) {
                                            NexusColors.LiveGreenBorder
                                        } else {
                                            NexusColors.BorderMedium
                                        },
                                        RoundedCornerShape(NexusDimens.RadiusMd)
                                    )
                                    .clickable(enabled = !isListenBusy, onClick = onToggleListen)
                                    .testTag("mic_listen_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                when (state.listenState) {
                                    MonitoringState.STARTING, MonitoringState.STOPPING -> SpinningLoaderIcon(size = 15.dp)
                                    MonitoringState.LIVE -> Icon(
                                        painter = painterResource(id = R.drawable.ic_pause),
                                        contentDescription = "Pause child mic",
                                        tint = NexusColors.LiveGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    MonitoringState.IDLE -> Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "Listen to child mic",
                                        tint = NexusColors.TextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // 2. Split Speak + Voice Selector Group
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                                    .background(if (isSpeaking) NexusColors.DangerRedSubtle else NexusColors.SurfaceCard)
                                    .border(
                                        NexusDimens.BorderHairline,
                                        if (isSpeaking) NexusColors.DangerRedBorder else NexusColors.BorderMedium,
                                        RoundedCornerShape(NexusDimens.RadiusMd)
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clickable(enabled = !isSpeakBusy, onClick = onToggleSpeak)
                                        .padding(horizontal = 10.dp)
                                        .testTag("mic_speak_btn"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSpeakBusy) {
                                        SpinningLoaderIcon(size = 14.dp)
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_mic),
                                            contentDescription = null,
                                            tint = if (isSpeaking) NexusColors.DangerRed else NexusColors.TextPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = when (state.speakState) {
                                            MonitoringState.STARTING -> "Connecting..."
                                            MonitoringState.STOPPING -> "Stopping..."
                                            MonitoringState.LIVE -> "Speaking"
                                            MonitoringState.IDLE -> "Speak"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NexusColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TacticalBadgePill(
                                        text = state.selectedVoice.displayName,
                                        textColor = NexusColors.TextSecondary,
                                        backgroundColor = NexusColors.SurfaceHover
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(22.dp)
                                        .background(NexusColors.BorderSubtle)
                                )

                                Box(
                                    modifier = Modifier
                                        .width(34.dp)
                                        .height(38.dp)
                                        .clickable(onClick = onToggleVoiceMenu)
                                        .testTag("mic_voice_menu_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_down),
                                        contentDescription = "Select voice effect",
                                        tint = NexusColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumVisualizer(
    bars: List<Float>,
    isListening: Boolean,
    isSpeaking: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
            .background(NexusColors.CanvasBlack)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = bars.size.coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val totalGap = gap * (count - 1)
            val barWidth = ((size.width - totalGap) / count).coerceAtLeast(1f)
            val barColor = when {
                isSpeaking -> NexusColors.LiveGreen
                isListening -> NexusColors.AccentWhite
                else -> NexusColors.TextMuted.copy(alpha = 0.45f)
            }

            bars.forEachIndexed { index, amp ->
                val clamped = amp.coerceIn(0.08f, 1f)
                val barHeight = size.height * clamped
                val x = index * (barWidth + gap)
                val y = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

@Composable
private fun VoicePresetDropdown(
    selectedVoice: VoicePresetId,
    onSelectVoice: (VoicePresetId) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(NexusDimens.RadiusMd),
        color = NexusColors.SurfaceDropdown,
        border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusColors.SurfaceCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE CHANGER",
                    style = NexusTypography.SectionHeader,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Real-time DSP",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.TextMuted
                )
            }
            HorizontalDivider(color = NexusColors.BorderSubtle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                VoicePresetId.entries.forEach { preset ->
                    val isSelected = preset == selectedVoice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .background(if (isSelected) NexusColors.AccentWhiteSubtle else NexusColors.SurfaceDropdown)
                            .clickable { onSelectVoice(preset) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = preset.iconRes),
                                contentDescription = null,
                                tint = if (isSelected) NexusColors.TextPrimary else NexusColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) NexusColors.TextPrimary else NexusColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
