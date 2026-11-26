package com.nexus.parent.ui.settings.maincontent.about

import android.os.Build
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.parent.BuildConfig
import com.nexus.parent.R
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.ui.components.TacticalSecondaryButton
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusDimens
import com.nexus.parent.ui.theme.NexusTypography

/**
 * RuntimeEnvironmentCard.kt - Live host Android environment, hardware decode status, and
 * copyable/openable local directory rows matching Desktop `about/runtime-environment.ts`.
 */
@Composable
fun RuntimeEnvironmentCard(
    state: ParentUiState,
    onCopyValue: (label: String, value: String) -> Unit,
    onOpenDownloadsFolder: () -> Unit
) {
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    val cpuCores = Runtime.getRuntime().availableProcessors()
    val gpuText = if (state.settings.hardwareAcceleration) "On" else "Off"
    val downloadsDisplay = if (state.settings.askBeforeDownload) {
        "Prompt every time (Save As dialog active)"
    } else {
        state.settings.downloadsDirectoryLabel
    }
    val canOpenDownloads = !state.settings.askBeforeDownload && !state.settings.downloadsTreeUri.isNullOrBlank()
    val updatesPath = state.appUpdate.updatesDirPath.ifBlank { "files/updates" }

    SettingsSectionGroup(title = "System & Runtime Environment") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RuntimeSpecTile(
                label = "HOST OS & ABI",
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • $primaryAbi",
                modifier = Modifier.weight(1f)
            )
            RuntimeSpecTile(
                label = "CORE RUNTIME",
                value = "Jetpack Compose • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RuntimeSpecTile(
                label = "VIDEO RENDERER",
                value = "WebRTC 123.0.0 • TextureView + EglRenderer",
                modifier = Modifier.weight(1f)
            )
            RuntimeSpecTile(
                label = "CPU & GPU",
                value = "$cpuCores Cores • GPU $gpuText (${Build.MODEL})",
                modifier = Modifier.weight(1f)
            )
        }

        AboutPathRow(
            title = "App Preferences Store",
            subtitle = "Stores SettingsValues, paired device history & cached telemetry",
            pathValue = state.localStorageMetrics.prefsPathLabel,
            onCopy = { onCopyValue("Preferences path", state.localStorageMetrics.prefsPathLabel) }
        )
        AboutPathRow(
            title = "Local Staged Updates",
            subtitle = "Stores downloaded GitHub release APKs & SHA-256 verified packages",
            pathValue = updatesPath,
            onCopy = { onCopyValue("Staged updates directory", updatesPath) }
        )
        AboutPathRow(
            title = "Downloads Directory",
            subtitle = "Target folder for Monitor Storage exports and DVR clips",
            pathValue = downloadsDisplay,
            onCopy = { onCopyValue("Downloads directory", downloadsDisplay) },
            onOpen = if (canOpenDownloads) onOpenDownloadsFolder else null
        )
    }
}

@Composable
private fun RuntimeSpecTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = NexusTypography.SectionHeader, color = NexusColors.TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextPrimary)
    }
}

@Composable
private fun AboutPathRow(
    title: String,
    subtitle: String,
    pathValue: String,
    onCopy: () -> Unit,
    onOpen: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexusDimens.RadiusMd))
            .background(NexusColors.SurfaceCard)
            .border(NexusDimens.BorderHairline, NexusColors.BorderSubtle, RoundedCornerShape(NexusDimens.RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = NexusColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NexusColors.TextMuted)
        }
        Text(
            text = pathValue,
            style = NexusTypography.MonoBadge,
            color = NexusColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TacticalSecondaryButton(
                text = "Copy",
                iconRes = R.drawable.ic_file_text,
                onClick = onCopy
            )
            if (onOpen != null) {
                TacticalSecondaryButton(
                    text = "Open",
                    iconRes = R.drawable.ic_folder,
                    onClick = onOpen
                )
            }
        }
    }
}
