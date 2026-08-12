package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.ExternalInputPolicy
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
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
                            val result = try {
                                withContext(Dispatchers.IO) {
                                    readImportedFile(file, type)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                FileImportResult.Error(ExternalInputFailure.UNREADABLE)
                            }
                            currentCoroutineContext().ensureActive()
                            currentOnResult.value(result)
                        }
                    }
                }.onFailure { currentOnResult.value(FileImportResult.Error()) }
            }
        }
    }
}

private suspend fun readImportedFile(file: File, type: ImportFileType): FileImportResult {
    if (!file.isFile || !type.accepts(file.name)) {
        return FileImportResult.Error(ExternalInputFailure.INVALID_FILE_TYPE)
    }
    if (file.length() > ExternalInputPolicy.MAX_IMPORT_BYTES) {
        return FileImportResult.Error(ExternalInputFailure.TOO_LARGE)
    }
    return try {
        file.inputStream().use { input ->
            when (val result = readBoundedUtf8(input, ExternalInputPolicy.MAX_IMPORT_BYTES)) {
                is BoundedText.Success -> FileImportResult.Selected(
                    ImportedFile(name = file.name, content = result.content),
                )
                BoundedText.TooLarge -> FileImportResult.Error(ExternalInputFailure.TOO_LARGE)
                BoundedText.InvalidUtf8 -> FileImportResult.Error(ExternalInputFailure.INVALID_UTF8)
                BoundedText.Unreadable -> FileImportResult.Error(ExternalInputFailure.UNREADABLE)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FileImportResult.Error(ExternalInputFailure.UNREADABLE)
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
