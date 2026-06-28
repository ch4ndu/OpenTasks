package com.udnahc.opentasks.data.sync.records

import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AttachmentRecord(
    val localId: String = "",
    val ownerType: String = "",
    val ownerId: String = "",
    val kind: String = "",
    val file: String? = null,
    val mimeType: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    @SerialName("localCreatedAt") val createdAtUtc: Long = 0L,
    @SerialName("localUpdatedAt") val updatedAtUtc: Long = 0L,
) : BaseModel()

fun Attachment.toAttachmentRecord(): AttachmentRecord = AttachmentRecord(
    localId = id,
    ownerType = ownerType,
    ownerId = ownerId,
    kind = kind,
    file = remoteFileName,
    mimeType = mimeType,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    width = width,
    height = height,
    sortOrder = sortOrder,
    isDeleted = isDeleted,
    createdAtUtc = createdAt,
    updatedAtUtc = updatedAt,
)

fun AttachmentRecord.toAttachment(): Attachment = Attachment(
    id = localId,
    ownerType = ownerType,
    ownerId = ownerId,
    kind = kind,
    remoteFileName = file,
    mimeType = mimeType,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    width = width,
    height = height,
    sortOrder = sortOrder,
    syncState = if (file.isNullOrBlank() || isDeleted) AttachmentSyncState.SYNCED else AttachmentSyncState.NEEDS_DOWNLOAD,
    isSynced = file.isNullOrBlank() || isDeleted,
    isDeleted = isDeleted,
    pbId = id,
    createdAt = createdAtUtc,
    updatedAt = updatedAtUtc,
)
