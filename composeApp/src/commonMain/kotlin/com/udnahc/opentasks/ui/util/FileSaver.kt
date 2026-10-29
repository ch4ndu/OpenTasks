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

@Composable
expect fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
): (FileExportRequest) -> Unit
