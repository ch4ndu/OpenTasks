package com.udnahc.opentasks.ui.util

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.attachment.PickedImage
import java.io.ByteArrayOutputStream
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter
import java.io.InputStream
import javax.swing.SwingUtilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val scope = rememberCoroutineScope()
    val currentOnImagePicked = rememberUpdatedState(onImagePicked)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        TaskImagePickerActions(
            pickFromGallery = {
                SwingUtilities.invokeLater {
                    var dialog: FileDialog? = null
                    try {
                        val activeDialog = FileDialog(
                            null as java.awt.Frame?,
                            "Select image",
                            FileDialog.LOAD,
                        )
                        dialog = activeDialog
                        activeDialog.filenameFilter = FilenameFilter { _, name ->
                            name.endsWith(".jpg", true) ||
                                    name.endsWith(".jpeg", true) ||
                                    name.endsWith(".png", true) ||
                                    name.endsWith(".webp", true)
                        }
                        activeDialog.isVisible = true
                        val fileName = activeDialog.file
                        val directory = activeDialog.directory
                        if (fileName != null && directory != null) {
                            val file = File(directory, fileName)
                            scope.launch {
                                try {
                                    val bytes = withContext(Dispatchers.IO) {
                                        if (file.length() > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                                            throw AttachmentFileTooLargeException(
                                                AttachmentFilePolicy.MAX_SOURCE_BYTES,
                                            )
                                        }
                                        file.inputStream().use { input -> input.readBoundedImageBytes() }
                                    }
                                    currentCoroutineContext().ensureActive()
                                    currentOnImagePicked.value(PickedImage(file.name, bytes))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    currentOnError.value("image_pick_failed")
                                }
                            }
                        }
                    } catch (_: Exception) {
                        currentOnError.value("image_pick_failed")
                    } finally {
                        dialog?.dispose()
                    }
                }
            },
            captureFromCamera = { currentOnError.value("camera_unavailable") },
        )
    }
}

private suspend fun InputStream.readBoundedImageBytes(): ByteArray {
    val maxBytes = AttachmentFilePolicy.MAX_SOURCE_BYTES
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (total <= maxBytes) {
        currentCoroutineContext().ensureActive()
        val remaining = (maxBytes + 1L - total).coerceAtMost(buffer.size.toLong()).toInt()
        val read = read(buffer, 0, remaining)
        if (read < 0) break
        if (read == 0) {
            val byte = read()
            if (byte < 0) break
            total += 1L
            if (total > maxBytes) throw AttachmentFileTooLargeException(maxBytes)
            output.write(byte)
            continue
        }
        total += read
        if (total > maxBytes) throw AttachmentFileTooLargeException(maxBytes)
        output.write(buffer, 0, read)
    }
    currentCoroutineContext().ensureActive()
    return output.toByteArray()
}

@Composable
actual fun LocalAttachmentImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier,
    contentMode: AttachmentImageContentMode,
) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                File(path).takeIf { it.isFile }
                    ?.readBytes()
                    ?.decodeToImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = imageBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = when (contentMode) {
                AttachmentImageContentMode.Crop -> ContentScale.Crop
                AttachmentImageContentMode.Fit -> ContentScale.Fit
            },
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
