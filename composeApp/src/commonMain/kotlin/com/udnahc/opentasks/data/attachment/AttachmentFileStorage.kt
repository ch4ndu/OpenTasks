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

interface AttachmentFileStorage {
    suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile
    suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile
    suspend fun readBytes(path: String): ByteArray?
    suspend fun exists(path: String): Boolean
    suspend fun delete(path: String)
    suspend fun clearAll()
}
