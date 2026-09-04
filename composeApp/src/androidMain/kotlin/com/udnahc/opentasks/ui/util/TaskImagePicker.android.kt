package com.udnahc.opentasks.ui.util

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.attachment.PickedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@SuppressLint("RememberReturnType")
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImagePicked = rememberUpdatedState(onImagePicked)
    val currentOnError = rememberUpdatedState(onError)
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            val launcherOwnedFile = pendingCameraFile
            pendingCameraFile = null
            launcherOwnedFile?.delete()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val declaredLength = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")
                        ?.use { it.length }
                    if (declaredLength != null &&
                        declaredLength >= 0L &&
                        declaredLength > AttachmentFilePolicy.MAX_SOURCE_BYTES
                    ) {
                        throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBoundedImageBytes()
                    } ?: throw IllegalArgumentException("Attachment image could not be read")
                }
                currentCoroutineContext().ensureActive()
                currentOnImagePicked.value(PickedImage("gallery.jpg", bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                currentOnError.value("image_pick_failed")
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile ?: return@rememberLauncherForActivityResult
        pendingCameraFile = null
        if (!success) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val declaredLength = file.length()
                        if (declaredLength > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                            throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                        }
                        file.inputStream().use { input -> input.readBoundedImageBytes() }
                    }
                    currentCoroutineContext().ensureActive()
                    currentOnImagePicked.value(PickedImage(file.name, bytes))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    currentOnError.value("image_capture_failed")
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    file.delete()
                }
            }
        }
    }
    val actions: TaskImagePickerActions = remember(context, galleryLauncher, cameraLauncher) {
        TaskImagePickerActions(
            pickFromGallery = { galleryLauncher.launch("image/*") },
            captureFromCamera = capture@{
                if (pendingCameraFile != null) return@capture
                var createdFile: File? = null
                try {
                    val directory = File(context.cacheDir, "task_image_captures").apply { mkdirs() }
                    val file = File.createTempFile("task-image-", ".jpg", directory)
                    createdFile = file
                    pendingCameraFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    if (pendingCameraFile === createdFile) {
                        pendingCameraFile = null
                    }
                    createdFile?.delete()
                    currentOnError.value("image_capture_failed")
                }
            },
        )
    }
    return actions
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
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    val decodedBitmap = bitmap
    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap.asImageBitmap(),
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
