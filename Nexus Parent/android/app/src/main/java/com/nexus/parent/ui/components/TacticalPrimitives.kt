package com.nexus.parent.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.parent.R
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * TacticalPrimitives.kt - Shared monochrome building blocks used across all Nexus Parent screens.
 *
 * Hey friend! Centralizing these components guarantees consistent hairline borders, rounded
 * capsules, Lucide vector icon tinting, animated pulsing status LEDs, and empty states.
 */

@Composable
fun TacticalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = NexusColors.SurfaceCard,
    borderColor: Color = NexusColors.BorderSubtle,
    cornerRadius: Dp = NexusDimens.RadiusLg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        border = BorderStroke(NexusDimens.BorderHairline, borderColor)
    ) {
        Column(content = content)
    }
}

@Composable
fun CapsuleSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = NexusColors.SurfaceSidebar.copy(alpha = 0.94f),
    borderColor: Color = NexusColors.BorderMedium,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NexusDimens.RadiusCapsule),
        color = containerColor,
        border = BorderStroke(NexusDimens.BorderHairline, borderColor),
        shadowElevation = 6.dp
    ) {
        content()
    }
}

@Composable
fun StatusPulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = true,
    size: Dp = 8.dp
) {
    val alpha = if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SpinningLoaderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = NexusColors.TextPrimary
) {
    // 0.75s linear 360-degree rotation matching Desktop assets/icons/animations/loading.svg
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_loading_ring_track),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_loading_ring_arc),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .rotate(angle)
        )
    }
}

@Composable
fun TacticalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = null,
            tint = NexusColors.TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = NexusColors.TextPrimary),
                cursorBrush = SolidColor(NexusColors.AccentWhite),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Clear search",
                    tint = NexusColors.TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun TacticalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    containerColor: Color = NexusColors.AccentWhite,
    contentColor: Color = NexusColors.TextInverse
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(NexusDimens.RadiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = NexusColors.SurfacePressed,
            disabledContentColor = NexusColors.TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TacticalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    tintColor: Color = NexusColors.TextPrimary,
    borderColor: Color = NexusColors.BorderMedium
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(NexusDimens.RadiusMd),
        color = NexusColors.SurfaceCard,
        border = BorderStroke(NexusDimens.BorderHairline, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (enabled) tintColor else NexusColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) tintColor else NexusColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TacticalBadgePill(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = NexusColors.TextSecondary,
    backgroundColor: Color = NexusColors.SurfaceHover,
    borderColor: Color = NexusColors.BorderSubtle
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(NexusDimens.BorderHairline, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = NexusTypography.MonoBadge.copy(fontSize = 10.sp),
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun TacticalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = NexusColors.TextInverse,
            checkedTrackColor = NexusColors.AccentWhite,
            uncheckedThumbColor = NexusColors.TextMuted,
            uncheckedTrackColor = NexusColors.SurfacePressed,
            uncheckedBorderColor = NexusColors.BorderMedium
        )
    )
}

@Composable
fun TacticalEmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(NexusDimens.RadiusLg))
                .background(NexusColors.SurfaceCard)
                .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusLg)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = NexusColors.TextMuted,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NexusColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = NexusColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
