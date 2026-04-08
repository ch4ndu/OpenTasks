package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.SettingsCategoryHeader
import com.udnahc.opentasks.ui.screens.SettingsContent
import com.udnahc.opentasks.ui.screens.SettingsRow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.SyncStatus

@Composable
@LightDarkPreview
private fun SettingsContentPreview() {
    OpenTasksTheme {
        SettingsContent(
            currentUrl = null,
            syncStatus = SyncStatus.IDLE,
            onBack = {},
            onSaveUrl = {},
            onClearUrl = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun SettingsContentConnectedPreview() {
    OpenTasksTheme {
        SettingsContent(
            currentUrl = "http://192.168.1.100:8090",
            syncStatus = SyncStatus.SUCCESS,
            onBack = {},
            onSaveUrl = {},
            onClearUrl = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun SettingsRowPreview() {
    OpenTasksTheme {
        SettingsRow(
            title = "PocketBase URL",
            summary = "http://192.168.1.100:8090",
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun SettingsCategoryHeaderPreview() {
    OpenTasksTheme {
        SettingsCategoryHeader(title = "Appearance")
    }
}
