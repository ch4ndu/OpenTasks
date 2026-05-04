package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.udnahc.opentasks.viewmodel.ImportIcsUiState
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.choose_ics_file
import opentasks.composeapp.generated.resources.ics_import_description
import opentasks.composeapp.generated.resources.import_from_ics
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImportIcsDialog(
    viewModel: ImportIcsViewModel,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    ImportIcsDialogContent(
        uiState = uiState,
        onPickFile = onPickFile,
        onDismiss = {
            viewModel.resetState()
            onDismiss()
        },
    )
}

@Composable
internal fun ImportIcsDialogContent(
    uiState: ImportIcsUiState,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorText = uiState.error?.let { error ->
        importErrorText(error)
    }

    FileImportDialogContent(
        title = stringResource(Res.string.import_from_ics),
        description = stringResource(Res.string.ics_import_description),
        chooseFileText = stringResource(Res.string.choose_ics_file),
        isLoading = uiState.isLoading,
        importedCount = uiState.importedCount,
        errorText = errorText,
        onPickFile = onPickFile,
        onDismiss = onDismiss,
    )
}
