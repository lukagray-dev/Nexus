package com.nexus.parent.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.FeatureRegistry
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.SidebarSectionId
import com.nexus.parent.state.StealthStatus
import com.nexus.parent.ui.components.SpinningLoaderIcon
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * LeftDrawerContent.kt - Monochrome Tactical Left Navigation Drawer for Nexus Parent Android.
 *
 * Hey friend! This replicates the Desktop Left Sidebar (`left-sidebar/sidebar.ts`) cleanly:
 * - Fixed Header: Brand emblem, Pair Child Device / Connected Device Pill + telemetry popover,
 *   and instant feature search filter.
 * - Scrollable Middle: 4 collapsible categories with all 18 monitoring/control items and LIVE badges.
 * - Fixed Footer: Stealth Mode button (ARMED / ACTIVE red accent) and Settings button.
 */
@Composable
fun LeftDrawerContent(
    state: ParentUiState,
    onPairOrDeviceButtonClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onCancelReconnectClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSection: (SidebarSectionId) -> Unit,
    onSelectFeature: (FeatureId) -> Unit,
    onStealthClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryReconnectNowClick: () -> Unit = {}
) {
    val filteredSections = FeatureRegistry.filterSections(state.drawerSearchQuery)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(NexusColors.SurfaceSidebar)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Fixed Top Section: Brand + Pair/Device Button + Search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_nexus_parent),
                    contentDescription = "Nexus Parent",
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Nexus Parent",
                    style = MaterialTheme.typography.titleMedium,
                    color = NexusColors.TextPrimary
                )
            }

            PairStatusButton(
                state = state,
                onClick = onPairOrDeviceButtonClick
            )

            AnimatedVisibility(visible = state.isDevicePopoverOpen) {
                DeviceStatusPopover(
                    connectionPhase = state.connectionPhase,
                    device = state.connectedDevice ?: state.lastPairedDevice,
                    reconnectAttempt = state.reconnectAttempt,
                    reconnectMaxAttempts = state.reconnectMaxAttempts,
                    reconnectSecondsRemaining = state.reconnectSecondsRemaining,
                    onDisconnect = onDisconnectClick,
                    onCancelReconnect = onCancelReconnectClick,
                    onRetryReconnectNow = onRetryReconnectNowClick
                )
            }

            TacticalSearchField(
                query = state.drawerSearchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search features..."
            )
        }

        HorizontalDivider(color = NexusColors.BorderSubtle)

        // 2. Scrollable Middle Section: 4 Categories & 18 Features
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredSections, key = { it.id.name }) { section ->
                val isCollapsed = section.id in state.collapsedSections && state.drawerSearchQuery.isBlank()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .clickable { onToggleSection(section.id) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isCollapsed) R.drawable.ic_chevron_right else R.drawable.ic_chevron_down
                                ),
                                contentDescription = null,
                                tint = NexusColors.TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = section.title.uppercase(),
                                style = NexusTypography.SectionHeader,
                                color = NexusColors.TextMuted
                            )
                        }
                        TacticalBadgePill(
                            text = section.items.size.toString(),
                            textColor = NexusColors.TextMuted,
                            backgroundColor = NexusColors.SurfaceCard
                        )
                    }

                    AnimatedVisibility(visible = !isCollapsed) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            section.items.forEach { feature ->
                                val isActive = if (feature == FeatureId.MIC) {
                                    state.mic.isOverlayOpen
                                } else {
                                    state.activeFeature == feature
                                }
                                val isLive = feature in state.liveFeatures

                                DrawerFeatureRow(
                                    feature = feature,
                                    isActive = isActive,
                                    isLive = isLive,
                                    onClick = { onSelectFeature(feature) }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = NexusColors.BorderSubtle)

        // 3. Fixed Bottom Section: Stealth Mode + Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StealthSidebarButton(
                stealthStatus = state.stealthStatus,
                onClick = onStealthClick
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.SurfaceCard)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_settings_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = null,
                    tint = NexusColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun PairStatusButton(
    state: ParentUiState,
    onClick: () -> Unit
) {
    val isConnected = state.connectionPhase == ConnectionPhase.CONNECTED && state.connectedDevice != null
    val isConnecting = state.connectionPhase == ConnectionPhase.CONNECTING
    val isReconnecting = state.connectionPhase == ConnectionPhase.RECONNECTING

    val borderColor = when {
        isReconnecting -> NexusColors.WarningAmberBorder
        isConnected -> NexusColors.LiveGreenBorder
        else -> NexusColors.BorderMedium
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, borderColor, RoundedCornerShape(NexusDimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when {
            isConnecting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpinningLoaderIcon(size = 14.dp, tint = NexusColors.TextPrimary)
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusColors.TextPrimary,
                        maxLines = 1
                    )
                }
            }
            isReconnecting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpinningLoaderIcon(size = 14.dp, tint = NexusColors.WarningAmber)
                    Text(
                        text = "Reconnecting (${state.reconnectAttempt}/${state.reconnectMaxAttempts})",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            isConnected -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_nexus_child),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.connectedDevice.formattedId,
                        style = NexusTypography.MonoValue,
                        color = NexusColors.TextPrimary,
                        maxLines = 1
                    )
                    StatusPulseDot(color = NexusColors.LiveGreen, size = 7.dp)
                    if (state.connectedDevice.telemetry.updateAvailable) {
                        TacticalBadgePill(
                            text = "Update",
                            textColor = NexusColors.WarningAmber,
                            backgroundColor = NexusColors.WarningAmberSubtle,
                            borderColor = NexusColors.WarningAmberBorder,
                            modifier = Modifier.testTag("drawer_pair_btn_update_badge")
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = null,
                        tint = NexusColors.TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Pair Child Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerFeatureRow(
    feature: FeatureId,
    isActive: Boolean,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(if (isActive) NexusColors.AccentWhiteSubtle else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag("drawer_item_${feature.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = feature.iconRes),
                contentDescription = null,
                tint = if (isActive) NexusColors.TextPrimary else NexusColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = feature.sidebarLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) NexusColors.TextPrimary else NexusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLive) {
            TacticalBadgePill(
                text = "LIVE",
                textColor = NexusColors.DangerRed,
                backgroundColor = NexusColors.DangerRedSubtle,
                borderColor = NexusColors.DangerRedBorder
            )
        }
    }
}

@Composable
private fun StealthSidebarButton(
    stealthStatus: StealthStatus,
    onClick: () -> Unit
) {
    val isActive = stealthStatus == StealthStatus.ACTIVE
    val isWaiting = stealthStatus == StealthStatus.WAIT

    val bg = if (isActive) NexusColors.DangerRedSubtle else NexusColors.SurfaceCard
    val border = if (isActive) NexusColors.DangerRed else NexusColors.DangerRedBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(bg)
            .border(NexusDimens.BorderHairline, border, RoundedCornerShape(NexusDimens.RadiusMd))
            .clickable(enabled = !isWaiting, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("drawer_stealth_btn"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_eye_off),
                contentDescription = null,
                tint = if (isActive) NexusColors.DangerRed else NexusColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = when (stealthStatus) {
                    StealthStatus.ACTIVE -> "Stealth Active"
                    StealthStatus.WAIT -> "Processing..."
                    StealthStatus.ARMED -> "Stealth Mode"
                },
                style = MaterialTheme.typography.labelMedium,
                color = NexusColors.TextPrimary
            )
        }
        TacticalBadgePill(
            text = stealthStatus.name,
            textColor = when (stealthStatus) {
                StealthStatus.ACTIVE -> NexusColors.TextPrimary
                StealthStatus.WAIT -> NexusColors.WarningAmber
                StealthStatus.ARMED -> NexusColors.DangerRed
            },
            backgroundColor = when (stealthStatus) {
                StealthStatus.ACTIVE -> NexusColors.DangerRed
                StealthStatus.WAIT -> NexusColors.WarningAmberSubtle
                StealthStatus.ARMED -> Color.Transparent
            },
            borderColor = when (stealthStatus) {
                StealthStatus.ACTIVE -> NexusColors.DangerRed
                StealthStatus.WAIT -> NexusColors.WarningAmberBorder
                StealthStatus.ARMED -> NexusColors.DangerRedBorder
            }
        )
    }
}
