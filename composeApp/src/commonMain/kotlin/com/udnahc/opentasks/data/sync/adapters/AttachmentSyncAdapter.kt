package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.dao.AttachmentDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.withSyncState
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.SyncAdapterException
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.data.sync.records.toAttachment
import com.udnahc.opentasks.data.sync.records.toAttachmentRecord
import io.github.agrevster.pocketbaseKotlin.FileUpload
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.lighthousegames.logging.logging

private val log = logging("AttachmentSyncAdapter")

internal class AttachmentFileDownloadException(val statusCode: Int) :
    IllegalStateException("Attachment file download failed with HTTP $statusCode")

class AttachmentSyncAdapter(
    private val dao: AttachmentDao,
    private val taskDao: TaskDao,
    private val fileStorage: AttachmentFileStorage,
) : BaseSyncAdapter<Attachment, AttachmentRecord>() {

    override val collectionName = "attachments"
    override val order = 15

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllOnce()
    override suspend fun getById(localId: String) = dao.findByIdAnyState(localId)
    override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean) =
        dao.markSyncedIfUnchanged(localId, updatedAt, isDeleted)

    override suspend fun updatePbId(localId: String, pbId: String) = dao.updatePbId(localId, pbId)
    override suspend fun markUnsynced(localId: String) = dao.markUnsynced(localId)
    override suspend fun hardDeleteLocalNeverSynced(entity: Attachment) {
        fileStorage.delete(entity.localPath)
        fileStorage.delete(entity.thumbnailPath)
        dao.delete(entity)
    }

    override suspend fun upsert(entity: Attachment) = dao.upsert(entity)
    override fun localId(entity: Attachment) = entity.id
    override fun pbId(entity: Attachment) = entity.pbId
    override fun isDeleted(entity: Attachment) = entity.isDeleted
    override fun isSynced(entity: Attachment) = entity.isSynced
    override fun updatedAt(entity: Attachment) = entity.updatedAt
    override fun recordLocalId(record: AttachmentRecord) = record.localId
    override fun recordIsDeleted(record: AttachmentRecord) = record.isDeleted
    override fun recordUpdatedAt(record: AttachmentRecord) = record.updatedAtUtc
    override fun toRecord(entity: Attachment) = entity.toAttachmentRecord()
    override fun toEntity(record: AttachmentRecord) = record.toAttachment()
    override fun toJsonBody(entity: Attachment) = Json.encodeToString(entity.toAttachmentRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<AttachmentRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<AttachmentRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<AttachmentRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<AttachmentRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): AttachmentRecord? =
        client.records.getList<AttachmentRecord>(
            collectionName,
            1,
            1,
            filterBy = Filter("localId='$localId'")
        ).items.firstOrNull()

    override suspend fun pullAll(client: PocketbaseClient) {
        val remoteRecords = fetchAllRecords(client)
        val localSnapshot = getAllOnce()
        val localById = localSnapshot.associateBy { it.id }
        for (record in remoteRecords) {
            val local = localById[record.localId]
            if (shouldSkipIncomingRecord(record, local)) continue
            val incoming = record.toAttachment()
            if (record.isDeleted || record.file.isNullOrBlank()) {
                upsertRemoteTombstone(incoming, local)
                continue
            }
            if (record.fileSizeBytes > AttachmentFilePolicy.MAX_UPLOAD_BYTES || record.kind != ATTACHMENT_KIND_IMAGE) {
                dao.upsert(incoming.withSyncState(AttachmentSyncState.BLOCKED).copy(lastSyncError = "blocked_policy"))
                continue
            }
            runCatching {
                val recordId = record.id ?: throw SyncAdapterException("Attachment ${record.localId} missing remote id")
                val response = client.httpClient.get {
                    url { path("api", "files", collectionName, recordId, record.file) }
                }
                if (response.status.value !in 200..299) {
                    throw AttachmentFileDownloadException(response.status.value)
                }
                val bytes = response.bodyAsBytes()
                val stored = fileStorage.storeRemoteImage(record.file, bytes)
                dao.upsert(
                    incoming.copy(
                        localPath = stored.localPath,
                        thumbnailPath = stored.thumbnailPath,
                        fileName = stored.fileName,
                        mimeType = stored.mimeType,
                        fileSizeBytes = stored.fileSizeBytes,
                        width = stored.width,
                        height = stored.height,
                    ).withSyncState(AttachmentSyncState.SYNCED)
                )
            }.onFailure {
                log.e(it) { "Failed to download attachment ${record.localId}" }
                upsertRemoteDownloadFailure(incoming, it)
            }
        }
        recoverMissingRemoteRows(remoteRecords, localSnapshot)
    }

    internal suspend fun upsertRemoteDownloadFailure(incoming: Attachment, error: Throwable) {
        val (syncState, errorCode) = when (error) {
            is AttachmentImageDecodeException -> AttachmentSyncState.BLOCKED to "blocked_decode_failed"
            is AttachmentFileDownloadException -> AttachmentSyncState.FAILED to error.downloadErrorCode()
            else -> AttachmentSyncState.FAILED to "download_failed"
        }
        dao.upsert(
            incoming.withSyncState(syncState)
                .copy(lastSyncError = errorCode)
        )
    }

    internal suspend fun shouldSkipIncomingRecord(record: AttachmentRecord, local: Attachment?): Boolean {
        if (local == null) return false
        val localFileMissing = !local.isDeleted &&
                !record.isDeleted &&
                !record.file.isNullOrBlank() &&
                !fileStorage.exists(local.localPath)
        return record.updatedAtUtc <= local.updatedAt && !localFileMissing
    }

    internal suspend fun upsertRemoteTombstone(incoming: Attachment, local: Attachment?) {
        local?.let { deleteLocalFilesFor(it) }
        dao.upsert(incoming.withSyncState(AttachmentSyncState.SYNCED))
    }

    private suspend fun deleteLocalFilesFor(attachment: Attachment) {
        runCatching { fileStorage.delete(attachment.localPath) }
        runCatching { fileStorage.delete(attachment.thumbnailPath) }
    }

    override suspend fun pushAll(client: PocketbaseClient) {
        val failures = mutableListOf<Throwable>()
        for (attachment in getUnsynced()) {
            if (!shouldPush(attachment)) continue
            try {
                pushOne(client, attachment)
            } catch (e: Exception) {
                log.e(e) { "Failed to push attachment ${attachment.id}" }
                failures += e
                dao.markSyncFailed(attachment.id, AttachmentSyncState.FAILED.name, "sync_failed")
            }
        }
        if (failures.isNotEmpty()) {
            throw SyncAdapterException("Failed to push $collectionName", failures.first())
        }
    }

    private suspend fun shouldPush(attachment: Attachment): Boolean {
        if (attachment.isDeleted) return true
        if (attachment.syncState == AttachmentSyncState.BLOCKED ||
            attachment.syncState == AttachmentSyncState.NEEDS_DOWNLOAD ||
            attachment.isRemoteOriginDownloadFailure()
        ) {
            return false
        }
        return true
    }

    private fun Attachment.isRemoteOriginDownloadFailure(): Boolean =
        lastSyncError == "download_failed" || lastSyncError?.startsWith("download_http_") == true

    private fun AttachmentFileDownloadException.downloadErrorCode(): String =
        when (statusCode) {
            in 300..399 -> "download_http_3xx"
            in 400..499 -> "download_http_4xx"
            in 500..599 -> "download_http_5xx"
            else -> "download_http_other"
        }

    private suspend fun pushOne(client: PocketbaseClient, attachment: Attachment) {
        if (attachment.isDeleted && attachment.pbId == null) {
            hardDeleteLocalNeverSynced(attachment)
            return
        }

        val parent = taskDao.findTaskByIdAnyState(attachment.ownerId)
        if (attachment.ownerType == ATTACHMENT_OWNER_TASK && parent?.pbId == null) return

        val body = attachment.toBody()
        val updated = if (attachment.pbId != null) {
            if (attachment.isDeleted) {
                updateByPbIdOrRecover(client, attachment, body, bytes = null, clearFile = true)
            } else {
                val bytes = fileStorage.readBytes(attachment.localPath)
                if (bytes == null) {
                    dao.markSyncFailed(attachment.id, AttachmentSyncState.FAILED.name, "local_file_missing")
                    return
                }
                updateByPbIdOrRecover(client, attachment, body, bytes = bytes, clearFile = false)
            }
        } else {
            val bytes = fileStorage.readBytes(attachment.localPath)
            if (bytes == null) {
                dao.markSyncFailed(attachment.id, AttachmentSyncState.FAILED.name, "local_file_missing")
                return
            }
            createOrRecover(client, attachment, body, bytes = bytes, clearFile = false)
        }

        updated.id?.let { dao.updatePbId(attachment.id, it) }
        dao.updateRemoteFileName(attachment.id, updated.file)
        dao.markSyncedIfUnchanged(attachment.id, attachment.updatedAt, attachment.isDeleted)
    }

    internal suspend fun recoverMissingRemoteRows(
        remoteRecords: List<AttachmentRecord>,
        localSnapshot: List<Attachment>,
    ) {
        val localSyncedActive = localSnapshot.filter { it.isSynced && !it.isDeleted }
        if (localSyncedActive.isEmpty()) return
        if (remoteRecords.isEmpty()) {
            val message =
                "Degraded $collectionName sync: server returned 0 records but ${localSyncedActive.size} local synced exist; skipping missing-row recovery"
            log.w { message }
            throw SyncDegradedException(message)
        }
        if (remoteRecords.size < localSyncedActive.size * MISSING_ROW_RECOVERY_MIN_RATIO) {
            log.w { "Skipping $collectionName missing-row recovery: server returned ${remoteRecords.size} records but ${localSyncedActive.size} local synced exist -- possible partial response" }
            return
        }
        val remoteIds = remoteRecords.map { it.localId }.toSet()
        for (local in localSyncedActive) {
            if (local.id !in remoteIds) {
                log.w { "Recovering missing $collectionName ${local.id}: server row absent, marking unsynced for recreation" }
                dao.markUnsynced(local.id)
            }
        }
    }

    private suspend fun updateByPbIdOrRecover(
        client: PocketbaseClient,
        attachment: Attachment,
        body: Map<String, JsonPrimitive>,
        bytes: ByteArray?,
        clearFile: Boolean,
    ): AttachmentRecord {
        val pbId = attachment.pbId ?: return createOrRecover(client, attachment, body, bytes, clearFile)
        val updated = runCatching { updateRecordWithFile(client, pbId, body, bytes, attachment.fileName, clearFile) }
        if (updated.isSuccess) return updated.getOrThrow()
        val error = updated.exceptionOrNull()
        if (!error.isNotFound()) throw error ?: SyncAdapterException("Failed to update attachment ${attachment.id}")

        log.w { "Stale pbId for $collectionName ${attachment.id}; looking up by localId" }
        val existing = runCatching { findRecordByLocalId(client, attachment.id) }
            .onFailure { log.e(it) { "Failed localId lookup for $collectionName ${attachment.id}" } }
            .getOrNull()
        val recoveredPbId = existing?.id
        if (recoveredPbId != null) {
            dao.updatePbId(attachment.id, recoveredPbId)
            return updateRecordWithFile(client, recoveredPbId, body, bytes, attachment.fileName, clearFile)
        }
        return createRecordWithFile(client, body, bytes, attachment.fileName, clearFile)
    }

    private suspend fun createOrRecover(
        client: PocketbaseClient,
        attachment: Attachment,
        body: Map<String, JsonPrimitive>,
        bytes: ByteArray?,
        clearFile: Boolean,
    ): AttachmentRecord {
        val created = runCatching { createRecordWithFile(client, body, bytes, attachment.fileName, clearFile) }
        if (created.isSuccess) return created.getOrThrow()

        val error = created.exceptionOrNull()
        log.w(error) { "Create failed for $collectionName ${attachment.id}; looking up existing server row by localId" }
        val existing = runCatching { findRecordByLocalId(client, attachment.id) }
            .onFailure { log.e(it) { "Failed localId lookup after create failure for $collectionName ${attachment.id}" } }
            .getOrNull()
        val recoveredPbId = existing?.id ?: throw error ?: SyncAdapterException("Failed to create attachment ${attachment.id}")
        dao.updatePbId(attachment.id, recoveredPbId)
        return updateRecordWithFile(client, recoveredPbId, body, bytes, attachment.fileName, clearFile)
    }

    private suspend fun createRecordWithFile(
        client: PocketbaseClient,
        body: Map<String, JsonPrimitive>,
        bytes: ByteArray?,
        fileName: String,
        clearFile: Boolean,
    ): AttachmentRecord =
        if (clearFile) {
            client.records.create<AttachmentRecord>(collectionName, body.withClearedFile(), emptyList())
        } else {
            val uploadBytes = bytes ?: throw SyncAdapterException("Attachment file bytes missing")
            client.records.create<AttachmentRecord>(
                collectionName,
                body,
                listOf(FileUpload(FILE_FIELD, uploadBytes, fileName))
            )
        }

    private suspend fun updateRecordWithFile(
        client: PocketbaseClient,
        pbId: String,
        body: Map<String, JsonPrimitive>,
        bytes: ByteArray?,
        fileName: String,
        clearFile: Boolean,
    ): AttachmentRecord =
        if (clearFile) {
            client.records.update<AttachmentRecord>(collectionName, pbId, body.withClearedFile(), emptyList())
        } else {
            val uploadBytes = bytes ?: throw SyncAdapterException("Attachment file bytes missing")
            client.records.update<AttachmentRecord>(
                collectionName,
                pbId,
                body,
                listOf(FileUpload(FILE_FIELD, uploadBytes, fileName))
            )
        }

    private fun Attachment.toBody(): Map<String, JsonPrimitive> =
        mapOf(
            "localId" to JsonPrimitive(id),
            "ownerType" to JsonPrimitive(ownerType),
            "ownerId" to JsonPrimitive(ownerId),
            "kind" to JsonPrimitive(kind),
            "mimeType" to JsonPrimitive(mimeType),
            "fileName" to JsonPrimitive(fileName),
            "fileSizeBytes" to JsonPrimitive(fileSizeBytes),
            "width" to JsonPrimitive(width),
            "height" to JsonPrimitive(height),
            "sortOrder" to JsonPrimitive(sortOrder),
            "isDeleted" to JsonPrimitive(isDeleted),
            "localCreatedAt" to JsonPrimitive(createdAt),
            "localUpdatedAt" to JsonPrimitive(updatedAt),
        )

    private fun Map<String, JsonPrimitive>.withClearedFile(): Map<String, JsonPrimitive> =
        this + (FILE_FIELD to JsonPrimitive(""))

    private fun Throwable?.isNotFound(): Boolean =
        this?.message?.contains(": 404") == true

    private companion object {
        const val FILE_FIELD = "file"
        const val MISSING_ROW_RECOVERY_MIN_RATIO = 0.1
    }
}
