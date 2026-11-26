package com.nexus.parent.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.SettingsTabId
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.components.TacticalSwitch
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * SettingsScreen.kt - 5-Category Settings Console matching Desktop `settings/settings.ts`.
 *
 * Hey friend! Uses an adaptive layout:
 * - On phones (< 600dp wide): Horizontal scrollable category pills at top + detail pane below.
 * - On tablets (>= 600dp wide): True two-pane master-detail matching Desktop!
 */
@Composable
fun SettingsScreen(
    state: ParentUiState,
    onSelectTab: (SettingsTabId) -> Unit,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onResetDefaults: () -> Unit,
    onResetWorkspaceToast: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isSettingsOpen) return

    val devicesBadge = when {
        state.connectionPhase == ConnectionPhase.CONNECTED -> "Online"
        state.connectionPhase == ConnectionPhase.RECONNECTING -> "Reconnecting"
        state.lastPairedDevice != null -> "Offline"
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(10.dp),
            shape = RoundedCornerShape(NexusDimens.RadiusXl),
            color = NexusColors.SurfaceSidebar,
            border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = null,
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = NexusColors.TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .testTag("settings_close_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close settings",
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = NexusColors.BorderSubtle)

                // Adaptive Body
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWide = maxWidth >= 600.dp

                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .width(220.dp)
                                    .fillMaxHeight()
                                    .background(NexusColors.SurfaceSidebar)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SettingsTabId.entries.forEach { tab ->
                                    SettingsSidebarTabRow(
                                        tab = tab,
                                        isActive = state.activeSettingsTab == tab,
                                        badgeText = if (tab == SettingsTabId.DEVICES) devicesBadge else null,
                                        onClick = { onSelectTab(tab) }
                                    )
                                }
                            }
                            VerticalDivider(color = NexusColors.BorderSubtle)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(NexusColors.CanvasElevated)
                            ) {
                                SettingsDetailHost(
                                    state = state,
                                    onUpdateSettings = onUpdateSettings,
                                    onResetWorkspaceToast = onResetWorkspaceToast
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NexusColors.SurfaceSidebar)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SettingsTabId.entries.forEach { tab ->
                                    val active = state.activeSettingsTab == tab
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(NexusDimens.RadiusCapsule))
                                            .background(if (active) NexusColors.AccentWhite else NexusColors.SurfaceCard)
                                            .border(
                                                NexusDimens.BorderHairline,
                                                if (active) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                                                RoundedCornerShape(NexusDimens.RadiusCapsule)
                                            )
                                            .clickable { onSelectTab(tab) }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                            .testTag("settings_tab_${tab.name.lowercase()}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (tab == SettingsTabId.ABOUT) {
                                            Image(
                                                painter = painterResource(id = tab.iconRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(id = tab.iconRes),
                                                contentDescription = null,
                                                tint = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = NexusColors.BorderSubtle)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(NexusColors.CanvasElevated)
                            ) {
                                SettingsDetailHost(
                                    state = state,
                                    onUpdateSettings = onUpdateSettings,
                                    onResetWorkspaceToast = onResetWorkspaceToast
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = NexusColors.BorderSubtle)

                // Footer: Reset Defaults + Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TacticalSecondaryButton(
                        text = "Reset Defaults",
                        iconRes = R.drawable.ic_refresh_cw,
                        onClick = onResetDefaults,
                        modifier = Modifier.testTag("settings_reset_btn")
                    )
                    TacticalPrimaryButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.testTag("settings_done_btn")
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailHost(
    state: ParentUiState,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onResetWorkspaceToast: () -> Unit
) {
    when (state.activeSettingsTab) {
        SettingsTabId.GENERAL -> GeneralSystemPane(state.settings, onUpdateSettings)
        SettingsTabId.AUDIO -> AudioIntercomPane(state.settings, onUpdateSettings)
        SettingsTabId.SURVEILLANCE -> SurveillanceDvrPane(state.settings, onUpdateSettings)
        SettingsTabId.DEVICES -> PairedDevicesPane(
            connectionPhase = state.connectionPhase,
            device = state.connectedDevice ?: state.lastPairedDevice
        )
        SettingsTabId.ABOUT -> AboutPane(onResetWorkspaceToast = onResetWorkspaceToast)
    }
}

@Composable
private fun SettingsSidebarTabRow(
    tab: SettingsTabId,
    isActive: Boolean,
    badgeText: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(if (isActive) NexusColors.AccentWhiteSubtle else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("settings_tab_${tab.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (tab == SettingsTabId.ABOUT) {
                Image(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = null,
                    tint = if (isActive) NexusColors.TextPrimary else NexusColors.TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) NexusColors.TextPrimary else NexusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badgeText != null) {
            TacticalBadgePill(
                text = badgeText,
                textColor = when (badgeText) {
                    "Online" -> NexusColors.LiveGreen
                    "Reconnecting" -> NexusColors.WarningAmber
                    else -> NexusColors.TextMuted
                }
            )
        }
    }
}

@Composable
internal fun SettingsSectionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = NexusTypography.SectionHeader,
            color = NexusColors.TextMuted
        )
        content()
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = NexusColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextMuted)
        }
        TacticalSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun <T> SettingsSegmentedRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = NexusColors.TextPrimary)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                        .background(if (active) NexusColors.AccentWhite else NexusColors.SurfaceSidebar)
                        .border(
                            NexusDimens.BorderHairline,
                            if (active) NexusColors.AccentWhite else NexusColors.BorderSubtle,
                            RoundedCornerShape(NexusDimens.RadiusSm)
                        )
                        .clickable { onSelect(option) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(option),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
