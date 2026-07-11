package com.udnahc.opentasks.ui.util

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFileImportLauncher(
    onResult: (FileImportResult) -> Unit,
): (ImportFileType) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requestedType by remember { mutableStateOf<ImportFileType?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expectedType = requestedType
        requestedType = null
        if (uri == null) {
            onResult(FileImportResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                        ?: uri.lastPathSegment
                        ?: "import.${expectedType?.extension ?: "txt"}"
                    if (expectedType != null && !expectedType.accepts(name)) {
                        error("Select a .${expectedType.extension} file")
                    }
                    val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to open the selected file")
                    ImportedFile(name = name, content = content)
                }
            }.fold(
                onSuccess = { FileImportResult.Selected(it) },
                onFailure = { FileImportResult.Error() },
            )
            onResult(result)
        }
    }
    return remember(launcher) {
        { type ->
            requestedType = type
            launcher.launch(type.mimeTypes.toTypedArray())
        }
    }
}
