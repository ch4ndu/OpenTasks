package com.udnahc.opentasks.ui.util

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.udnahc.opentasks.data.attachment.PickedImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            pendingCameraFile?.delete()
            pendingCameraFile = null
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read image")
                }
            }.onSuccess { bytes ->
                onImagePicked(PickedImage("gallery.jpg", bytes))
            }.onFailure {
                onError("image_pick_failed")
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
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        file.readBytes()
                    } finally {
                        file.delete()
                    }
                }
            }.onSuccess { bytes ->
                onImagePicked(PickedImage(file.name, bytes))
            }.onFailure {
                file.delete()
                onError("image_capture_failed")
            }
        }
    }
    return remember(context, galleryLauncher, cameraLauncher) {
        TaskImagePickerActions(
            pickFromGallery = { galleryLauncher.launch("image/*") },
            captureFromCamera = {
                runCatching {
                    pendingCameraFile?.delete()
                    val directory = File(context.cacheDir, "task_image_captures").apply { mkdirs() }
                    val file = File.createTempFile("task-image-", ".jpg", directory)
                    pendingCameraFile = file
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.onSuccess { uri ->
                    cameraLauncher.launch(uri)
                }.onFailure {
                    pendingCameraFile?.delete()
                    pendingCameraFile = null
                    onError("image_capture_failed")
                }
            },
        )
    }
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
