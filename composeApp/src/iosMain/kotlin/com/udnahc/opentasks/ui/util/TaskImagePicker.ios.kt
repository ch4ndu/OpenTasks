package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.udnahc.opentasks.data.attachment.PickedImage
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
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
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject

@Composable
actual fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions {
    val pickerDelegate = remember {
        IosImagePickerDelegate(onImagePicked = onImagePicked)
    }
    return remember(pickerDelegate) {
        TaskImagePickerActions(
            pickFromGallery = {
                presentImagePicker(
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary,
                    delegate = pickerDelegate,
                    onUnavailable = { onError("gallery_unavailable") },
                )
            },
            captureFromCamera = {
                presentImagePicker(
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
                    delegate = pickerDelegate,
                    onUnavailable = { onError("camera_unavailable") },
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
            }
        },
        update = { imageView ->
            imageView.contentMode = contentMode.toUIViewContentMode()
            imageView.image = UIImage.imageWithContentsOfFile(path)
        },
        modifier = modifier,
    )
}

private fun AttachmentImageContentMode.toUIViewContentMode(): UIViewContentMode =
    when (this) {
        AttachmentImageContentMode.Crop -> UIViewContentMode.UIViewContentModeScaleAspectFill
        AttachmentImageContentMode.Fit -> UIViewContentMode.UIViewContentModeScaleAspectFit
    }

private fun presentImagePicker(
    sourceType: UIImagePickerControllerSourceType,
    delegate: IosImagePickerDelegate,
    onUnavailable: () -> Unit,
) {
    if (!UIImagePickerController.isSourceTypeAvailable(sourceType)) {
        onUnavailable()
        return
    }
    val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: UIApplication.sharedApplication.windows.firstOrNull()?.let { it as? platform.UIKit.UIWindow }?.rootViewController
        ?: return
    val picker = UIImagePickerController()
    picker.sourceType = sourceType
    picker.delegate = delegate
    rootController.presentViewController(picker, animated = true, completion = null)
}

private class IosImagePickerDelegate(
    private val onImagePicked: (PickedImage) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage] as? UIImage
            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 0.9) }
        val bytes = data?.toByteArray()
        if (bytes != null) {
            onImagePicked(PickedImage("ios-image.jpg", bytes))
        }
        picker.dismissViewControllerAnimated(true, completion = null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val rawBytes = bytes ?: return null
    return rawBytes.reinterpret<ByteVar>().readBytes(length.toInt())
}
