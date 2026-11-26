package com.nexus.parent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color.kt - Centralized Monochrome Tactical Dark Palette for Nexus Parent Android.
 *
 * Hey friend! Every color here mirrors `Nexus Parent/desktop/src/css/tokens.css` and
 * `docs/UI-PREFERENCES.md`:
 * - Pure Pitch Black (#000000) for the main canvas.
 * - Neutral dark charcoal surfaces (#121212, #181818, #1A1A1A) for sidebars, capsules, and cards.
 * - Crisp White (#FFFFFF) for primary text and active highlights.
 * - Functional accents ONLY: Danger Red (#EF4444) for Stealth/Delete/Live dot, Live Green (#22C55E)
 *   for online/call-alert states, and Warning Amber (#F59E0B) for reconnecting/failed states.
 */
object NexusColors {
    val CanvasBlack = Color(0xFF000000)
    val CanvasElevated = Color(0xFF0A0A0A)
    val SurfaceSidebar = Color(0xFF121212)
    val SurfaceCard = Color(0xFF181818)
    val SurfaceDropdown = Color(0xFF1A1A1A)
    val SurfaceHover = Color(0xFF202020)
    val SurfacePressed = Color(0xFF262626)

    val BorderSubtle = Color(0x14FFFFFF)     // rgba(255, 255, 255, 0.08)
    val BorderMedium = Color(0x26FFFFFF)     // rgba(255, 255, 255, 0.15)
    val BorderStrong = Color(0x40FFFFFF)     // rgba(255, 255, 255, 0.25)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD3D3D3)
    val TextMuted = Color(0xFF888888)
    val TextInverse = Color(0xFF000000)

    val AccentWhite = Color(0xFFFFFFFF)
    val AccentWhiteSubtle = Color(0x1FFFFFFF)

    // Functional Status Accents (strictly controlled per UI-PREFERENCES.md)
    val DangerRed = Color(0xFFEF4444)
    val DangerRedDark = Color(0xFFDC2626)
    val DangerRedSubtle = Color(0x26EF4444)
    val DangerRedBorder = Color(0x66EF4444)

    val LiveGreen = Color(0xFF22C55E)
    val LiveGreenDark = Color(0xFF15803D)
    val LiveGreenSubtle = Color(0x2622C55E)
    val LiveGreenBorder = Color(0x5922C55E)

    val WarningAmber = Color(0xFFF59E0B)
    val WarningAmberSubtle = Color(0x26F59E0B)
    val WarningAmberBorder = Color(0x66F59E0B)
}
