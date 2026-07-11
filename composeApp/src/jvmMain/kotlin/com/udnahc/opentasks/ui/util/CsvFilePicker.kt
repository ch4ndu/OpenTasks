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
import java.io.FilenameFilter
import javax.swing.SwingUtilities

@Composable
actual fun rememberFileImportLauncher(
    onResult: (FileImportResult) -> Unit,
): (ImportFileType) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember {
        { type ->
            SwingUtilities.invokeLater {
                runCatching {
                    val dialog = FileDialog(
                        null as java.awt.Frame?,
                        "Select .${type.extension} file",
                        FileDialog.LOAD,
                    )
                    dialog.filenameFilter = FilenameFilter { _, name -> type.accepts(name) }
                    dialog.isVisible = true
                    val fileName = dialog.file
                    val directory = dialog.directory
                    if (fileName == null || directory == null) {
                        currentOnResult.value(FileImportResult.Cancelled)
                    } else {
                        val file = File(directory, fileName)
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    check(file.isFile)
                                    check(type.accepts(file.name))
                                    ImportedFile(file.name, file.readText())
                                }
                            }.fold(
                                onSuccess = { FileImportResult.Selected(it) },
                                onFailure = { FileImportResult.Error() },
                            )
                            currentOnResult.value(result)
                        }
                    }
                }.onFailure { currentOnResult.value(FileImportResult.Error()) }
            }
        }
    }
}
