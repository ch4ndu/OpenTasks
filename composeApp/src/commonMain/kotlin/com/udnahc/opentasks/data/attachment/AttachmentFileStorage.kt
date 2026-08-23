package com.udnahc.opentasks.data.attachment

import com.udnahc.opentasks.data.extensions.uuid4

data class PickedImage(
    val fileName: String,
    val bytes: ByteArray,
    val id: String = uuid4(),
)

data class StoredAttachmentFile(
    val localPath: String,
    val thumbnailPath: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
)

class AttachmentImageDecodeException : IllegalArgumentException("Attachment image decode failed")

class AttachmentFileTooLargeException(
    val maxBytes: Long,
) : IllegalArgumentException("Attachment file exceeds the configured byte limit")

interface AttachmentFileStorage {
    suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile
    suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile
    /** Reads no more than [maxBytes], throwing a typed failure when one additional byte exists. */
    suspend fun readBytes(
        path: String,
        maxBytes: Long = AttachmentFilePolicy.MAX_UPLOAD_BYTES,
    ): ByteArray?
    suspend fun exists(path: String): Boolean
    suspend fun delete(path: String)
    suspend fun clearAll()
}
