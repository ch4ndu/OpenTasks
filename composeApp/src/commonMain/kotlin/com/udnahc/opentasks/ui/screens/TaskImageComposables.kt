package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.AttachmentImageContentMode
import com.udnahc.opentasks.ui.util.LocalAttachmentImage
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.add_from_camera
import opentasks.composeapp.generated.resources.add_from_gallery
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.delete_image_message
import opentasks.composeapp.generated.resources.delete_image_title
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.ic_add
import opentasks.composeapp.generated.resources.ic_attach
import opentasks.composeapp.generated.resources.ic_check_circle
import opentasks.composeapp.generated.resources.ic_close
import opentasks.composeapp.generated.resources.ic_delete
import opentasks.composeapp.generated.resources.ic_info
import opentasks.composeapp.generated.resources.image_attachment
import opentasks.composeapp.generated.resources.image_blocked
import opentasks.composeapp.generated.resources.image_failed
import opentasks.composeapp.generated.resources.image_pending
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskImageEditorStrip(
    existingImages: List<Attachment>,
    pendingImages: List<PickedImage>,
    onAddFromGallery: () -> Unit,
    onAddFromCamera: () -> Unit,
    onRemoveExisting: (Attachment) -> Unit,
    onRemovePending: (PickedImage) -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var viewerImage by remember { mutableStateOf<Attachment?>(null) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacerSmall)) {
            IconButton(onClick = onAddFromGallery) {
                Icon(
                    painter = painterResource(Res.drawable.ic_attach),
                    contentDescription = stringResource(Res.string.add_from_gallery),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAddFromCamera) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.add_from_camera),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (existingImages.isNotEmpty() || pendingImages.isNotEmpty()) {
            Spacer(Modifier.height(dimens.spacerSmall))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacerMedium)) {
                items(existingImages, key = { it.id }) { image ->
                    ExistingImageThumbnail(
                        image = image,
                        onClick = { viewerImage = image },
                        onRemove = { onRemoveExisting(image) },
                    )
                }
                items(pendingImages, key = { it.id }) { image ->
                    PendingImageThumbnail(
                        image = image,
                        onRemove = { onRemovePending(image) },
                    )
                }
            }
        }
    }

    viewerImage?.let { image ->
        TaskImageViewer(
            image = image,
            onDismiss = { viewerImage = null },
            onDelete = {
                onRemoveExisting(image)
                viewerImage = null
            },
        )
    }
}

@Composable
private fun ExistingImageThumbnail(
    image: Attachment,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.touchTargetLarge * 2)
            .clip(RoundedCornerShape(dimens.cornerMedium))
            .clickable(onClick = onClick),
    ) {
        LocalAttachmentImage(
            path = image.thumbnailPath.ifBlank { image.localPath },
            contentDescription = stringResource(Res.string.image_attachment),
            modifier = Modifier.fillMaxSize(),
        )
        AttachmentSyncBadge(
            state = image.syncState,
            modifier = Modifier.align(Alignment.BottomStart).padding(dimens.paddingTiny),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(dimens.touchTargetSmall),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.delete),
                tint = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(dimens.cornerSmall))
                    .padding(dimens.paddingTiny),
            )
        }
    }
}

@Composable
private fun PendingImageThumbnail(
    image: PickedImage,
    onRemove: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.touchTargetLarge * 2)
            .clip(RoundedCornerShape(dimens.cornerMedium))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                dimens.priorityIndicatorBorder,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(dimens.cornerMedium),
            ),
    ) {
        Text(
            text = image.fileName.substringBeforeLast('.').take(8),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center).padding(dimens.paddingTiny),
        )
        AttachmentSyncBadge(
            state = AttachmentSyncState.LOCAL_ONLY,
            modifier = Modifier.align(Alignment.BottomStart).padding(dimens.paddingTiny),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(dimens.touchTargetSmall),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.delete),
                tint = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(dimens.cornerSmall))
                    .padding(dimens.paddingTiny),
            )
        }
    }
}

@Composable
internal fun AttachmentSyncBadge(
    state: AttachmentSyncState?,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens
    val (label, color, icon) = when (state) {
        AttachmentSyncState.SYNCED -> Triple(stringResource(Res.string.done), PrimaryBlue, Res.drawable.ic_check_circle)
        AttachmentSyncState.FAILED -> Triple(stringResource(Res.string.image_failed), MaterialTheme.colorScheme.error, Res.drawable.ic_info)
        AttachmentSyncState.BLOCKED -> Triple(stringResource(Res.string.image_blocked), MaterialTheme.colorScheme.error, Res.drawable.ic_info)
        AttachmentSyncState.NEEDS_DOWNLOAD -> Triple(stringResource(Res.string.image_pending), MaterialTheme.colorScheme.tertiary, Res.drawable.ic_info)
        AttachmentSyncState.LOCAL_ONLY, null -> Triple(stringResource(Res.string.image_pending), MaterialTheme.colorScheme.secondary, Res.drawable.ic_info)
    }
    Surface(
        color = color.copy(alpha = 0.9f),
        shape = RoundedCornerShape(dimens.cornerSmall),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = dimens.paddingTiny, vertical = dimens.paddingTiny),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(dimens.iconTiny),
            )
        }
    }
}

@Composable
private fun TaskImageViewer(
    image: Attachment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                LocalAttachmentImage(
                    path = image.localPath,
                    contentDescription = stringResource(Res.string.image_attachment),
                    contentMode = AttachmentImageContentMode.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .padding(dimens.paddingXLarge),
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(dimens.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacerSmall),
                ) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = stringResource(Res.string.delete),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.cancel),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(Res.string.delete_image_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(Res.string.delete_image_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }) {
                        Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}
