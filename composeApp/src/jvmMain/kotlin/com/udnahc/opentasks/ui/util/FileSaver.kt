package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    onAbandoned: () -> Unit,
): FileExportLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnAbandoned = rememberUpdatedState(onAbandoned)
    val scope = rememberCoroutineScope()
    val launcher = remember {
        object : FileExportLauncher {
            override var isBusy by mutableStateOf(false)
                private set
            @Volatile
            private var disposed = false
            private var activeDialog: FileDialog? = null
            @Volatile
            private var pending: FileExportRequest? = null

            @Synchronized
            override fun launch(request: FileExportRequest): Boolean {
                if (disposed || isBusy) return false
                isBusy = true
                pending = request
                SwingUtilities.invokeLater {
                    if (disposed || pending !== request) return@invokeLater
                    var dialog: FileDialog? = null
                    try {
                        val picker = FileDialog(
                            null as java.awt.Frame?,
                            "Save export file",
                            FileDialog.SAVE,
                        )
                        dialog = picker
                        activeDialog = picker
                        picker.file = request.fileName
                        picker.isVisible = true
                        if (disposed || pending !== request) return@invokeLater
                        val selectedFile = picker.file
                        val directory = picker.directory
                        if (selectedFile == null || directory == null) {
                            finish(request, FileExportResult.Cancelled)
                        } else {
                            scope.launch write@{
                                if (disposed || pending !== request) return@write
                                try {
                                    withContext(Dispatchers.IO) {
                                        File(directory, selectedFile).writeText(request.content)
                                    }
                                    currentCoroutineContext().ensureActive()
                                    finish(request, FileExportResult.Completed(request.itemCount))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    finish(request, FileExportResult.Error())
                                }
                            }
                        }
                    } catch (_: Exception) {
                        finish(request, FileExportResult.Error())
                    } finally {
                        if (activeDialog === dialog) activeDialog = null
                        dialog?.dispose()
                    }
                }
                return true
            }

            @Synchronized
            private fun finish(request: FileExportRequest, result: FileExportResult) {
                if (disposed || pending !== request) return
                pending = null
                isBusy = false
                currentOnResult.value(result)
            }

            @Synchronized
            fun dispose() {
                disposed = true
                pending = null
                isBusy = false
                // The queued presentation also checks disposed; all AWT dialog access stays on EDT.
                SwingUtilities.invokeLater { activeDialog?.dispose() }
            }
        }
    }
    DisposableEffect(launcher) {
        onDispose {
            launcher.dispose()
            currentOnAbandoned.value()
        }
    }
    return launcher
}
