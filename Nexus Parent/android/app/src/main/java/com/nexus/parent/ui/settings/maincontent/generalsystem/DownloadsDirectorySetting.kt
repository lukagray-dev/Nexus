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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * DownloadsDirectorySetting.kt - Default Downloads & Media Save Directory setting row
 * matching Desktop `general-system/downloads-directory.ts`.
 *
 * Hey friend! Uses Android Storage Access Framework (`OpenDocumentTree`) when clicking Browse so
 * users can pick any folder cleanly without legacy storage permissions, and visually disables when
 * "Ask Before Download" is active just like Desktop!
 */
@Composable
fun DownloadsDirectorySetting(
    directoryLabel: String,
    hasCustomFolder: Boolean,
    disabled: Boolean,
    onBrowseClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.55f else 1f)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Default Downloads Directory",
                style = MaterialTheme.typography.labelMedium,
                color = NexusColors.TextPrimary
            )
            Text(
                text = if (disabled) {
                    "Bypassed while 'Ask Before Download' prompts on every file."
                } else {
                    "Default folder where remote files and exported DVR clips are saved."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                .background(NexusColors.SurfaceSidebar)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = directoryLabel,
                style = NexusTypography.MonoValue,
                color = NexusColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TacticalSecondaryButton(
                text = "Browse Folder",
                iconRes = R.drawable.ic_folder,
                enabled = !disabled,
                onClick = onBrowseClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_browse_downloads_btn")
            )
            if (hasCustomFolder) {
                TacticalSecondaryButton(
                    text = "Reset Default",
                    iconRes = R.drawable.ic_refresh_cw,
                    enabled = !disabled,
                    onClick = onResetClick,
                    modifier = Modifier.testTag("settings_reset_downloads_btn")
                )
            }
        }
    }
}
