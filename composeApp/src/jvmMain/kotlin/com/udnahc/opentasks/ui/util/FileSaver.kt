package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
                var dialog: FileDialog? = null
                try {
                    val activeDialog = FileDialog(
                        null as java.awt.Frame?,
                        "Save export file",
                        FileDialog.SAVE,
                    )
                    dialog = activeDialog
                    activeDialog.file = request.fileName
                    activeDialog.isVisible = true
                    val selectedFile = activeDialog.file
                    val directory = activeDialog.directory
                    if (selectedFile == null || directory == null) {
                        currentOnResult.value(FileExportResult.Cancelled)
                    } else {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    File(directory, selectedFile).writeText(request.content)
                                }
                                currentCoroutineContext().ensureActive()
                                currentOnResult.value(FileExportResult.Completed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                currentCoroutineContext().ensureActive()
                                currentOnResult.value(FileExportResult.Error())
                            }
                        }
                    }
                } catch (_: Exception) {
                    currentOnResult.value(FileExportResult.Error())
                } finally {
                    dialog?.dispose()
                }
            }
        }
    }
}
