package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.udnahc.opentasks.viewmodel.ImportCsvUiState
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.choose_csv_file
import opentasks.composeapp.generated.resources.csv_import_description
import opentasks.composeapp.generated.resources.import_csv_ticktick
import opentasks.composeapp.generated.resources.import_error
import opentasks.composeapp.generated.resources.no_tasks_in_file
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImportCsvDialog(
    viewModel: ImportCsvViewModel,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    ImportCsvDialogContent(
        uiState = uiState,
        onPickFile = onPickFile,
        onDismiss = {
            viewModel.resetState()
            onDismiss()
        },
    )
}

@Composable
internal fun ImportCsvDialogContent(
    uiState: ImportCsvUiState,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorText = uiState.error?.let { error ->
        if (error == "No tasks found in file") {
            stringResource(Res.string.no_tasks_in_file)
        } else {
            stringResource(Res.string.import_error, error)
        }
    }

    FileImportDialogContent(
        title = stringResource(Res.string.import_csv_ticktick),
        description = stringResource(Res.string.csv_import_description),
        chooseFileText = stringResource(Res.string.choose_csv_file),
        isLoading = uiState.isLoading,
        importedCount = uiState.importedCount,
        errorText = errorText,
        onPickFile = onPickFile,
        onDismiss = onDismiss,
    )
}
