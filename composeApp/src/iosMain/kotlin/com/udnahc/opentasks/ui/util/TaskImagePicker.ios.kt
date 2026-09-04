package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.attachment.PickedImage
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.fileHandleForReadingFromURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImageView
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIViewContentMode
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

@Composable
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val scope = rememberCoroutineScope()
    val currentOnImagePicked = rememberUpdatedState(onImagePicked)
    val currentOnError = rememberUpdatedState(onError)
    val cameraPickerDelegate = remember(scope) {
        IosCameraImagePickerDelegate { image ->
            scope.launch {
                try {
                    val pickedImage = withContext(Dispatchers.Default) {
                        image?.preprocessForAttachment() ?: throw AttachmentImageDecodeException()
                    }
                    currentCoroutineContext().ensureActive()
                    currentOnImagePicked.value(pickedImage)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    currentOnError.value("image_capture_failed")
                }
            }
        }
    }
    val photoPickerDelegate = remember(scope) {
        IosPhotoPickerDelegate { result ->
            scope.launch {
                try {
                    val pickedImage = result.loadBoundedPickedImage()
                    currentCoroutineContext().ensureActive()
                    currentOnImagePicked.value(pickedImage)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    currentOnError.value("image_pick_failed")
                }
            }
        }
    }
    return remember(cameraPickerDelegate, photoPickerDelegate) {
        TaskImagePickerActions(
            pickFromGallery = {
                presentPhotoPicker(
                    delegate = photoPickerDelegate,
                    onUnavailable = { currentOnError.value("gallery_unavailable") },
                )
            },
            captureFromCamera = {
                presentCameraPicker(
                    delegate = cameraPickerDelegate,
                    onUnavailable = { currentOnError.value("camera_unavailable") },
                )
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
    UIKitView(
        factory = {
            UIImageView().apply {
                this.contentMode = contentMode.toUIViewContentMode()
                clipsToBounds = true
                image = UIImage.imageWithContentsOfFile(path)
                isAccessibilityElement = contentDescription != null
                accessibilityLabel = contentDescription
            }
        },
        update = { imageView ->
            imageView.contentMode = contentMode.toUIViewContentMode()
            imageView.image = UIImage.imageWithContentsOfFile(path)
            imageView.isAccessibilityElement = contentDescription != null
            imageView.accessibilityLabel = contentDescription
        },
        onRelease = { imageView ->
            imageView.image = null
            imageView.accessibilityLabel = null
            imageView.isAccessibilityElement = false
        },
        modifier = modifier,
    )
}

private fun AttachmentImageContentMode.toUIViewContentMode(): UIViewContentMode =
    when (this) {
        AttachmentImageContentMode.Crop -> UIViewContentMode.UIViewContentModeScaleAspectFill
        AttachmentImageContentMode.Fit -> UIViewContentMode.UIViewContentModeScaleAspectFit
    }

private fun presentCameraPicker(
    delegate: IosCameraImagePickerDelegate,
    onUnavailable: () -> Unit,
) {
    val sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    if (!UIImagePickerController.isSourceTypeAvailable(sourceType)) {
        onUnavailable()
        return
    }
    val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: UIApplication.sharedApplication.windows.firstOrNull()?.let { it as? platform.UIKit.UIWindow }?.rootViewController
        ?: run {
            onUnavailable()
            return
        }
    try {
        val picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = delegate
        rootController.presentViewController(picker, animated = true, completion = null)
    } catch (_: Exception) {
        onUnavailable()
    }
}

private fun presentPhotoPicker(
    delegate: IosPhotoPickerDelegate,
    onUnavailable: () -> Unit,
) {
    val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: UIApplication.sharedApplication.windows.firstOrNull()?.let { it as? platform.UIKit.UIWindow }?.rootViewController
        ?: run {
            onUnavailable()
            return
        }
    try {
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1L
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration)
        picker.delegate = delegate
        rootController.presentViewController(picker, animated = true, completion = null)
    } catch (_: Exception) {
        onUnavailable()
    }
}

private class IosCameraImagePickerDelegate(
    private val onImageSelected: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage] as? UIImage
            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        onImageSelected(image)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private class IosPhotoPickerDelegate(
    private val onImageSelected: (PHPickerResult) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        onImageSelected(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun PHPickerResult.loadBoundedPickedImage(): PickedImage =
    suspendCancellableCoroutine { continuation ->
        val provider: NSItemProvider = itemProvider
        val progress = provider.loadFileRepresentationForTypeIdentifier(
            typeIdentifier = UTTypeImage.identifier,
        ) { url: NSURL?, error ->
            if (!continuation.isActive) return@loadFileRepresentationForTypeIdentifier
            if (error != null || url == null) {
                continuation.resumeWithException(AttachmentImageDecodeException())
                return@loadFileRepresentationForTypeIdentifier
            }
            try {
                val handle = NSFileHandle.fileHandleForReadingFromURL(url, error = null)
                    ?: throw AttachmentImageDecodeException()
                val data = try {
                    handle.readDataUpToLength(
                        length = (AttachmentFilePolicy.MAX_SOURCE_BYTES + 1L).toULong(),
                        error = null,
                    ) ?: throw AttachmentImageDecodeException()
                } finally {
                    handle.closeAndReturnError(null)
                }
                if (data.length.toLong() > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
                    throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
                }
                val bytes = data.toByteArray() ?: throw AttachmentImageDecodeException()
                val fileName = provider.suggestedName ?: url.lastPathComponent ?: "ios-image"
                if (continuation.isActive) continuation.resume(PickedImage(fileName, bytes))
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
        continuation.invokeOnCancellation { progress.cancel() }
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun UIImage.preprocessForAttachment(): PickedImage {
    currentCoroutineContext().ensureActive()
    val nativeImage = CGImage ?: throw AttachmentImageDecodeException()
    val sourceWidth = CGImageGetWidth(nativeImage).toLong()
    val sourceHeight = CGImageGetHeight(nativeImage).toLong()
    if (sourceWidth !in 1..Int.MAX_VALUE.toLong() ||
        sourceHeight !in 1..Int.MAX_VALUE.toLong() ||
        !AttachmentFilePolicy.acceptsSourceDimensions(sourceWidth.toInt(), sourceHeight.toInt())
    ) {
        throw AttachmentImageDecodeException()
    }

    val (displayWidth, displayHeight) = size.useContents { width to height }
    val displayLongEdge = maxOf(displayWidth, displayHeight)
    if (!displayWidth.isFinite() ||
        !displayHeight.isFinite() ||
        displayWidth <= 0.0 ||
        displayHeight <= 0.0 ||
        !displayLongEdge.isFinite()
    ) {
        throw AttachmentImageDecodeException()
    }
    val scale = if (displayLongEdge > AttachmentFilePolicy.MAX_LONG_EDGE.toDouble()) {
        AttachmentFilePolicy.MAX_LONG_EDGE.toDouble() / displayLongEdge
    } else {
        1.0
    }
    val targetWidth = (displayWidth * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (displayHeight * scale).roundToInt().coerceAtLeast(1)
    currentCoroutineContext().ensureActive()
    UIGraphicsBeginImageContextWithOptions(
        platform.CoreGraphics.CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()),
        false,
        1.0,
    )
    val renderedImage = try {
        drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
        UIGraphicsGetImageFromCurrentImageContext() ?: throw AttachmentImageDecodeException()
    } finally {
        UIGraphicsEndImageContext()
    }
    currentCoroutineContext().ensureActive()
    val data = UIImageJPEGRepresentation(renderedImage, 0.8)
        ?: throw AttachmentImageDecodeException()
    val length = data.length.toLong()
    if (length > AttachmentFilePolicy.MAX_SOURCE_BYTES) {
        throw AttachmentFileTooLargeException(AttachmentFilePolicy.MAX_SOURCE_BYTES)
    }
    currentCoroutineContext().ensureActive()
    val bytes = data.toByteArray() ?: throw AttachmentImageDecodeException()
    currentCoroutineContext().ensureActive()
    return PickedImage("ios-image.jpg", bytes)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val rawBytes = bytes ?: return null
    return rawBytes.reinterpret<ByteVar>().readBytes(length.toInt())
}
