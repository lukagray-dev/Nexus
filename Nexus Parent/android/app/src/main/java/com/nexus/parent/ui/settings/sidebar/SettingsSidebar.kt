package com.nexus.parent.ui.settings.sidebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexus.parent.state.SettingsTabId
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * SettingsSidebar.kt - Adaptive settings navigation mirroring Desktop `settings/sidebar/sidebar.ts`.
 *
 * Hey friend! On wide screens / tablets we show the full left navigation column just like Desktop;
 * on compact phones we show a crisp horizontal pill bar so the detail settings get maximum width.
 */
@Composable
fun SettingsSidebarRail(
    activeTab: SettingsTabId,
    devicesBadge: String?,
    onSelectTab: (SettingsTabId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(NexusColors.SurfaceSidebar)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SettingsTabId.entries.forEach { tab ->
            SettingsSidebarTabRow(
                tab = tab,
                isActive = activeTab == tab,
                badgeText = if (tab == SettingsTabId.DEVICES) devicesBadge else null,
                onClick = { onSelectTab(tab) }
            )
        }
    }
}

@Composable
fun SettingsSidebarPillStrip(
    activeTab: SettingsTabId,
    devicesBadge: String?,
    onSelectTab: (SettingsTabId) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NexusColors.SurfaceSidebar)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsTabId.entries.forEach { tab ->
            val active = activeTab == tab
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
                if (tab == SettingsTabId.DEVICES && devicesBadge != null) {
                    TacticalBadgePill(
                        text = devicesBadge,
                        textColor = if (active) NexusColors.TextInverse else NexusColors.TextSecondary,
                        backgroundColor = if (active) NexusColors.BorderSubtle else NexusColors.SurfaceSidebar
                    )
                }
            }
        }
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
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) NexusColors.TextPrimary else NexusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badgeText != null) {
            TacticalBadgePill(
                text = badgeText,
                textColor = NexusColors.TextSecondary,
                backgroundColor = NexusColors.SurfaceCard
            )
        }
    }
}
