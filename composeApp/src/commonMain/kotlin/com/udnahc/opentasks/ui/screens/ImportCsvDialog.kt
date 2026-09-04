package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.udnahc.opentasks.viewmodel.ImportCsvUiState
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import com.udnahc.opentasks.viewmodel.ImportErrorState
import com.udnahc.opentasks.viewmodel.ImportErrorType
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.choose_csv_file
import opentasks.composeapp.generated.resources.csv_import_description
import opentasks.composeapp.generated.resources.import_csv_ticktick
import opentasks.composeapp.generated.resources.import_error
import opentasks.composeapp.generated.resources.import_failed_generic
import opentasks.composeapp.generated.resources.import_file_too_large
import opentasks.composeapp.generated.resources.calendar_access_denied_import
import opentasks.composeapp.generated.resources.calendar_import_invalid_response
import opentasks.composeapp.generated.resources.calendar_import_too_many_events
import opentasks.composeapp.generated.resources.calendar_import_transport_failed
import opentasks.composeapp.generated.resources.no_events_in_file
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
        importErrorText(error)
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

@Composable
internal fun importErrorText(error: ImportErrorState): String = when (error.type) {
    ImportErrorType.FILE_TOO_LARGE -> stringResource(Res.string.import_file_too_large)
    ImportErrorType.EMPTY_CSV_FILE -> stringResource(Res.string.no_tasks_in_file)
    ImportErrorType.EMPTY_ICS_FILE -> stringResource(Res.string.no_events_in_file)
    ImportErrorType.CALENDAR_ACCESS_DENIED ->
        stringResource(Res.string.calendar_access_denied_import)
    ImportErrorType.CALENDAR_TRANSPORT ->
        stringResource(Res.string.calendar_import_transport_failed)
    ImportErrorType.CALENDAR_INVALID_RESPONSE ->
        stringResource(Res.string.calendar_import_invalid_response)
    ImportErrorType.CALENDAR_TOO_MANY_EVENTS ->
        stringResource(Res.string.calendar_import_too_many_events)
    ImportErrorType.GENERIC -> error.detail?.let { detail ->
        stringResource(Res.string.import_error, detail)
    } ?: stringResource(Res.string.import_failed_generic)
}
