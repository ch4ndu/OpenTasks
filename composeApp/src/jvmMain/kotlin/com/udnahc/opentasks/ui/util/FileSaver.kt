package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import javax.swing.SwingUtilities

@Composable
actual fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
): (FileExportRequest) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember {
        { request ->
            SwingUtilities.invokeLater {
                runCatching {
                    val dialog = FileDialog(
                        null as java.awt.Frame?,
                        "Save export file",
                        FileDialog.SAVE,
                    )
                    dialog.file = request.fileName
                    dialog.isVisible = true
                    val selectedFile = dialog.file
                    val directory = dialog.directory
                    if (selectedFile == null || directory == null) {
                        currentOnResult.value(FileExportResult.Cancelled)
                    } else {
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    File(directory, selectedFile).writeText(request.content)
                                }
                            }.fold(
                                onSuccess = { FileExportResult.Completed },
                                onFailure = { FileExportResult.Error() },
                            )
                            currentOnResult.value(result)
                        }
                    }
                }.onFailure { currentOnResult.value(FileExportResult.Error()) }
            }
        }
    }
}
