package com.udnahc.opentasks.testutil

import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.attachment.PickedImage
import com.udnahc.opentasks.data.attachment.StoredAttachmentFile

class FakeAttachmentFileStorage : AttachmentFileStorage {
    private val files = mutableMapOf<String, ByteArray>()
    var clearAllCalled = false
    var storePickedImageError: Throwable? = null
    var storeRemoteImageError: Throwable? = null

    override suspend fun storePickedImage(image: PickedImage): StoredAttachmentFile =
        storePickedImageError?.let { throw it } ?:
        store(image.fileName, image.bytes)

    override suspend fun storeRemoteImage(fileName: String, bytes: ByteArray): StoredAttachmentFile =
        storeRemoteImageError?.let { throw it } ?:
        store(fileName, bytes)

    override suspend fun readBytes(path: String, maxBytes: Long): ByteArray? = files[path]?.also { bytes ->
        if (bytes.size.toLong() > maxBytes) throw AttachmentFileTooLargeException(maxBytes)
    }

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun delete(path: String) {
        files.remove(path)
    }

    override suspend fun clearAll() {
        clearAllCalled = true
        files.clear()
    }

    fun addFile(path: String, bytes: ByteArray = byteArrayOf(1)) {
        files[path] = bytes
    }

    private fun store(fileName: String, bytes: ByteArray): StoredAttachmentFile {
        val localPath = "/tmp/$fileName"
        val thumbnailPath = "/tmp/thumb_$fileName"
        files[localPath] = bytes
        files[thumbnailPath] = bytes
        return StoredAttachmentFile(
            localPath = localPath,
            thumbnailPath = thumbnailPath,
            fileName = fileName,
            mimeType = "image/jpeg",
            fileSizeBytes = bytes.size.toLong(),
            width = 100,
            height = 100,
        )
    }
}
