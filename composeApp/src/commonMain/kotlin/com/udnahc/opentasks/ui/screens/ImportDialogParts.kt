package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.import_success
import opentasks.composeapp.generated.resources.importing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FileImportDialogContent(
    title: String,
    description: String,
    chooseFileText: String,
    isLoading: Boolean,
    importedCount: Int?,
    errorText: String?,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    isLoading -> {
                        ImportLoadingRow()
                    }

                    importedCount != null -> {
                        ImportSuccessText(stringResource(Res.string.import_success, importedCount))
                    }

                    errorText != null -> {
                        ImportErrorText(errorText)
                    }

                    else -> {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                importedCount != null || errorText != null -> {
                    ImportDoneButton(onDismiss)
                }

                isLoading -> {}
                else -> {
                    PrimaryDialogTextButton(
                        text = chooseFileText,
                        onClick = onPickFile,
                    )
                }
            }
        },
        dismissButton = {
            if (!isLoading && importedCount == null) {
                ImportCancelButton(onDismiss)
            }
        },
    )
}

@Composable
internal fun ImportLoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.width(OpenTasksTheme.dimens.spacerXLarge))
        Text(stringResource(Res.string.importing))
    }
}

@Composable
internal fun ImportSuccessText(text: String) {
    Text(
        text = text,
        color = PrimaryBlue,
    )
}

@Composable
internal fun ImportErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun ImportDoneButton(onClick: () -> Unit) {
    DialogDoneTextButton(onClick = onClick)
}

@Composable
internal fun ImportCancelButton(onClick: () -> Unit) {
    DialogCancelTextButton(onClick = onClick)
}
