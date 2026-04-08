package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.ImportIcsDialogContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.ImportIcsUiState

@Composable
@LightDarkPreview
private fun ImportIcsDialogPreview() {
    OpenTasksTheme {
        ImportIcsDialogContent(
            uiState = ImportIcsUiState(),
            onPickFile = {},
            onDismiss = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ImportIcsDialogSuccessPreview() {
    OpenTasksTheme {
        ImportIcsDialogContent(
            uiState = ImportIcsUiState(importedCount = 8),
            onPickFile = {},
            onDismiss = {},
        )
    }
}
