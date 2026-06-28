package com.udnahc.opentasks.ui.util

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.udnahc.opentasks.data.attachment.PickedImage
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions =
    remember {
        TaskImagePickerActions(
            pickFromGallery = {
                SwingUtilities.invokeLater {
                    runCatching {
                        val dialog = FileDialog(null as java.awt.Frame?, "Select image", FileDialog.LOAD)
                        dialog.filenameFilter = FilenameFilter { _, name ->
                            name.endsWith(".jpg", true) ||
                                    name.endsWith(".jpeg", true) ||
                                    name.endsWith(".png", true) ||
                                    name.endsWith(".webp", true)
                        }
                        dialog.isVisible = true
                        val fileName = dialog.file
                        val directory = dialog.directory
                        if (fileName != null && directory != null) {
                            val file = File(directory, fileName)
                            onImagePicked(PickedImage(file.name, file.readBytes()))
                        }
                    }.onFailure { onError("image_pick_failed") }
                }
            },
            captureFromCamera = { onError("camera_unavailable") },
        )
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
