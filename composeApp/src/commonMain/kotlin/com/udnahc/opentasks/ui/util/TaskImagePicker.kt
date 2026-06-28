package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.data.attachment.PickedImage

class TaskImagePickerActions(
    val pickFromGallery: () -> Unit,
    val captureFromCamera: () -> Unit,
)

enum class AttachmentImageContentMode {
    Crop,
    Fit,
}

@Composable
expect fun rememberTaskImagePickerActions(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): TaskImagePickerActions

@Composable
expect fun LocalAttachmentImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentMode: AttachmentImageContentMode = AttachmentImageContentMode.Crop,
)
