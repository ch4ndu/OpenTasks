package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.viewmodel.ImportCsvUiState
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.choose_csv_file
import opentasks.composeapp.generated.resources.csv_import_description
import opentasks.composeapp.generated.resources.import_csv_ticktick
import opentasks.composeapp.generated.resources.import_error
import opentasks.composeapp.generated.resources.import_success
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.import_csv_ticktick),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    uiState.isLoading -> {
                        ImportLoadingRow()
                    }
                    uiState.importedCount != null -> {
                        ImportSuccessText(stringResource(Res.string.import_success, uiState.importedCount))
                    }
                    uiState.error != null -> {
                        val errorText = if (uiState.error == "No tasks found in file") {
                            stringResource(Res.string.no_tasks_in_file)
                        } else {
                            stringResource(Res.string.import_error, uiState.error)
                        }
                        ImportErrorText(errorText)
                    }
                    else -> {
                        Text(
                            text = stringResource(Res.string.csv_import_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                uiState.importedCount != null || uiState.error != null -> {
                    ImportDoneButton(onDismiss)
                }
                uiState.isLoading -> {}
                else -> {
                    TextButton(onClick = onPickFile) {
                        Text(stringResource(Res.string.choose_csv_file), color = PrimaryBlue)
                    }
                }
            }
        },
        dismissButton = {
            if (!uiState.isLoading && uiState.importedCount == null) {
                ImportCancelButton(onDismiss)
            }
        },
    )
}
