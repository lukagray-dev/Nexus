package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.data.update.AppUpdateProtocol
import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.AppUpdateUiState
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.settings.maincontent.SettingsToggleRow
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * AutoUpdateSetting.kt - Background GitHub Stable Release checker and APK installer UI
 * matching Desktop `general-system/auto-update.ts` 1:1.
 *
 * Hey friend! Controls automatic background update discovery (`vX.Y.Z` stable releases on
 * `lukagray-dev/Nexus`) and renders the Release Channel card with live status pill and
 * "Check Now" -> "Restart to update" action button.
 */
@Composable
fun AutoUpdateSetting(
    autoCheckUpdates: Boolean,
    appUpdate: AppUpdateUiState,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onRestartToUpdate: () -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    SettingsToggleRow(
        title = "Check for Updates Automatically",
        subtitle = "Checks GitHub stable releases in the background and stages new updates automatically.",
        checked = autoCheckUpdates,
        onCheckedChange = { next ->
            onUpdateSettings { it.copy(autoCheckUpdates = next) }
            onShowToast(
                if (next) {
                    "Automatic GitHub stable release checks enabled"
                } else {
                    "Automatic update checks disabled"
                }
            )
        }
    )

    val badgeLabel = AppUpdateProtocol.formatUpdateStatusBadge(appUpdate)
    val isReady = appUpdate.status == AppUpdateStatusKind.READY
    val isBusy = appUpdate.status == AppUpdateStatusKind.CHECKING ||
        appUpdate.status == AppUpdateStatusKind.DOWNLOADING
    val isError = appUpdate.status == AppUpdateStatusKind.ERROR

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(
                NexusDimens.BorderHairline,
                if (isReady) NexusColors.LiveGreenBorder else NexusColors.BorderSubtle,
                RoundedCornerShape(NexusDimens.RadiusMd)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Release Channel & Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Stable releases from GitHub (lukagray-dev/Nexus)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }

            TacticalBadgePill(
                text = badgeLabel,
                textColor = when {
                    isReady -> NexusColors.LiveGreen
                    isError -> NexusColors.DangerRed
                    isBusy -> NexusColors.WarningAmber
                    else -> NexusColors.TextSecondary
                },
                backgroundColor = NexusColors.SurfaceSidebar,
                borderColor = when {
                    isReady -> NexusColors.LiveGreenBorder
                    isError -> NexusColors.DangerRedBorder
                    else -> NexusColors.BorderSubtle
                },
                modifier = Modifier.testTag("settings_update_status_badge")
            )
        }

        if (isError && !appUpdate.errorMessage.isNullOrBlank()) {
            Text(
                text = appUpdate.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.DangerRed
            )
        }

        if (isReady) {
            TacticalPrimaryButton(
                text = "Restart to update",
                iconRes = R.drawable.ic_refresh_cw,
                onClick = onRestartToUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_restart_to_update_btn")
            )
        } else {
            TacticalSecondaryButton(
                text = if (isBusy) badgeLabel else "Check Now",
                iconRes = R.drawable.ic_refresh_cw,
                enabled = !isBusy,
                onClick = onCheckUpdatesNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_check_updates_btn")
            )
        }
    }
}
