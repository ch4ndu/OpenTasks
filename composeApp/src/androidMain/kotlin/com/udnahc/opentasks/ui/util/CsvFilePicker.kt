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
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.ExternalInputPolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
            val result = try {
                withContext(Dispatchers.IO) {
                    val type = expectedType
                        ?: return@withContext FileImportResult.Error(ExternalInputFailure.UNREADABLE)
                    readImportedFile(context.contentResolver, uri, type)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FileImportResult.Error(ExternalInputFailure.UNREADABLE)
            }
            currentCoroutineContext().ensureActive()
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

private suspend fun readImportedFile(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    expectedType: ImportFileType,
): FileImportResult {
    var name: String? = null
    var size: Long? = null
    try {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            }
        }
    } catch (_: Exception) {
        // Metadata is only an early rejection optimization. The bounded read remains authoritative.
    }

    val fileName = name
        ?: uri.lastPathSegment
        ?: "import.${expectedType.extension}"
    if (!expectedType.accepts(fileName)) {
        return FileImportResult.Error(ExternalInputFailure.INVALID_FILE_TYPE)
    }
    if (size?.let { it > ExternalInputPolicy.MAX_IMPORT_BYTES } == true) {
        return FileImportResult.Error(ExternalInputFailure.TOO_LARGE)
    }

    val input = try {
        resolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    } ?: return FileImportResult.Error(ExternalInputFailure.UNREADABLE)

    return try {
        when (val result = readBoundedUtf8(input, ExternalInputPolicy.MAX_IMPORT_BYTES)) {
            is BoundedText.Success -> FileImportResult.Selected(
                ImportedFile(name = fileName, content = result.content),
            )
            BoundedText.TooLarge -> FileImportResult.Error(ExternalInputFailure.TOO_LARGE)
            BoundedText.InvalidUtf8 -> FileImportResult.Error(ExternalInputFailure.INVALID_UTF8)
            BoundedText.Unreadable -> FileImportResult.Error(ExternalInputFailure.UNREADABLE)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FileImportResult.Error(ExternalInputFailure.UNREADABLE)
    } finally {
        input.close()
    }
}

private sealed interface BoundedText {
    data class Success(val content: String) : BoundedText
    data object TooLarge : BoundedText
    data object InvalidUtf8 : BoundedText
    data object Unreadable : BoundedText
}

private suspend fun readBoundedUtf8(input: InputStream, maxBytes: Int): BoundedText {
    val bytes = ByteArrayOutputStream(minOf(maxBytes + 1, 8192))
    val buffer = ByteArray(minOf(maxBytes + 1, 8192))
    while (bytes.size() <= maxBytes) {
        currentCoroutineContext().ensureActive()
        val requested = minOf(buffer.size, maxBytes + 1 - bytes.size())
        val count = input.read(buffer, 0, requested)
        if (count < 0) break
        if (count == 0) continue
        bytes.write(buffer, 0, count)
    }
    currentCoroutineContext().ensureActive()
    val contentBytes = bytes.toByteArray()
    if (contentBytes.size > maxBytes) return BoundedText.TooLarge
    if (!ExternalInputPolicy.isStrictUtf8(contentBytes)) return BoundedText.InvalidUtf8
    return try {
        val content = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(contentBytes))
            .toString()
        BoundedText.Success(content)
    } catch (_: CharacterCodingException) {
        BoundedText.InvalidUtf8
    }
}
