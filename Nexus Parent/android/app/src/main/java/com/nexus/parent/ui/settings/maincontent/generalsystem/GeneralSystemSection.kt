package com.nexus.parent.ui.settings.maincontent.generalsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.settings.maincontent.SettingsSectionGroup

/**
 * GeneralSystemSection.kt - Tab 1 of Settings Console (`general-system/general-system.ts`).
 */
@Composable
fun GeneralSystemSection(
    settings: SettingsValues,
    onUpdateSettings: ((SettingsValues) -> SettingsValues) -> Unit,
    onBrowseDownloadsFolder: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionGroup(title = "Connection & Display") {
            AutoConnectLastDeviceSetting(
                checked = settings.autoConnectLastDevice,
                onUpdateSettings = onUpdateSettings
            )
            KeepScreenAwakeSetting(
                checked = settings.keepScreenAwake,
                onUpdateSettings = onUpdateSettings
            )
        }

        SettingsSectionGroup(title = "Downloads & Storage") {
            DownloadsDirectorySetting(
                directoryLabel = settings.downloadsDirectoryLabel,
                hasCustomFolder = !settings.downloadsTreeUri.isNullOrBlank(),
                disabled = settings.askBeforeDownload,
                onBrowseClick = onBrowseDownloadsFolder,
                onResetClick = {
                    val defaults = SettingsValues()
                    onUpdateSettings {
                        it.copy(
                            downloadsDirectoryLabel = defaults.downloadsDirectoryLabel,
                            downloadsTreeUri = null
                        )
                    }
                }
            )
            AskBeforeDownloadSetting(
                checked = settings.askBeforeDownload,
                onUpdateSettings = onUpdateSettings
            )
        }

        SafetyAlertsSetting(
            settings = settings,
            onUpdateSettings = onUpdateSettings
        )
    }
}
