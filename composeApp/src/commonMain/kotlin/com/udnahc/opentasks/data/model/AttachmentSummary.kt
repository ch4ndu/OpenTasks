package com.udnahc.opentasks.data.model

data class AttachmentSummary(
    val ownerType: String,
    val ownerId: String,
    val imageCount: Int,
    val firstThumbnailPath: String?,
    val worstSyncState: AttachmentSyncState?,
)

fun AttachmentSummary.ownerKey(): String = "$ownerType:$ownerId"
