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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.attachment.PickedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@SuppressLint("RememberReturnType")
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val context = LocalContext.current.applicationContext
    val currentOnImagePicked = rememberUpdatedState(onImagePicked)
    val currentOnError = rememberUpdatedState(onError)
    val state = rememberAndroidPickerState(::AndroidImagePickerState, AndroidImagePickerState::abandon) { it.abandoned }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { state.result }.filterNotNull().collect { result ->
                if (state.consume(result)) {
                    when (result) {
                        is ImagePickerResult.Picked -> currentOnImagePicked.value(result.image)
                        is ImagePickerResult.Error -> currentOnError.value(result.code)
                    }
                }
            }
        }
    }
    val galleryLauncher = key(state) {
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            state.receiveGallery(context.contentResolver, uri)
        }
    }
    val cameraLauncher = key(state) {
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            state.receiveCamera(success)
        }
    }
    return remember(context, state, galleryLauncher, cameraLauncher) {
        TaskImagePickerActions(
            pickFromGallery = {
                state.launchGallery { galleryLauncher.launch("image/*") }
            },
            captureFromCamera = {
                state.launchCamera(context) { cameraLauncher.launch(it) }
            },
        )
    }
}

private sealed interface ImagePickerResult {
    data class Picked(val image: PickedImage) : ImagePickerResult
    data class Error(val code: String) : ImagePickerResult
}

/** The file and any bounded read survive configuration recreation, but not process death. */
private class AndroidImagePickerState : ViewModel() {
    private var pendingCameraFile: File? = null
    private var galleryPending = false
    private var readJob: Job? = null
    var abandoned = false
        private set
    var result by mutableStateOf<ImagePickerResult?>(null)
        private set

    private fun isBusy(): Boolean =
        abandoned || pendingCameraFile != null || galleryPending || readJob != null || result != null

    fun launchGallery(launch: () -> Unit) {
        if (isBusy()) return
        galleryPending = true
        try {
            launch()
        } catch (_: Exception) {
            galleryPending = false
            result = ImagePickerResult.Error("image_pick_failed")
        }
    }

    fun receiveGallery(resolver: android.content.ContentResolver, uri: android.net.Uri?) {
        if (!galleryPending || abandoned) return
        galleryPending = false
        if (uri == null) return
        readJob = viewModelScope.launch {
            val picked = try {
                val bytes = withContext(Dispatchers.IO) {
                    val declaredLength = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    if (declaredLength != null && declaredLength > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                        throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                    }
                    resolver.openInputStream(uri)?.use { it.readBoundedImageBytes() }
                        ?: throw IllegalArgumentException("Attachment image could not be read")
                }
                ImagePickerResult.Picked(PickedImage("gallery.jpg", bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImagePickerResult.Error("image_pick_failed")
            }
            currentCoroutineContext().ensureActive()
            readJob = null
            if (!abandoned) result = picked
        }
    }

    fun launchCamera(context: android.content.Context, launch: (android.net.Uri) -> Unit) {
        if (isBusy()) return
        try {
            val directory = File(context.cacheDir, "task_image_captures").apply { mkdirs() }
            val file = File.createTempFile("task-image-", ".jpg", directory)
            pendingCameraFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launch(uri)
        } catch (_: Exception) {
            pendingCameraFile?.delete()
            pendingCameraFile = null
            result = ImagePickerResult.Error("image_capture_failed")
        }
    }

    fun receiveCamera(success: Boolean) {
        val file = pendingCameraFile ?: return
        if (abandoned) return
        pendingCameraFile = null
        if (!success) {
            file.delete()
            return
        }
        // Start immediately so this coroutine owns cleanup even if the editor exits before IO runs.
        readJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val picked = try {
                val bytes = withContext(Dispatchers.IO) {
                    if (file.length() > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                        throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                    }
                    file.inputStream().use { it.readBoundedImageBytes() }
                }
                ImagePickerResult.Picked(PickedImage(file.name, bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImagePickerResult.Error("image_capture_failed")
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            }
            currentCoroutineContext().ensureActive()
            readJob = null
            if (!abandoned) result = picked
        }
    }

    fun consume(expected: ImagePickerResult): Boolean {
        if (abandoned || result !== expected) return false
        result = null
        return true
    }

    fun abandon() {
        abandoned = true
        pendingCameraFile?.delete()
        pendingCameraFile = null
        galleryPending = false
        result = null
        readJob?.cancel()
        readJob = null
    }

    override fun onCleared() = abandon()
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
