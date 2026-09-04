package com.udnahc.opentasks.ui.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
): (FileExportRequest) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    var pendingCsv by remember { mutableStateOf<FileExportRequest?>(null) }
    var pendingIcs by remember { mutableStateOf<FileExportRequest?>(null) }

    fun writePending(request: FileExportRequest?, uri: android.net.Uri?) {
        if (uri == null) {
            currentOnResult.value(FileExportResult.Cancelled)
            return
        }
        if (request == null) {
            currentOnResult.value(FileExportResult.Error())
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                        writer.write(request.content)
                        writer.flush()
                    } ?: error("Unable to open the selected destination")
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

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val request = pendingCsv
        pendingCsv = null
        writePending(request, uri)
    }
    val icsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val request = pendingIcs
        pendingIcs = null
        writePending(request, uri)
    }

    return remember(csvLauncher, icsLauncher) {
        { request ->
            when (request.mimeType) {
                "text/calendar" -> {
                    pendingIcs = request
                    icsLauncher.launch(request.fileName)
                }

                else -> {
                    pendingCsv = request
                    csvLauncher.launch(request.fileName)
                }
            }
        }
    }
}
