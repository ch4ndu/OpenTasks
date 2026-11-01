package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable

data class FileExportRequest(
    val fileName: String,
    val content: String,
    val mimeType: String,
    val itemCount: Int? = null,
)

sealed interface FileExportResult {
    data class Completed(val itemCount: Int? = null) : FileExportResult
    data object Cancelled : FileExportResult
    data class Error(val detail: String? = null) : FileExportResult
}

/** Native operation ownership, including an awaiting terminal result, survives its caller where supported. */
interface FileExportLauncher {
    /** Compose-observable ownership from native acceptance through terminal-result delivery. */
    val isBusy: Boolean

    /** False means no request was accepted and no terminal result will be delivered for this request. */
    fun launch(request: FileExportRequest): Boolean
}

@Composable
expect fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
    /** Called after native retirement on nonconfiguration disposal, even before native admission. */
    onAbandoned: () -> Unit,
): FileExportLauncher
