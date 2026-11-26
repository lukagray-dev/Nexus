package com.nexus.parent.ui.monitor.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexus.parent.R
import com.nexus.parent.state.AppLockAttemptItem
import com.nexus.parent.state.AppLockUiState
import com.nexus.parent.ui.components.TacticalBadgePill
import com.nexus.parent.ui.components.TacticalEmptyState
import com.nexus.parent.ui.components.TacticalModalDialog
import com.nexus.parent.ui.components.TacticalPrimaryButton
import com.nexus.parent.ui.components.TacticalSearchField
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.components.TacticalSwitch
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * AppLockPanel.kt - Remote App Lock & Intruder Selfie Audit UI (`right-sidebar/applock/applock.ts`).
 */
@Composable
fun AppLockPanel(
    state: AppLockUiState,
    onToggleRemoteLock: () -> Unit,
    onOpenChangePin: () -> Unit,
    onDismissChangePin: () -> Unit,
    onConfirmNewPin: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectSelfiePreview: (AppLockAttemptItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = state.searchQuery.trim().lowercase()
    val filtered = if (q.isEmpty()) {
        state.attempts
    } else {
        state.attempts.filter {
            it.timestampLabel.lowercase().contains(q) ||
                (if (it.pinCorrect) "unlocked" else if (it.isLockedOut) "locked out" else "failed").contains(q)
        }
    }

    val total = state.attempts.size
    val success = state.attempts.count { it.pinCorrect }
    val failed = state.attempts.count { !it.pinCorrect }
    val lockouts = state.attempts.count { it.isLockedOut }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "APP LOCK STATUS",
                        style = NexusTypography.SectionHeader,
                        color = NexusColors.TextMuted
                    )
                    Text(
                        text = if (state.isLockEnabled) "ACTIVE" else "DISABLED",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isLockEnabled) NexusColors.LiveGreen else NexusColors.TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TacticalSecondaryButton(
                        text = "Change PIN",
                        onClick = onOpenChangePin
                    )
                    TacticalSwitch(
                        checked = state.isLockEnabled,
                        onCheckedChange = { onToggleRemoteLock() }
                    )
                }
            }

            TacticalSearchField(
                query = state.searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search attempts by status or time..."
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLockStatCell("TOTAL", total, NexusColors.TextPrimary, Modifier.weight(1f))
                AppLockStatCell("SUCCESS", success, NexusColors.LiveGreen, Modifier.weight(1f))
                AppLockStatCell("FAILED", failed, NexusColors.WarningAmber, Modifier.weight(1f))
                AppLockStatCell("LOCKOUTS", lockouts, NexusColors.DangerRed, Modifier.weight(1f))
            }
        }

        if (filtered.isEmpty()) {
            TacticalEmptyState(
                iconRes = R.drawable.ic_lock,
                title = "No Unlock Attempts Recorded",
                description = "Tap the Play button in the bottom dock to monitor App Lock PIN attempts and intruder selfies.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { attempt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                            .background(NexusColors.SurfaceCard)
                            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
                            .clickable(enabled = attempt.hasSelfiePreview) { onSelectSelfiePreview(attempt) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TacticalBadgePill(
                                text = when {
                                    attempt.pinCorrect -> "UNLOCKED"
                                    attempt.isLockedOut -> "LOCKED OUT"
                                    else -> "FAILED (${attempt.failedAttempts})"
                                },
                                textColor = when {
                                    attempt.pinCorrect -> NexusColors.LiveGreen
                                    attempt.isLockedOut -> NexusColors.DangerRed
                                    else -> NexusColors.WarningAmber
                                }
                            )
                            Text(
                                text = attempt.timestampLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusColors.TextMuted
                            )
                        }
                        if (attempt.hasSelfiePreview) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera),
                                contentDescription = "View intruder selfie",
                                tint = NexusColors.TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showChangePinDialog) {
        var pinInput by remember { mutableStateOf("") }
        TacticalModalDialog(
            title = "Set Remote 6-Digit PIN",
            onDismiss = onDismissChangePin,
            footer = {
                TacticalSecondaryButton(text = "Cancel", onClick = onDismissChangePin)
                Spacer(modifier = Modifier.width(8.dp))
                TacticalPrimaryButton(
                    text = "Update PIN",
                    enabled = pinInput.length == 6,
                    onClick = { onConfirmNewPin(pinInput) }
                )
            }
        ) {
            Text(
                text = "Enter a new 6-digit numeric PIN for Nexus Child App Lock.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextSecondary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexusDimens.RadiusMd))
                    .background(NexusColors.CanvasBlack)
                    .border(NexusDimens.BorderHairline, NexusColors.BorderMedium, RoundedCornerShape(NexusDimens.RadiusMd))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (pinInput.isEmpty()) {
                    Text("000000", style = NexusTypography.MonoValue, color = NexusColors.TextMuted)
                }
                BasicTextField(
                    value = pinInput,
                    onValueChange = { raw -> pinInput = raw.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    textStyle = NexusTypography.MonoValue.copy(color = NexusColors.TextPrimary),
                    cursorBrush = SolidColor(NexusColors.AccentWhite),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (state.previewingSelfieAttempt != null) {
        TacticalModalDialog(
            title = "Captured Intruder Selfie",
            onDismiss = { onSelectSelfiePreview(null) },
            footer = {
                TacticalPrimaryButton(text = "Close", onClick = { onSelectSelfiePreview(null) })
            }
        ) {
            Text(
                text = "Captured at ${state.previewingSelfieAttempt.timestampLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.TextMuted
            )
        }
    }
}

@Composable
private fun AppLockStatCell(
    label: String,
    count: Int,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusSm))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusSm))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
        Text(
            text = label,
            style = NexusTypography.MonoBadge,
            color = NexusColors.TextMuted
        )
    }
}
