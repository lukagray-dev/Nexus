package com.nexus.parent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.CameraLens
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.state.ScreenQuality
import com.nexus.parent.state.SettingsValues

/**
 * SurveillanceDvrPane.kt - Tab 3 of Settings Console (`surveillance-dvr/`).
 */
@Composable
fun SurveillanceDvrPane(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Live Video Capture") {
            SettingsSegmentedRow(
                title = "Screen Mirror Quality",
                subtitle = "Resolution preset requested from child MediaProjection stream.",
                options = ScreenQuality.entries,
                selected = settings.screenQuality,
                labelProvider = { it.label },
                onSelect = { q -> onUpdateSettings { it.copy(screenQuality = q) } }
            )
            SettingsSegmentedRow(
                title = "Preferred Default Camera Lens",
                subtitle = "Sensor selected initially when Camera Feed starts.",
                options = CameraLens.entries,
                selected = settings.preferredCameraLens,
                labelProvider = { it.label },
                onSelect = { lens -> onUpdateSettings { it.copy(preferredCameraLens = lens) } }
            )
        }

        SettingsSectionGroup(title = "Rolling DVR & Export") {
            SettingsSegmentedRow(
                title = "Rolling DVR Buffer Duration",
                subtitle = "Time-shift history window retained during live Camera and Screen sessions.",
                options = DvrDuration.entries,
                selected = settings.dvrBufferDuration,
                labelProvider = { it.label },
                onSelect = { dur -> onUpdateSettings { it.copy(dvrBufferDuration = dur) } }
            )
            SettingsSegmentedRow(
                title = "Export Clip Format",
                subtitle = "Container format used by Save Clip exports.",
                options = ClipFormat.entries,
                selected = settings.dvrClipFormat,
                labelProvider = { it.label },
                onSelect = { fmt -> onUpdateSettings { it.copy(dvrClipFormat = fmt) } }
            )
            SettingsToggleRow(
                title = "Hardware Video Acceleration",
                subtitle = "Uses MediaCodec + EglRenderer GPU decoding on Android.",
                checked = settings.hardwareAcceleration,
                onCheckedChange = { v -> onUpdateSettings { it.copy(hardwareAcceleration = v) } }
            )
        }
    }
}
