package com.nexus.parent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.parent.R
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * TacticalDialog.kt - Reusable monochrome modal shell and confirmation dialogs.
 *
 * Hey friend! All modals in Nexus Parent share the same header bar, hairline border,
 * #121212/#181818 surface contrast, and clean action footer.
 */
@Composable
fun TacticalModalDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(NexusDimens.RadiusLg),
            color = NexusColors.SurfaceSidebar,
            border = BorderStroke(NexusDimens.BorderHairline, NexusColors.BorderMedium)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusColors.SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NexusColors.TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close dialog",
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                HorizontalDivider(color = NexusColors.BorderSubtle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                if (footer != null) {
                    HorizontalDivider(color = NexusColors.BorderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexusColors.SurfaceCard)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalConfirmDialog(
    title: String,
    message: String,
    noteText: String? = null,
    confirmText: String,
    isDangerAction: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    TacticalModalDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            TacticalSecondaryButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(10.dp))
            TacticalPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                containerColor = if (isDangerAction) NexusColors.DangerRed else NexusColors.AccentWhite,
                contentColor = if (isDangerAction) NexusColors.TextPrimary else NexusColors.TextInverse
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NexusColors.TextPrimary
        )
        if (!noteText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                    .background(NexusColors.SurfaceCard)
                    .padding(12.dp)
            ) {
                Text(
                    text = "NOTE: $noteText",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun TacticalNoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    TacticalModalDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            TacticalPrimaryButton(
                text = "OK",
                onClick = onDismiss,
                containerColor = Color.White,
                contentColor = Color.Black
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NexusColors.TextPrimary
        )
    }
}
