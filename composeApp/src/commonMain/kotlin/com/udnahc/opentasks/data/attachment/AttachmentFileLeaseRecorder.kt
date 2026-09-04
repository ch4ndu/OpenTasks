package com.udnahc.opentasks.data.attachment

import com.udnahc.opentasks.data.dao.AttachmentDao
import com.udnahc.opentasks.data.model.AttachmentFileCleanup

class AttachmentFileLeaseRecorder(
    private val dao: AttachmentDao,
) {
    suspend fun lease(paths: Iterable<String>) {
        val entries = paths
            .filter(String::isNotBlank)
            .distinct()
            .map(::AttachmentFileCleanup)
        if (entries.isNotEmpty()) dao.upsertAttachmentFileCleanup(entries)
    }

    suspend fun listedPaths(): List<String> = dao.getAttachmentFileCleanupPaths()

    suspend fun isReferenced(path: String): Boolean = dao.isAttachmentFilePathReferenced(path)

    suspend fun release(path: String) {
        if (path.isNotBlank()) dao.deleteAttachmentFileCleanupPath(path)
    }
}
