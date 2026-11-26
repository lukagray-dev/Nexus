package com.nexus.parent.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.FeatureId
import com.nexus.parent.ui.components.CapsuleSurface
import com.nexus.parent.ui.components.StatusPulseDot
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * TopCapsuleBar.kt - Floating three-capsule top toolbar matching legacy Android geometry
 * updated to the strict Monochrome Tactical theme.
 *
 * Hey friend! Just like `toolbar_main.xml` from `.temp/Nexus`, we separate the top bar into
 * three floating pills:
 * 1. Left Capsule: Drawer toggle button.
 * 2. Center Capsule: App title or active feature header with live dot + close button.
 * 3. Right Capsule: Floating Mic HUD toggle and Settings shortcut.
 */
@Composable
fun TopCapsuleBar(
    activeFeature: FeatureId?,
    connectionPhase: ConnectionPhase,
    isMicLive: Boolean,
    isMicOverlayOpen: Boolean,
    isActiveFeatureLive: Boolean,
    onMenuClick: () -> Unit,
    onCloseFeature: () -> Unit,
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexusDimens.SpacingMd, vertical = NexusDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Left Menu Capsule
        CapsuleSurface(
            modifier = Modifier
                .size(NexusDimens.CapsuleHeight)
                .testTag("capsule_menu_btn")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_panel_left),
                    contentDescription = stringResource(R.string.cd_menu),
                    tint = NexusColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2. Center Title / Active Feature Capsule
        CapsuleSurface(
            modifier = Modifier
                .weight(1f)
                .height(NexusDimens.CapsuleHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (activeFeature != null) {
                    if (isActiveFeatureLive) {
                        StatusPulseDot(
                            color = NexusColors.DangerRed,
                            size = 7.dp
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                    } else {
                        Icon(
                            painter = painterResource(id = activeFeature.iconRes),
                            contentDescription = null,
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                    }
                    Text(
                        text = activeFeature.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NexusColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onCloseFeature)
                            .testTag("capsule_close_feature_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_close),
                            tint = NexusColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    if (connectionPhase == ConnectionPhase.CONNECTED) {
                        StatusPulseDot(color = NexusColors.LiveGreen, size = 7.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (connectionPhase == ConnectionPhase.RECONNECTING) {
                        StatusPulseDot(color = NexusColors.WarningAmber, size = 7.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = NexusColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Right Quick-Actions Capsule (Floating Mic + Settings)
        CapsuleSurface(
            modifier = Modifier.height(NexusDimens.CapsuleHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMicClick)
                        .testTag("capsule_mic_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.cd_mic_toggle),
                        tint = when {
                            isMicLive -> NexusColors.LiveGreen
                            isMicOverlayOpen -> NexusColors.TextPrimary
                            else -> NexusColors.TextSecondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSettingsClick)
                        .testTag("capsule_settings_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = NexusColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
