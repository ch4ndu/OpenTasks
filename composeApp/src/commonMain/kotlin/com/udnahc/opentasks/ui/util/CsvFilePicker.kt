package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ExternalInputFailure

enum class ImportFileType(
    val extension: String,
    val mimeTypes: List<String>,
) {
    CSV(
        extension = "csv",
        mimeTypes = listOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
    ),
    ICS(
        extension = "ics",
        mimeTypes = listOf("text/calendar", "application/ics", "text/plain"),
    ),
    ;

    fun accepts(fileName: String): Boolean =
        fileName.substringAfterLast('.', missingDelimiterValue = "")
            .equals(extension, ignoreCase = true)
}

data class ImportedFile(
    val name: String,
    val content: String,
)

sealed interface FileImportResult {
    data class Selected(val file: ImportedFile) : FileImportResult
    data object Cancelled : FileImportResult
    data class Error(
        val reason: ExternalInputFailure = ExternalInputFailure.UNREADABLE,
        val detail: String? = null,
    ) : FileImportResult
}

@Composable
expect fun rememberFileImportLauncher(
    onResult: (FileImportResult) -> Unit,
): (ImportFileType) -> Unit
