package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.attachment.StoredAttachmentFile
import com.udnahc.opentasks.data.dao.AttachmentDao
import com.udnahc.opentasks.data.dao.AttachmentDownloadInstallResult
import com.udnahc.opentasks.data.dao.AttachmentTombstoneMergeResult
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.model.ATTACHMENT_KIND_IMAGE
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.withSyncState
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.AuthoritativeLocalSeedSourceException
import com.udnahc.opentasks.data.sync.AuthoritativeSeedConflictException
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import com.udnahc.opentasks.data.sync.PocketBaseRecordGateway
import com.udnahc.opentasks.data.sync.SyncAdapterException
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.data.sync.records.toAttachment
import com.udnahc.opentasks.data.sync.records.toAttachmentRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("AttachmentSyncAdapter")

internal class AttachmentFileDownloadException(val statusCode: Int) :
    IllegalStateException("Attachment file download failed with HTTP $statusCode")

open class AttachmentSyncAdapter(
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
        dao.delete(entity)
        deleteFileBestEffort(entity.localPath)
        deleteFileBestEffort(entity.thumbnailPath)
    }

    override suspend fun upsert(entity: Attachment) = dao.upsert(entity)
    override suspend fun mergeRemoteIfNewer(entity: Attachment): RemoteMergeResult =
        dao.mergeRemoteIfNewer(entity)
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
    override fun recordFromJson(json: JsonObject): AttachmentRecord = gatewayJson.decodeFromJsonElement(json)
    override fun toJsonBody(entity: Attachment) = Json.encodeToString(entity.toAttachmentRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<AttachmentRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<AttachmentRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(client: PocketbaseClient, body: String): AttachmentRecord =
        throw SyncAdapterException("Attachment creates must use the guarded multipart gateway")

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String): AttachmentRecord =
        throw SyncAdapterException("Attachment updates must use the guarded multipart gateway")

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): AttachmentRecord? =
        throw SyncAdapterException("Attachment lookup must use the structured guarded gateway")

    override suspend fun pullAll(client: PocketbaseClient) {
        val remoteRecords = if (allowsTestOnlyLegacySdkWrites()) {
            fetchAllRecords(client)
        } else {
            fetchAllRecordsThroughGateway(client)
        }
        val gateway = if (allowsTestOnlyLegacySdkWrites()) null else recordGateway(client)
        val localSnapshot = getAllOnce()
        val localById = localSnapshot.associateBy { it.id }
        for (record in remoteRecords) {
            val local = localById[record.localId]
            if (shouldSkipIncomingRecord(record, local)) continue
            val incoming = record.toAttachment()
            if (record.isDeleted) {
                upsertRemoteTombstone(incoming, local)
                continue
            }
            if (record.file.isNullOrBlank()) {
                log.w { "Active remote attachment ${record.localId} has no file; retaining local files" }
                upsertRemoteDownloadFailure(
                    incoming,
                    SyncAdapterException("Active remote attachment ${record.localId} has no file"),
                    local,
                )
                continue
            }
            if (record.fileSizeBytes > AttachmentFilePolicy.MAX_UPLOAD_BYTES || record.kind != ATTACHMENT_KIND_IMAGE) {
                upsertRemotePolicyBlock(incoming, local)
                continue
            }
            if (local?.remoteFileName == record.file && fileStorage.exists(local.localPath)) {
                upsertRemoteSameFile(incoming)
                continue
            }
            runCatching {
                val recordId = record.id ?: throw SyncAdapterException("Attachment ${record.localId} missing remote id")
                val bytes = if (gateway != null) {
                    val response = gateway.downloadProtectedFile(recordId, record.file)
                    if (!response.isSuccess) throw AttachmentFileDownloadException(response.status.value)
                    response.body ?: throw AttachmentFileDownloadException(response.status.value)
                } else {
                    throw SyncAdapterException("Protected attachment downloads require the owner-scoped gateway")
                }
                val stored = fileStorage.storeRemoteImage(record.file, bytes)
                upsertRemoteDownloadSuccess(incoming, stored, local)
            }.onFailure {
                if (it is CancellationException) throw it
                log.e(it) { "Failed to download attachment ${record.localId}" }
                upsertRemoteDownloadFailure(incoming, it, local)
            }
        }
        recoverMissingRemoteRows(remoteRecords, localSnapshot)
    }

    internal suspend fun upsertRemoteDownloadFailure(
        incoming: Attachment,
        error: Throwable,
        local: Attachment? = null,
    ) {
        val (syncState, errorCode) = when (error) {
            is AttachmentImageDecodeException -> AttachmentSyncState.BLOCKED to "blocked_decode_failed"
            is AttachmentFileDownloadException -> AttachmentSyncState.FAILED to error.downloadErrorCode()
            else -> AttachmentSyncState.FAILED to "download_failed"
        }
        dao.mergeRemoteWithRetainedFilesIfNewer(incoming, syncState, errorCode)
    }

    internal suspend fun upsertRemotePolicyBlock(
        incoming: Attachment,
        local: Attachment? = null,
    ) {
        dao.mergeRemoteWithRetainedFilesIfNewer(
            incoming,
            AttachmentSyncState.BLOCKED,
            "blocked_policy",
        )
    }

    internal suspend fun upsertRemoteSameFile(incoming: Attachment) {
        dao.mergeSameFileRemoteIfNewer(incoming)
    }

    internal suspend fun upsertRemoteDownloadSuccess(
        incoming: Attachment,
        stored: StoredAttachmentFile,
        local: Attachment?,
    ) {
        val replacement = incoming.copy(
            localPath = stored.localPath,
            thumbnailPath = stored.thumbnailPath,
            fileName = stored.fileName,
            mimeType = stored.mimeType,
            fileSizeBytes = stored.fileSizeBytes,
            width = stored.width,
            height = stored.height,
        ).withSyncState(AttachmentSyncState.SYNCED)
        when (val installed = dao.installDownloadedRemoteIfNewer(replacement)) {
            is AttachmentDownloadInstallResult.Applied ->
                installed.replaced?.let { deleteSupersededLocalFiles(it, replacement) }
            AttachmentDownloadInstallResult.KeptLocal -> deleteDownloadedFiles(replacement)
        }
    }

    internal suspend fun shouldSkipIncomingRecord(record: AttachmentRecord, local: Attachment?): Boolean {
        if (local == null) return false
        val localFileMissing = !local.isDeleted &&
                !record.isDeleted &&
                !record.file.isNullOrBlank() &&
                !fileStorage.exists(local.localPath)
        val retryRemoteDownload = local.isRemoteOriginDownloadFailure()
        return record.updatedAtUtc <= local.updatedAt && !localFileMissing && !retryRemoteDownload
    }

    internal suspend fun upsertRemoteTombstone(incoming: Attachment, local: Attachment?) {
        when (val merged = dao.mergeRemoteTombstoneIfNewer(incoming.withSyncState(AttachmentSyncState.SYNCED))) {
            is AttachmentTombstoneMergeResult.Applied -> merged.replaced?.let { deleteLocalFilesFor(it) }
            AttachmentTombstoneMergeResult.KeptLocal -> Unit
        }
    }

    private suspend fun deleteLocalFilesFor(attachment: Attachment) {
        deleteFileBestEffort(attachment.localPath)
        deleteFileBestEffort(attachment.thumbnailPath)
    }

    private suspend fun deleteSupersededLocalFiles(previous: Attachment, replacement: Attachment) {
        if (previous.localPath.isNotBlank() && previous.localPath != replacement.localPath) {
            deleteFileBestEffort(previous.localPath)
        }
        if (previous.thumbnailPath.isNotBlank() && previous.thumbnailPath != replacement.thumbnailPath) {
            deleteFileBestEffort(previous.thumbnailPath)
        }
    }

    private suspend fun deleteDownloadedFiles(attachment: Attachment) {
        deleteFileBestEffort(attachment.localPath)
        deleteFileBestEffort(attachment.thumbnailPath)
    }

    private suspend fun deleteFileBestEffort(path: String) {
        if (path.isBlank()) return
        try {
            fileStorage.delete(path)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Local cleanup is best effort after the Room decision is durable.
        }
    }

    override suspend fun pushAll(client: PocketbaseClient) {
        pushAttachments(client, seedMode = false, allowRemoteMerge = true)
    }

    /**
     * Seed mode preserves never-synced tombstones so the replacement server
     * receives durable deletion metadata. Normal sync retains the local-only
     * cleanup shortcut before any parent or gateway work.
     */
    override suspend fun seedAll(client: PocketbaseClient) {
        pushAttachments(client, seedMode = true, allowRemoteMerge = true)
    }

    override suspend fun seedAllAuthoritative(client: PocketbaseClient) {
        pushAttachments(client, seedMode = true, allowRemoteMerge = false)
    }

    override suspend fun validateLocalSeedSource() {
        for (attachment in getAllOnce()) {
            validateAttachmentSeedSource(attachment)
        }
    }

    private suspend fun pushAttachments(
        client: PocketbaseClient,
        seedMode: Boolean,
        allowRemoteMerge: Boolean,
    ) {
        val failures = mutableListOf<Throwable>()
        for (attachment in getUnsynced()) {
            if (!seedMode && attachment.isDeleted && attachment.pbId == null) {
                hardDeleteLocalNeverSynced(attachment)
                continue
            }
            if (!allowRemoteMerge) validateAttachmentSeedSource(attachment)
            if (!shouldPush(attachment)) continue
            try {
                pushOne(client, attachment, allowRemoteMerge)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is AuthoritativeSeedConflictException) throw e
                if (e is AuthoritativeLocalSeedSourceException) throw e
                log.e(e) { "Failed to push attachment ${attachment.id}" }
                failures += e
                dao.markSyncFailed(attachment.id, AttachmentSyncState.FAILED.name, "sync_failed")
            }
        }
        if (failures.isNotEmpty()) {
            throw SyncAdapterException("Failed to push $collectionName", failures.first())
        }
    }

    override suspend fun isSeedComplete(rows: List<JsonObject>): Boolean =
        super.isSeedComplete(rows) && rows.all { row ->
            val record = recordFromJson(row)
            if (record.isDeleted) record.file.isNullOrBlank() else !record.file.isNullOrBlank()
        }

    /** PocketBase assigns active attachment file names, so only that server-owned field may differ on resume. */
    override suspend fun validateSeedInventory(rows: List<JsonObject>): Boolean {
        for (row in rows) {
            val record = runCatching { recordFromJson(row) }.getOrElse { return false }
            val local = getById(record.localId) ?: return false
            when {
                record.updatedAtUtc > local.updatedAt -> return false
                record.updatedAtUtc == local.updatedAt &&
                    !attachmentSeedPayloadEquals(local, row, record.isDeleted) -> return false
                record.isDeleted && !record.file.isNullOrBlank() -> return false
            }
        }
        return true
    }

    private fun attachmentSeedPayloadEquals(
        local: Attachment,
        remote: JsonObject,
        isDeleted: Boolean,
    ): Boolean {
        val localBody = JsonObject(local.toBody()).let { body ->
            if (isDeleted) body else JsonObject(body.filterKeys { it != "file" })
        }
        val remoteBody = if (isDeleted) remote else JsonObject(remote.filterKeys { it != "file" })
        return canonicalPayloadEquals(localBody, remoteBody)
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
        !isDeleted && (lastSyncError == "download_failed" || lastSyncError?.startsWith("download_http_") == true)

    private suspend fun validateAttachmentSeedSource(attachment: Attachment) {
        if (attachment.isDeleted) return
        if (attachment.syncState == AttachmentSyncState.BLOCKED ||
            attachment.syncState == AttachmentSyncState.NEEDS_DOWNLOAD ||
            attachment.isRemoteOriginDownloadFailure() ||
            attachment.ownerType != ATTACHMENT_OWNER_TASK ||
            taskDao.findTaskByIdAnyState(attachment.ownerId) == null
        ) {
            throw AuthoritativeLocalSeedSourceException()
        }
        val bytes = try {
            fileStorage.readBytes(attachment.localPath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw AuthoritativeLocalSeedSourceException()
        }
        if (bytes == null) throw AuthoritativeLocalSeedSourceException()
    }

    private fun AttachmentFileDownloadException.downloadErrorCode(): String =
        when (statusCode) {
            in 300..399 -> "download_http_3xx"
            in 400..499 -> "download_http_4xx"
            in 500..599 -> "download_http_5xx"
            else -> "download_http_other"
        }

    private suspend fun pushOne(
        client: PocketbaseClient,
        attachment: Attachment,
        allowRemoteMerge: Boolean,
    ) {
        val body = JsonObject(attachment.toBody())
        if (attachment.isDeleted) {
            pushAttachmentTombstone(client, attachment, body, allowRemoteMerge)
            return
        }

        val parent = taskDao.findTaskByIdAnyState(attachment.ownerId)
        if (attachment.ownerType == ATTACHMENT_OWNER_TASK && parent?.pbId == null) {
            if (!allowRemoteMerge) throw AuthoritativeLocalSeedSourceException()
            return
        }

        val bytes = try {
            fileStorage.readBytes(attachment.localPath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!allowRemoteMerge) throw AuthoritativeLocalSeedSourceException()
            throw error
        }
        if (bytes == null) {
            if (!allowRemoteMerge) throw AuthoritativeLocalSeedSourceException()
            dao.markSyncFailed(attachment.id, AttachmentSyncState.FAILED.name, "local_file_missing")
            return
        }
        val gateway = recordGateway(client)
        val updated = pushActiveAttachment(
            gateway, attachment, body, bytes, allowRemoteMerge,
        ) ?: return
        val remoteId = updated["id"]?.jsonPrimitive?.contentOrNull
        if (remoteId != null) dao.updatePbId(attachment.id, remoteId)
        val changed = dao.confirmActiveSyncedIfUnchanged(
            id = attachment.id,
            updatedAt = attachment.updatedAt,
            pbId = remoteId,
            remoteFileName = updated["file"]?.jsonPrimitive?.contentOrNull,
        )
        if (changed == 0) log.w { "Attachment ${attachment.id} changed during multipart upload; leaving it unsynced" }
    }

    /**
     * Resolve every rejected multipart write from structured gateway responses.
     * A newer/equal-different remote row is never overwritten by an SDK retry.
     */
    private suspend fun pushActiveAttachment(
        gateway: PocketBaseRecordGateway,
        attachment: Attachment,
        body: JsonObject,
        bytes: ByteArray,
        allowRemoteMerge: Boolean,
    ): JsonObject? {
        val preflight = attachment.pbId?.let { gateway.getRecord(collectionName, it) }
        if (preflight?.isSuccess == true) {
            val remote = preflight.body
                ?: throw SyncAdapterException("Attachment ${attachment.id} preflight returned no record")
            return reconcileActiveAttachment(gateway, attachment, body, bytes, remote, allowRemoteMerge)
        }
        if (preflight != null && !preflight.isNotFound) {
            throw SyncAdapterException("Attachment ${attachment.id} preflight failed with HTTP ${preflight.status.value}")
        }

        val lookup = gateway.findByLocalId(collectionName, attachment.id)
        if (!lookup.isSuccess) {
            throw SyncAdapterException("Attachment ${attachment.id} lookup failed with HTTP ${lookup.status.value}")
        }
        val existing = lookup.body
        if (existing == null) {
            val created = gateway.createAttachment(body, attachment.fileName, bytes)
            if (created.isSuccess) return created.body
            val recovery = gateway.findByLocalId(collectionName, attachment.id)
            if (!recovery.isSuccess) {
                throw SyncAdapterException("Attachment create rejected with HTTP ${created.status.value}; recovery failed with HTTP ${recovery.status.value}")
            }
            val recovered = recovery.body
                ?: throw SyncAdapterException("Attachment create rejected with HTTP ${created.status.value}")
            return reconcileActiveAttachment(gateway, attachment, body, bytes, recovered, allowRemoteMerge)
        }
        return reconcileActiveAttachment(gateway, attachment, body, bytes, existing, allowRemoteMerge)
    }

    private suspend fun reconcileActiveAttachment(
        gateway: PocketBaseRecordGateway,
        attachment: Attachment,
        body: JsonObject,
        bytes: ByteArray,
        remote: JsonObject,
        allowRemoteMerge: Boolean,
    ): JsonObject? {
        val record = recordFromJson(remote)
        when {
            record.updatedAtUtc > attachment.updatedAt -> {
                if (!allowRemoteMerge) {
                    throw AuthoritativeSeedConflictException(
                        "Authoritative seed rejected a newer remote attachment row",
                    )
                }
                mergeRemoteIfNewer(record.toAttachment())
                return null
            }
            record.updatedAtUtc == attachment.updatedAt && canonicalPayloadEquals(body, remote) -> return remote
            record.updatedAtUtc < attachment.updatedAt -> {
                val remoteId = remote["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw SyncAdapterException("Attachment ${attachment.id} recovery has no record id")
                val retried = gateway.updateAttachment(remoteId, body, attachment.fileName, bytes)
                if (retried.isSuccess) return retried.body
            }
        }
        if (!allowRemoteMerge) {
            throw AuthoritativeSeedConflictException(
                "Authoritative seed found a divergent remote attachment row",
            )
        }
        throw SyncAdapterException("Attachment ${attachment.id} guarded write conflict was not safely resolvable")
    }

    private suspend fun pushAttachmentTombstone(
        client: PocketbaseClient,
        attachment: Attachment,
        body: JsonObject,
        allowRemoteMerge: Boolean,
    ) {
        val gateway = recordGateway(client)
        val byPbId = attachment.pbId?.let { pbId ->
            val preflight = gateway.getRecord(collectionName, pbId)
            when {
                preflight.isSuccess -> preflight.body
                preflight.isNotFound -> null
                else -> throw SyncAdapterException(
                    "Attachment ${attachment.id} tombstone preflight failed with HTTP ${preflight.status.value}",
                )
            }
        }
        var currentRecord = byPbId ?: findAttachmentRecordByLocalId(gateway, attachment.id)
        if (currentRecord == null) {
            val created = gateway.createAttachmentTombstone(body)
            if (created.isSuccess) {
                currentRecord = created.body
            } else {
                // A process death after create can surface as a unique-create conflict.
                currentRecord = findAttachmentRecordByLocalId(gateway, attachment.id)
                    ?: throw SyncAdapterException("Attachment tombstone was rejected with HTTP ${created.status.value}")
            }
        }
        val current = currentRecord
            ?: throw SyncAdapterException("Unable to resolve attachment ${attachment.id} before tombstone")
        val updated = reconcileAttachmentTombstone(
            gateway, attachment, body, current, allowRemoteMerge = allowRemoteMerge,
        ) ?: return
        val returnedFile = updated["file"]?.jsonPrimitive?.contentOrNull
        if (!returnedFile.isNullOrBlank()) {
            throw SyncAdapterException("Attachment tombstone retained a remote file")
        }
        dao.confirmTombstoneSyncedIfUnchanged(
            id = attachment.id,
            updatedAt = attachment.updatedAt,
            pbId = updated["id"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun findAttachmentRecordByLocalId(
        gateway: PocketBaseRecordGateway,
        localId: String,
    ): JsonObject? {
        val lookup = gateway.findByLocalId(collectionName, localId)
        if (!lookup.isSuccess) {
            throw SyncAdapterException("Attachment $localId lookup failed with HTTP ${lookup.status.value}")
        }
        return lookup.body
    }

    /** Re-read a rejected tombstone write, but never retry without reapplying timestamp guards. */
    private suspend fun reconcileAttachmentTombstone(
        gateway: PocketBaseRecordGateway,
        attachment: Attachment,
        body: JsonObject,
        remote: JsonObject,
        mayWrite: Boolean = true,
        allowRemoteMerge: Boolean = true,
    ): JsonObject? {
        val remoteRecord = recordFromJson(remote)
        return when {
            remoteRecord.updatedAtUtc > attachment.updatedAt -> {
                if (!allowRemoteMerge) {
                    throw AuthoritativeSeedConflictException(
                        "Authoritative seed rejected a newer remote attachment tombstone",
                    )
                }
                mergeRemoteIfNewer(remoteRecord.toAttachment())
                null
            }
            remoteRecord.updatedAtUtc == attachment.updatedAt &&
                canonicalPayloadEquals(body, remote) &&
                remote["file"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() -> remote
            remoteRecord.updatedAtUtc == attachment.updatedAt -> {
                if (!allowRemoteMerge) {
                    throw AuthoritativeSeedConflictException(
                        "Authoritative seed found an equal-timestamp divergent attachment tombstone",
                    )
                }
                throw SyncAdapterException(
                    "Attachment ${attachment.id} equal-timestamp tombstone payload differs remotely",
                )
            }
            !mayWrite -> {
                if (!allowRemoteMerge) {
                    throw AuthoritativeSeedConflictException(
                        "Authoritative seed could not prove the remote attachment tombstone",
                    )
                }
                throw SyncAdapterException(
                    "Attachment ${attachment.id} tombstone write was rejected without a safely newer remote row",
                )
            }
            else -> {
                val remoteId = remote["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw SyncAdapterException("Resolved attachment ${attachment.id} has no record id")
                val response = gateway.updateAttachmentTombstone(
                    remoteId,
                    body,
                    remote["file"]?.jsonPrimitive?.contentOrNull,
                )
                if (response.isSuccess) {
                    response.body ?: throw SyncAdapterException("Attachment tombstone update returned no record")
                } else {
                    val reread = findAttachmentRecordByLocalId(gateway, attachment.id)
                        ?: throw SyncAdapterException("Attachment tombstone was rejected with HTTP ${response.status.value}")
                    reconcileAttachmentTombstone(
                        gateway,
                        attachment,
                        body,
                        reread,
                        mayWrite = false,
                        allowRemoteMerge = allowRemoteMerge,
                    )
                }
            }
        }
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
            val message =
                "Degraded $collectionName sync: server returned ${remoteRecords.size} records but ${localSyncedActive.size} local synced exist -- possible partial response"
            log.w { message }
            throw SyncDegradedException(message)
        }
        val remoteIds = remoteRecords.map { it.localId }.toSet()
        for (local in localSyncedActive) {
            if (local.id !in remoteIds) {
                log.w { "Recovering missing $collectionName ${local.id}: server row absent, marking unsynced for recreation" }
                dao.markUnsynced(local.id)
            }
        }
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

    private companion object {
        const val MISSING_ROW_RECOVERY_MIN_RATIO = 0.1
    }
}
