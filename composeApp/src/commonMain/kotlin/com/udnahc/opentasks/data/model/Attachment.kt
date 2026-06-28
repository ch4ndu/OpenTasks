package com.udnahc.opentasks.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.udnahc.opentasks.data.extensions.uuid4

const val ATTACHMENT_OWNER_TASK = "task"
const val ATTACHMENT_KIND_IMAGE = "image"

enum class AttachmentSyncState {
    LOCAL_ONLY,
    SYNCED,
    NEEDS_DOWNLOAD,
    FAILED,
    BLOCKED,
}

@Entity(
    tableName = "attachments",
    indices = [
        Index("ownerType", "ownerId", "kind", "isDeleted", "sortOrder"),
        Index("isSynced"),
        Index("syncState"),
        Index("pbId"),
    ],
)
data class Attachment(
    @PrimaryKey val id: String = uuid4(),
    val ownerType: String,
    val ownerId: String,
    val kind: String,
    val localPath: String = "",
    val thumbnailPath: String = "",
    val remoteFileName: String? = null,
    val mimeType: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sortOrder: Int = 0,
    val syncState: AttachmentSyncState = AttachmentSyncState.LOCAL_ONLY,
    val lastSyncError: String? = null,
    val pbId: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

fun Attachment.withSyncState(state: AttachmentSyncState): Attachment =
    copy(syncState = state, isSynced = state == AttachmentSyncState.SYNCED)
