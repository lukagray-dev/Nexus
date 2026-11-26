package com.nexus.parent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.ActiveCallAlert
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens

/**
 * ActiveCallBanner.kt - Floating alert banner matching `nexus-call-alert-banner` on Desktop.
 *
 * Hey friend! When the child phone starts ringing or enters an active cellular call,
 * this banner slides in from the top with a 1-click "Turn On Mic" shortcut and Dismiss button.
 */
@Composable
fun ActiveCallBanner(
    alert: ActiveCallAlert?,
    onTurnOnMic: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (alert != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(NexusDimens.RadiusMd),
                color = NexusColors.SurfaceCard,
                border = BorderStroke(NexusDimens.BorderHairline, NexusColors.LiveGreenBorder),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
                            .background(NexusColors.LiveGreenSubtle)
                            .border(NexusDimens.BorderHairline, NexusColors.LiveGreenBorder, RoundedCornerShape(NexusDimens.RadiusSm)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_phone_call),
                            contentDescription = null,
                            tint = NexusColors.LiveGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = alert.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(NexusColors.LiveGreenDark)
                            .border(NexusDimens.BorderHairline, NexusColors.LiveGreen, RoundedCornerShape(5.dp))
                            .clickable(onClick = onTurnOnMic)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mic),
                            contentDescription = null,
                            tint = NexusColors.TextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Listen",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusColors.TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(5.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Dismiss call alert",
                            tint = NexusColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
