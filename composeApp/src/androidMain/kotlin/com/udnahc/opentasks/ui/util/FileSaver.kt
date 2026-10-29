package com.udnahc.opentasks.ui.util

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
): (FileExportRequest) -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val currentOnResult = rememberUpdatedState(onResult)
    val state = rememberAndroidPickerState(::AndroidFileExportState, AndroidFileExportState::abandon) { it.abandoned }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { state.result }.filterNotNull().collect { result ->
                if (state.consume(result)) currentOnResult.value(result)
            }
        }
    }
    val csvLauncher = key(state) {
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            state.receive(resolver, uri, calendar = false)
        }
    }
    val icsLauncher = key(state) {
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
            state.receive(resolver, uri, calendar = true)
        }
    }
    return remember(state, csvLauncher, icsLauncher) {
        { request ->
            if (state.begin(request)) {
                try {
                    if (request.mimeType == "text/calendar") icsLauncher.launch(request.fileName)
                    else csvLauncher.launch(request.fileName)
                } catch (_: Exception) {
                    state.launchFailed()
                }
            }
        }
    }
}

/** Configuration retention only: export contents intentionally never enter saved instance state. */
private class AndroidFileExportState : ViewModel() {
    private var pending: FileExportRequest? = null
    private var writeJob: Job? = null
    var abandoned = false
        private set
    var result by mutableStateOf<FileExportResult?>(null)
        private set

    fun begin(request: FileExportRequest): Boolean {
        if (abandoned || pending != null || writeJob != null || result != null) return false
        pending = request
        return true
    }

    fun launchFailed() {
        if (pending == null || abandoned) return
        pending = null
        result = FileExportResult.Error()
    }

    fun receive(resolver: ContentResolver, uri: Uri?, calendar: Boolean) {
        val request = pending ?: return
        if ((request.mimeType == "text/calendar") != calendar || abandoned) return
        pending = null // Claim this request once before starting any asynchronous work.
        if (uri == null) {
            result = FileExportResult.Cancelled
            return
        }
        writeJob = viewModelScope.launch {
            val completed = try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                        writer.write(request.content)
                        writer.flush()
                    } ?: error("Unable to open the selected destination")
                }
                FileExportResult.Completed(request.itemCount)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FileExportResult.Error()
            }
            currentCoroutineContext().ensureActive()
            writeJob = null
            if (!abandoned) result = completed
        }
    }

    fun consume(expected: FileExportResult): Boolean {
        if (abandoned || result !== expected) return false
        result = null
        return true
    }

    fun abandon() {
        abandoned = true
        pending = null
        result = null
        writeJob?.cancel()
        writeJob = null
    }

    override fun onCleared() = abandon()
}
