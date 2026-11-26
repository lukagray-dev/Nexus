package com.nexus.parent.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.FeatureRegistry
import com.nexus.parent.ui.components.TacticalCard
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * PairingScreen.kt - Phase 1 Empty State Pairing Card matching Desktop `empty-state.ts`.
 *
 * Hey friend! Whenever no child phone is connected, the center viewport renders this crisp
 * monochrome pairing card with 12-digit numeric validation, clear action, and a 1-click
 * Quick Connect shortcut for the last paired child phone.
 */
@Composable
fun PairingScreen(
    digits: String,
    statusMessage: String?,
    errorMessage: String?,
    lastPairedDevice: DeviceRecord?,
    onDigitsChange: (String) -> Unit,
    onClearDigits: () -> Unit,
    onConnectClick: () -> Unit,
    onQuickConnectRecent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = FeatureRegistry.isValidChildId(digits)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusColors.CanvasBlack)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        TacticalCard(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            containerColor = NexusColors.SurfaceSidebar,
            borderColor = NexusColors.BorderMedium,
            cornerRadius = NexusDimens.RadiusXl
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(NexusDimens.RadiusXl))
                        .background(NexusColors.SurfaceCard)
                        .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusXl)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_nexus_child),
                        contentDescription = "Nexus Child Logo",
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connect Child Device",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexusColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the 12-digit Child ID from Nexus Child to establish an encrypted P2P connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 12-digit Numeric Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                        .background(NexusColors.CanvasBlack)
                        .border(
                            NexusDimens.BorderHairline,
                            if (errorMessage != null) NexusColors.DangerRed else NexusColors.BorderMedium,
                            RoundedCornerShape(NexusDimens.RadiusMd)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_key_round),
                        contentDescription = null,
                        tint = NexusColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (digits.isEmpty()) {
                            Text(
                                text = "Enter 12-digit Child ID",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexusColors.TextMuted
                            )
                        }
                        BasicTextField(
                            value = digits,
                            onValueChange = onDigitsChange,
                            singleLine = true,
                            textStyle = NexusTypography.MonoValue.copy(color = NexusColors.TextPrimary),
                            cursorBrush = SolidColor(NexusColors.AccentWhite),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (isReady) onConnectClick() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pairing_id_input")
                        )
                    }
                    if (digits.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onClearDigits),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Clear ID",
                                tint = NexusColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val feedback = errorMessage ?: statusMessage
                if (!feedback.isNullOrBlank()) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorMessage != null) NexusColors.DangerRed else NexusColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                TacticalPrimaryButton(
                    text = "Connect",
                    iconRes = R.drawable.ic_link,
                    enabled = isReady,
                    onClick = onConnectClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_connect_btn")
                )

                if (lastPairedDevice != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = NexusColors.BorderSubtle)
                        Text(
                            text = "or reconnect to recent child",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusColors.TextMuted
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = NexusColors.BorderSubtle)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable { onQuickConnectRecent(lastPairedDevice.rawId) }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("pairing_quick_connect_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_nexus_child),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = lastPairedDevice.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NexusColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = lastPairedDevice.formattedId,
                                    style = NexusTypography.MonoBadge,
                                    color = NexusColors.TextMuted
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Quick Connect",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextSecondary
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_right),
                                contentDescription = null,
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
