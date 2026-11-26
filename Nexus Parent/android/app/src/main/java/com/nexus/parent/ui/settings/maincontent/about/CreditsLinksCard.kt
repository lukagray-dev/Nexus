package com.nexus.parent.ui.settings.maincontent.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

object NexusProjectLinks {
    const val REPOSITORY_URL = "https://github.com/lukagray-dev/Nexus"
    const val DOCUMENTATION_URL = "https://github.com/lukagray-dev/Nexus/tree/main/docs"
    const val REPORT_BUG_URL = "https://github.com/lukagray-dev/Nexus/issues"
    const val FOLLOW_CREATOR_URL = "https://www.instagram.com/lukagray.official/"
    const val CREATOR_EMAIL_URL = "mailto:heylukagray@gmail.com"
}

/**
 * CreditsLinksCard.kt - Maintainer attribution, GNU AGPLv3 license notice, and official
 * project links matching Desktop `about/credits-links.ts`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditsLinksCard(
    onOpenUrl: (String) -> Unit
) {
    SettingsSectionGroup(title = "Credits, License & Links") {
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
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Luka Gray (aka Soumo Mukherjee) • West Bengal, India",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Copyright © 2026 • Licensed under GNU Affero General Public License v3.0 or later (AGPLv3)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }
            TacticalBadgePill(
                text = "AGPL-3.0",
                textColor = NexusColors.TextSecondary,
                backgroundColor = NexusColors.SurfaceSidebar
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Project & Support Resources",
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusColors.TextPrimary
                )
                Text(
                    text = "Open official documentation, source code, issue tracker, or creator channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted
                )
            }

            val links = listOf(
                "GitHub Repository" to NexusProjectLinks.REPOSITORY_URL,
                "Documentation" to NexusProjectLinks.DOCUMENTATION_URL,
                "Report Issue" to NexusProjectLinks.REPORT_BUG_URL,
                "Follow Creator" to NexusProjectLinks.FOLLOW_CREATOR_URL,
                "Contact Email" to NexusProjectLinks.CREATOR_EMAIL_URL
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                links.forEach { (label, url) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceSidebar)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onOpenUrl(url) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("about_link_${label.lowercase().replace(" ", "_")}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
