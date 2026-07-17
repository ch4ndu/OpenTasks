package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.lighthousegames.logging.logging

private val log = logging("SyncAdapter")

abstract class BaseSyncAdapter<Entity, Record : BaseModel> {

    protected val gatewayJson = Json { ignoreUnknownKeys = true }

    /** PocketBase collection name (e.g., "tasks", "categories"). */
    abstract val collectionName: String

    /** Push ordering -- lower values are pushed first. Categories should be 0 (pushed before tasks). */
    open val order: Int = 10

    // DAO operations
    abstract suspend fun getUnsynced(): List<Entity>
    abstract suspend fun getAllOnce(): List<Entity>
    abstract suspend fun getById(localId: String): Entity?
    abstract suspend fun markSyncedIfUnchanged(
        localId: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    abstract suspend fun updatePbId(
        localId: String,
        pbId: String
    )

    abstract suspend fun markUnsynced(localId: String)
    abstract suspend fun hardDeleteLocalNeverSynced(entity: Entity)

    /**
     * A locally created tombstone can only be discarded when no remotely
     * identified child still needs its parent relation to remain durable.
     */
    open suspend fun shouldHardDeleteLocalNeverSynced(entity: Entity): Boolean = true

    abstract suspend fun upsert(entity: Entity)
    /**
     * Atomically rereads and merges an incoming record at the Room writer boundary.
     * The pull snapshot is intentionally never used to authorize an overwrite.
     */
    abstract suspend fun mergeRemoteIfNewer(entity: Entity): RemoteMergeResult

    // Entity field accessors
    abstract fun localId(entity: Entity): String
    abstract fun pbId(entity: Entity): String?
    abstract fun isDeleted(entity: Entity): Boolean
    abstract fun isSynced(entity: Entity): Boolean
    abstract fun updatedAt(entity: Entity): Long

    // Record field accessors
    abstract fun recordLocalId(record: Record): String
    abstract fun recordIsDeleted(record: Record): Boolean
    abstract fun recordUpdatedAt(record: Record): Long

    // Conversion
    abstract fun toRecord(entity: Entity): Record
    abstract fun toEntity(record: Record): Entity
    abstract fun recordFromJson(json: JsonObject): Record

    // PocketBase operations (concrete adapters provide reified type wrappers)
    abstract suspend fun fetchAllRecords(client: PocketbaseClient): List<Record>
    abstract suspend fun verifyCollection(client: PocketbaseClient)
    abstract suspend fun createRecord(
        client: PocketbaseClient,
        body: String
    ): Record

    abstract suspend fun updateRecord(
        client: PocketbaseClient,
        pbId: String,
        body: String
    ): Record

    abstract suspend fun findRecordByLocalId(
        client: PocketbaseClient,
        localId: String
    ): Record?

    /** Production clients always have a canonical endpoint and therefore use guarded HTTP writes. */
    open fun recordGateway(client: PocketbaseClient): PocketBaseRecordGateway =
        PocketBaseRecordGatewayFactory().create(client)

    /** Only in-memory test adapters may opt into the legacy SDK fake seam. */
    protected open fun allowsTestOnlyLegacySdkWrites(): Boolean = false

    /** Prefetch any local state needed to validate remote rows for this pull. */
    open suspend fun prepareRemoteValidation(records: List<Record>) {}

    /** Return a message when a remote row should be skipped but surfaced as degraded sync. */
    open suspend fun validateRemoteRecord(record: Record): String? = null

    /** Skip durable orphan tombstones without degrading a pull or blocking valid pushes. */
    open suspend fun skipRemoteRecordSilently(record: Record): Boolean = false

    /** Clear validation state created for this pull. */
    open fun clearRemoteValidation() {}

    /** Serialize entity to JSON body string for PocketBase. */
    abstract fun toJsonBody(entity: Entity): String

    /** Push all unsynced entities to server. */
    open suspend fun pushAll(client: PocketbaseClient) {
        val unsynced = getUnsynced()
        val failures = mutableListOf<Throwable>()
        log.d { "Pushing ${unsynced.size} $collectionName" }
        for (entity in unsynced) {
            try {
                val entityLocalId = localId(entity)
                val entityPbId = pbId(entity)
                val entityUpdatedAt = updatedAt(entity)
                val entityIsDeleted = isDeleted(entity)

                if (entityIsDeleted && entityPbId == null && shouldHardDeleteLocalNeverSynced(entity)) {
                    val toDelete = getById(entityLocalId)
                    if (toDelete != null) hardDeleteLocalNeverSynced(toDelete)
                    continue
                }

                val body = stripId(toJsonBody(entity))

                if (entityPbId != null) {
                    val result = updateByPbIdOrRecover(client, entityLocalId, entityPbId, body)
                    if (result == PushResolution.Pushed) {
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                    } else if (result == PushResolution.Failed) {
                        failures += SyncAdapterException("Failed to update $collectionName $entityLocalId")
                    }
                } else {
                    when (createOrRecover(client, entityLocalId, entityUpdatedAt, body)) {
                        PushResolution.Pushed -> {
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                        }
                        PushResolution.RemoteWon -> Unit
                        PushResolution.Failed -> failures += SyncAdapterException("Failed to create $collectionName $entityLocalId")
                    }
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to push $collectionName ${localId(entity)}" }
                failures += e
            }
        }
        if (failures.isNotEmpty()) {
            throw SyncAdapterException("Failed to push $collectionName", failures.first())
        }
    }

    /**
     * Migration seeding is deliberately separate from normal push semantics:
     * pending tombstones are durable remote records, never candidates for the
     * normal no-pbId local hard-delete shortcut.
     */
    open suspend fun seedAll(client: PocketbaseClient) {
        val failures = mutableListOf<Throwable>()
        for (entity in getUnsynced()) {
            try {
                val id = localId(entity)
                val timestamp = updatedAt(entity)
                val deleted = isDeleted(entity)
                val body = stripId(toJsonBody(entity))
                val resolution = pbId(entity)?.let {
                    updateByPbIdOrRecover(client, id, it, body)
                } ?: createOrRecover(client, id, timestamp, body)
                if (resolution == PushResolution.Pushed) markSyncedAfterPush(id, timestamp, deleted)
            } catch (error: Exception) {
                failures += error
            }
        }
        if (failures.isNotEmpty()) throw SyncAdapterException("Failed to seed $collectionName", failures.first())
    }

    /** Reject seed resume when the candidate has unknown, newer, or divergent rows. */
    open suspend fun validateSeedInventory(rows: List<JsonObject>): Boolean {
        for (row in rows) {
            val record = runCatching { recordFromJson(row) }.getOrElse { return false }
            val local = getById(recordLocalId(record)) ?: return false
            when {
                recordUpdatedAt(record) > updatedAt(local) -> return false
                recordUpdatedAt(record) == updatedAt(local) &&
                    !canonicalPayloadEquals(
                        Json.parseToJsonElement(stripId(toJsonBody(local))) as? JsonObject ?: return false,
                        row,
                    ) -> return false
            }
        }
        return true
    }

    /** Final inventory must exactly represent every durable active row and tombstone. */
    open suspend fun isSeedComplete(rows: List<JsonObject>): Boolean {
        val local = getAllOnce()
        if (rows.size != local.size || getUnsynced().isNotEmpty()) return false
        return validateSeedInventory(rows) && rows.mapNotNull { it["localId"]?.toString()?.trim('"') }.toSet() ==
            local.map { localId(it) }.toSet()
    }

    /** Pull all records from server and merge locally. */
    open suspend fun pullAll(client: PocketbaseClient) {
        try {
            val remoteRecords = fetchAllRecords(client)
            log.d { "Pulled ${remoteRecords.size} $collectionName" }

            val localSnapshot = getAllOnce()
            val localSyncedSnapshot = localSnapshot.filter { isSynced(it) && !isDeleted(it) }
            val degradedMessages = mutableListOf<String>()

            try {
                prepareRemoteValidation(remoteRecords)
                for (record in remoteRecords) {
                    if (skipRemoteRecordSilently(record)) continue
                    val validationMessage = validateRemoteRecord(record)
                    if (validationMessage != null) {
                        log.w { validationMessage }
                        degradedMessages += validationMessage
                        continue
                    }
                    when (mergeRemoteIfNewer(toEntity(record))) {
                        RemoteMergeResult.Applied,
                        RemoteMergeResult.KeptLocal -> Unit
                        RemoteMergeResult.MissingParent -> {
                            val message = "Skipping orphan $collectionName ${recordLocalId(record)}: missing local parent"
                            log.w { message }
                            degradedMessages += message
                        }
                    }
                }
            } finally {
                clearRemoteValidation()
            }

            val remoteIds = remoteRecords.map { recordLocalId(it) }.toSet()
            if (degradedMessages.isNotEmpty()) {
                log.w { "Skipping $collectionName missing-row recovery because pull is already degraded" }
            } else if (remoteRecords.isEmpty() && localSyncedSnapshot.isNotEmpty()) {
                val message =
                    "Degraded $collectionName sync: server returned 0 records but ${localSyncedSnapshot.size} local synced exist; skipping missing-row recovery"
                log.w { message }
                degradedMessages += message
            } else if (remoteRecords.size < localSyncedSnapshot.size * 0.1 && localSyncedSnapshot.isNotEmpty()) {
                val message =
                    "Degraded $collectionName sync: server returned ${remoteRecords.size} records but ${localSyncedSnapshot.size} local synced exist -- possible partial response"
                log.w { message }
                degradedMessages += message
            } else {
                for (local in localSyncedSnapshot) {
                    if (localId(local) !in remoteIds) {
                        try {
                            log.w { "Recovering missing $collectionName ${localId(local)}: server row absent, marking unsynced for recreation" }
                            markUnsynced(localId(local))
                        } catch (e: Exception) {
                            log.e(e) { "Failed to mark missing $collectionName ${localId(local)} unsynced" }
                        }
                    }
                }
            }
            if (degradedMessages.isNotEmpty()) {
                throw SyncDegradedException(degradedMessages.joinToString("; "))
            }
        } catch (e: SyncDegradedException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to pull $collectionName" }
            throw SyncAdapterException("Failed to pull $collectionName", e)
        }
    }

    /** Remove the PocketBase auto-assigned "id" field from JSON to avoid pk_change errors. */
    private fun stripId(jsonString: String): String {
        val obj = Json.parseToJsonElement(jsonString) as? JsonObject ?: return jsonString
        return buildJsonObject {
            obj.forEach { (key, value) -> if (key != "id") put(key, value) }
        }.toString()
    }

    private suspend fun updateByPbIdOrRecover(
        client: PocketbaseClient,
        localId: String,
        pbId: String,
        body: String,
    ): PushResolution {
        if (allowsTestOnlyLegacySdkWrites()) {
            return testOnlyUpdateByPbIdOrRecover(client, localId, pbId, body)
        }
        val gateway = runCatching { recordGateway(client) }.getOrNull()
        if (gateway != null) return guardedUpdateByPbIdOrRecover(gateway, localId, pbId, body)
        throw SyncAdapterException("$collectionName requires a canonical guarded PocketBase gateway")
    }

    /** Isolated fake adapters can model SDK records without exposing this path to production DI. */
    private suspend fun testOnlyUpdateByPbIdOrRecover(
        client: PocketbaseClient,
        localId: String,
        pbId: String,
        body: String,
    ): PushResolution {
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure { lookupError ->
                log.e(lookupError) { "Test-only localId lookup failed for $collectionName $localId" }
            }
            .getOrNull()
            ?: return PushResolution.Failed
        val localUpdatedAt = Json.parseToJsonElement(body)
            .jsonObject["localUpdatedAt"]
            ?.toString()
            ?.trim('"')
            ?.toLongOrNull()
            ?: return PushResolution.Failed
        val recoveredPbId = existing.id ?: return PushResolution.Failed
        return when {
            recordUpdatedAt(existing) > localUpdatedAt -> {
                mergeRemoteIfNewer(toEntity(existing))
                PushResolution.RemoteWon
            }
            recordUpdatedAt(existing) == localUpdatedAt && testOnlyCanonicalPayloadEquals(body, existing) -> {
                updatePbIdSafely(localId, recoveredPbId)
                PushResolution.Pushed
            }
            recordUpdatedAt(existing) < localUpdatedAt -> runCatching {
                updateRecord(client, recoveredPbId, body)
            }.onFailure { retryError ->
                log.e(retryError) { "Test-only recovered update failed for $collectionName $localId" }
            }.fold(
                onSuccess = {
                    updatePbIdSafely(localId, recoveredPbId)
                    PushResolution.Pushed
                },
                onFailure = { PushResolution.Failed },
            )
            else -> PushResolution.Failed
        }
    }

    private suspend fun createOrRecover(
        client: PocketbaseClient,
        localId: String,
        updatedAt: Long,
        body: String,
    ): PushResolution {
        if (allowsTestOnlyLegacySdkWrites()) {
            return testOnlyCreateOrRecover(client, localId, updatedAt, body)
        }
        val gateway = runCatching { recordGateway(client) }.getOrNull()
        if (gateway != null) return guardedCreateOrRecover(gateway, localId, updatedAt, body)
        throw SyncAdapterException("$collectionName requires a canonical guarded PocketBase gateway")
    }

    private suspend fun testOnlyCreateOrRecover(
        client: PocketbaseClient,
        localId: String,
        updatedAt: Long,
        body: String,
    ): PushResolution {
        val created = runCatching { createRecord(client, body) }
        if (created.isSuccess) {
            created.getOrNull()?.id?.let { updatePbIdSafely(localId, it) }
            return PushResolution.Pushed
        }
        log.w { "Create failed for $collectionName $localId; looking up existing server row by localId" }
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure { log.e(it) { "Failed localId lookup after create failure for $collectionName $localId" } }
            .getOrNull()
            ?: return PushResolution.Failed
        val serverId = existing.id ?: return PushResolution.Failed
        return when {
            recordUpdatedAt(existing) > updatedAt -> {
                mergeRemoteIfNewer(toEntity(existing))
                PushResolution.RemoteWon
            }
            recordUpdatedAt(existing) == updatedAt && testOnlyCanonicalPayloadEquals(body, existing) -> {
                updatePbIdSafely(localId, serverId)
                PushResolution.Pushed
            }
            recordUpdatedAt(existing) < updatedAt -> runCatching {
                updateRecord(client, serverId, body)
            }.onFailure {
                log.e(it) { "Test-only SDK update failed for $collectionName $localId after create conflict" }
            }.fold(
                onSuccess = {
                    updatePbIdSafely(localId, serverId)
                    PushResolution.Pushed
                },
                onFailure = { PushResolution.Failed },
            )
            else -> PushResolution.Failed
        }
    }

    private suspend fun guardedUpdateByPbIdOrRecover(
        gateway: PocketBaseRecordGateway,
        localId: String,
        pbId: String,
        body: String,
    ): PushResolution {
        val bodyObject = Json.parseToJsonElement(body) as? JsonObject ?: return PushResolution.Failed
        val preflight = gateway.getRecord(collectionName, pbId)
        if (preflight.isSuccess) {
            val existing = preflight.body ?: return PushResolution.Failed
            val record = recordFromJson(existing)
            val localUpdatedAt = bodyObject["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull()
                ?: return PushResolution.Failed
            return when {
                recordUpdatedAt(record) > localUpdatedAt -> {
                    mergeRemoteIfNewer(toEntity(record))
                    PushResolution.RemoteWon
                }
                recordUpdatedAt(record) == localUpdatedAt && canonicalPayloadEquals(bodyObject, existing) -> {
                    existing["id"]?.toString()?.trim('"')?.let { updatePbIdSafely(localId, it) }
                    PushResolution.Pushed
                }
                recordUpdatedAt(record) < localUpdatedAt -> {
                    val updated = gateway.updateJson(collectionName, pbId, bodyObject)
                    if (updated.isSuccess) {
                        updated.body?.get("id")?.let { updatePbIdSafely(localId, it.toString().trim('"')) }
                        PushResolution.Pushed
                    } else {
                        resolveRejectedGuardedWrite(gateway, localId, bodyObject, updated)
                    }
                }
                else -> PushResolution.Failed
            }
        }
        if (!preflight.isNotFound) return PushResolution.Failed
        return resolveRejectedGuardedWrite(gateway, localId, bodyObject, preflight)
    }

    private suspend fun guardedCreateOrRecover(
        gateway: PocketBaseRecordGateway,
        localId: String,
        updatedAt: Long,
        body: String,
    ): PushResolution {
        val bodyObject = Json.parseToJsonElement(body) as? JsonObject ?: return PushResolution.Failed
        val created = gateway.createJson(collectionName, bodyObject)
        if (created.isSuccess) {
            created.body?.get("id")?.let { updatePbIdSafely(localId, it.toString().trim('"')) }
            return PushResolution.Pushed
        }
        val lookup = gateway.findByLocalId(collectionName, localId)
        val existing = lookup.body ?: return PushResolution.Failed
        val record = recordFromJson(existing)
        return resolveExistingAfterCreateConflict(gateway, localId, updatedAt, bodyObject, existing, record)
    }

    private suspend fun resolveRejectedGuardedWrite(
        gateway: PocketBaseRecordGateway,
        localId: String,
        body: JsonObject,
        rejected: GatewayResponse<JsonObject>,
    ): PushResolution {
        val lookup = gateway.findByLocalId(collectionName, localId)
        val existing = lookup.body ?: return if (lookup.isSuccess) {
            guardedCreateOrRecover(gateway, localId, body["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L, body.toString())
        } else {
            log.w { "Guarded $collectionName update rejected with HTTP ${rejected.status.value}; lookup failed with HTTP ${lookup.status.value}" }
            PushResolution.Failed
        }
        val record = recordFromJson(existing)
        val localUpdatedAt = body["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
        return when {
            recordUpdatedAt(record) > localUpdatedAt -> {
                mergeRemoteIfNewer(toEntity(record))
                PushResolution.RemoteWon
            }
            recordUpdatedAt(record) == localUpdatedAt && canonicalPayloadEquals(body, existing) -> {
                existing["id"]?.toString()?.trim('"')?.let { updatePbIdSafely(localId, it) }
                PushResolution.Pushed
            }
            else -> PushResolution.Failed
        }
    }

    private suspend fun resolveExistingAfterCreateConflict(
        gateway: PocketBaseRecordGateway,
        localId: String,
        updatedAt: Long,
        body: JsonObject,
        existingJson: JsonObject,
        existing: Record,
    ): PushResolution = when {
        recordUpdatedAt(existing) > updatedAt -> {
            mergeRemoteIfNewer(toEntity(existing))
            PushResolution.RemoteWon
        }
        recordUpdatedAt(existing) == updatedAt && canonicalPayloadEquals(body, existingJson) -> {
            existingJson["id"]?.toString()?.trim('"')?.let { updatePbIdSafely(localId, it) }
            PushResolution.Pushed
        }
        recordUpdatedAt(existing) < updatedAt -> {
            val existingId = existingJson["id"]?.toString()?.trim('"') ?: return PushResolution.Failed
            val updated = gateway.updateJson(collectionName, existingId, body)
            if (updated.isSuccess) {
                updatePbIdSafely(localId, existingId)
                PushResolution.Pushed
            } else {
                PushResolution.Failed
            }
        }
        else -> PushResolution.Failed
    }

    protected fun canonicalPayloadEquals(local: JsonObject, remote: JsonObject): Boolean =
        local.entries.all { (key, value) -> remote[key] == value }

    private fun testOnlyCanonicalPayloadEquals(localBody: String, remote: Record): Boolean {
        val local = Json.parseToJsonElement(localBody) as? JsonObject ?: return false
        val remoteBody = Json.parseToJsonElement(stripId(toJsonBody(toEntity(remote)))) as? JsonObject
            ?: return false
        return canonicalPayloadEquals(local, remoteBody)
    }

    private suspend fun markSyncedAfterPush(
        localId: String,
        updatedAt: Long,
        isDeleted: Boolean
    ) {
        try {
            val changed = markSyncedIfUnchanged(localId, updatedAt, isDeleted)
            if (changed == 0) {
                log.w { "Skipped markSynced for $collectionName $localId: local row changed during push" }
            }
        } catch (e: Exception) {
            log.w(e) { "Failed to markSynced for $collectionName $localId (will retry)" }
        }
    }

    private suspend fun updatePbIdSafely(
        localId: String,
        pbId: String
    ) {
        try {
            updatePbId(localId, pbId)
        } catch (e: Exception) {
            log.e(e) { "Failed to save pbId for $collectionName $localId" }
        }
    }

    private enum class PushResolution { Pushed, RemoteWon, Failed }
}
