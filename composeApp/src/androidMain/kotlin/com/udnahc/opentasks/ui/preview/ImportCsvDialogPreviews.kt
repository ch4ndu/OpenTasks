package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.ImportCsvDialogContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.ImportCsvUiState

@Composable
@LightDarkPreview
private fun ImportCsvDialogPreview() {
    OpenTasksTheme {
        ImportCsvDialogContent(
            uiState = ImportCsvUiState(),
            onPickFile = {},
            onDismiss = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ImportCsvDialogSuccessPreview() {
    OpenTasksTheme {
        ImportCsvDialogContent(
            uiState = ImportCsvUiState(importedCount = 12),
            onPickFile = {},
            onDismiss = {},
        )
    }
}
