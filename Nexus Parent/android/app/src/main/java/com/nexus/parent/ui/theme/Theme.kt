package com.nexus.parent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusColors.AccentWhite,
    onPrimary = NexusColors.TextInverse,
    secondary = NexusColors.TextSecondary,
    onSecondary = NexusColors.TextInverse,
    background = NexusColors.CanvasBlack,
    onBackground = NexusColors.TextPrimary,
    surface = NexusColors.SurfaceSidebar,
    onSurface = NexusColors.TextPrimary,
    surfaceVariant = NexusColors.SurfaceCard,
    onSurfaceVariant = NexusColors.TextSecondary,
    outline = NexusColors.BorderMedium,
    error = NexusColors.DangerRed,
    onError = NexusColors.TextPrimary
)

/**
 * NexusParentTheme - Applies the strict Monochrome Tactical Dark palette and typography
 * across every screen, overlay, and dialog in Nexus Parent Android.
 */
@Composable
fun NexusParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkColorScheme,
        typography = NexusTypography.MaterialTypography,
        content = content
    )
}
