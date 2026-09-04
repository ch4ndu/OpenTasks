package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
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
    open fun recordGateway(client: PocketbaseClient): PocketBaseRecordGateway {
        val metadata = PocketBaseClientProvider.metadataFor(client)
            ?: error("PocketBase client has no registered metadata")
        return PocketBaseRecordGatewayFactory().create(
            client,
            metadata.endpoint,
            metadata.binding
                ?: error("PocketBase client has no active authenticated account"),
        )
    }

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

    /** Complete deterministic local snapshot projection used only for destructive reconfirmation. */
    suspend fun localInventoryFingerprintRows(): List<String> = getAllOnce()
        .map { entity ->
            val body = runCatching { Json.parseToJsonElement(stripId(toJsonBody(entity))) }
                .getOrElse { throw SyncAdapterException("Unable to fingerprint local $collectionName inventory", it) }
            "${localId(entity)}|${isDeleted(entity)}|${body.canonicalFingerprintText()}"
        }
        .sorted()

    /** Read-only preflight for rows that must be seedable before authoritative deletion begins. */
    open suspend fun validateLocalSeedSource() = Unit

    /**
     * Production pulls use the same owner-scoped gateway as writes.  The
     * legacy SDK fetch remains available only to isolated fake adapters.
     */
    protected suspend fun fetchAllRecordsThroughGateway(gateway: PocketBaseRecordGateway): List<Record> {
        val rows = mutableListOf<Record>()
        val pagination = PocketBasePaginationGuard(PAGE_SIZE)
        var page = 1
        var totalPages: Int
        do {
            val response = gateway.getRecords(collectionName, page, PAGE_SIZE)
            val result = response.body
                ?: throw SyncAdapterException(
                    "Unable to fetch $collectionName through the owner-scoped gateway (HTTP ${response.status.value})",
                )
            pagination.accept(page, result)
            rows += result.items.map { recordFromJson(it) }
            totalPages = result.totalPages
            page += 1
        } while (page <= totalPages)
        return rows
    }

    protected suspend fun verifyCollectionThroughGateway(gateway: PocketBaseRecordGateway) {
        val response = gateway.getRecords(collectionName, 1, 1)
        if (!response.isSuccess) {
            throw SyncAdapterException(
                "Unable to verify $collectionName through the owner-scoped gateway (HTTP ${response.status.value})",
            )
        }
    }

    /** Used by connection checks after a client has been activated. */
    suspend fun verifyCollectionForActiveBoundary(client: PocketbaseClient) {
        verifyCollectionThroughGateway(recordGateway(client))
    }

    /** Push all unsynced entities to server. */
    open suspend fun pushAll(client: PocketbaseClient) = pushAll(standalonePassContext(client))

    /** Production passes receive one shared owner-scoped gateway for all adapters. */
    open suspend fun pushAll(pass: SyncPassContext) {
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
                    val result = updateByPbIdOrRecover(pass, entityLocalId, entityPbId, body)
                    if (result == PushResolution.Pushed) {
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                    } else if (result == PushResolution.Failed) {
                        failures += SyncAdapterException("Failed to update a $collectionName record")
                    }
                } else {
                    when (createOrRecover(pass, entityLocalId, entityUpdatedAt, body)) {
                        PushResolution.Pushed -> {
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                        }
                        PushResolution.RemoteWon -> Unit
                        PushResolution.Failed -> failures += SyncAdapterException("Failed to create a $collectionName record")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.rethrowSyncAuthenticationRejected()
                log.e { "Failed to push a $collectionName record" }
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
    open suspend fun seedAll(client: PocketbaseClient) = seedAll(standalonePassContext(client))

    open suspend fun seedAll(pass: SyncPassContext) {
        seedAllInternal(pass, allowRemoteMerge = true)
    }

    /** Authoritative replacement treats any remote winner as a conflict and never merges it locally. */
    open suspend fun seedAllAuthoritative(client: PocketbaseClient) =
        seedAllAuthoritative(standalonePassContext(client))

    open suspend fun seedAllAuthoritative(pass: SyncPassContext) {
        seedAllInternal(pass, allowRemoteMerge = false)
    }

    private suspend fun seedAllInternal(pass: SyncPassContext, allowRemoteMerge: Boolean) {
        val failures = mutableListOf<Throwable>()
        for (entity in getUnsynced()) {
            try {
                val id = localId(entity)
                val timestamp = updatedAt(entity)
                val deleted = isDeleted(entity)
                val body = stripId(toJsonBody(entity))
                val resolution = pbId(entity)?.let {
                    updateByPbIdOrRecover(pass, id, it, body, allowRemoteMerge)
                } ?: createOrRecover(pass, id, timestamp, body, allowRemoteMerge)
                if (resolution == PushResolution.Pushed) markSyncedAfterPush(id, timestamp, deleted)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                error.rethrowSyncAuthenticationRejected()
                if (error is AuthoritativeSeedConflictException) throw error
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
    open suspend fun pullAll(client: PocketbaseClient) = pullAll(standalonePassContext(client))

    open suspend fun pullAll(pass: SyncPassContext) {
        val client = pass.client
        try {
            val remoteRecords = if (allowsTestOnlyLegacySdkWrites()) {
                fetchAllRecords(client)
            } else {
                fetchAllRecordsThroughGateway(requirePassGateway(pass))
            }
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
                        val message = "Remote $collectionName record failed validation"
                        log.w { message }
                        degradedMessages += message
                        continue
                    }
                    when (mergeRemoteAndValidate(pass, record)) {
                        RemoteMergeResult.Applied,
                        RemoteMergeResult.KeptLocal -> Unit
                        RemoteMergeResult.MissingParent -> {
                            val message = "Skipping orphan $collectionName record with a missing local parent"
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
                val missingCandidateIds = localSyncedSnapshot
                    .map(::localId)
                    .filterNot(remoteIds::contains)
                    .toSet()
                if (missingCandidateIds.isNotEmpty()) {
                    log.w {
                        "Recovering ${missingCandidateIds.size} missing $collectionName record(s) for recreation"
                    }
                    recoverMissingRowsAtWriterBoundary(pass, missingCandidateIds)
                }
            }
            if (degradedMessages.isNotEmpty()) {
                throw SyncDegradedException(degradedMessages.joinToString("; "))
            }
        } catch (e: SyncDegradedException) {
            e.rethrowSyncAuthenticationRejected()
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.rethrowSyncAuthenticationRejected()
            log.e { "Failed to pull $collectionName" }
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

    /**
     * A DAO may return KeptLocal because it reread a newer row, or because an
     * equal timestamp already exists. Equal timestamps are safe only when the
     * normalized durable payloads agree.
     */
    private suspend fun mergeRemoteAndValidate(
        pass: SyncPassContext,
        record: Record,
    ): RemoteMergeResult {
        val remote = toEntity(record)
        var result: RemoteMergeResult? = null
        pass.runWriterTransaction {
            val mergeResult = mergeRemoteIfNewer(remote)
            result = mergeResult
            if (mergeResult != RemoteMergeResult.KeptLocal) return@runWriterTransaction

            val current = getById(recordLocalId(record))
                ?: throw SyncDegradedException(
                    "$collectionName record disappeared while validating an equal-timestamp remote row",
                )
            when {
                updatedAt(current) > recordUpdatedAt(record) -> Unit
                updatedAt(current) < recordUpdatedAt(record) -> throw SyncDegradedException(
                    "$collectionName record changed while validating a remote row",
                )
                !normalizedPayloadEquals(current, remote) -> throw SyncDegradedException(
                    "$collectionName record has an equal-timestamp divergent payload",
                )
            }
        }
        return checkNotNull(result) { "Sync merge did not produce a result" }
    }

    /** Revalidates the complete snapshot candidate set in one pass-owned writer transaction. */
    private suspend fun recoverMissingRowsAtWriterBoundary(
        pass: SyncPassContext,
        localIds: Set<String>,
    ) {
        pass.runMissingRowTransaction {
            for (localId in localIds) {
                val current = getById(localId) ?: continue
                if (isSynced(current) && !isDeleted(current)) {
                    markUnsynced(localId)
                }
            }
        }
    }

    private fun normalizedPayloadEquals(local: Entity, remote: Entity): Boolean {
        val localBody = normalizedBody(local) ?: return false
        val remoteBody = normalizedBody(remote) ?: return false
        return localBody == remoteBody
    }

    private fun normalizedBody(entity: Entity): JsonObject? =
        Json.parseToJsonElement(stripId(toJsonBody(entity))) as? JsonObject

    protected fun standalonePassContext(client: PocketbaseClient): SyncPassContext =
        SyncPassContext.standalone(
            client = client,
            gateway = standaloneGatewayOrNull(client),
        )

    private fun standaloneGatewayOrNull(client: PocketbaseClient): PocketBaseRecordGateway? {
        if (allowsTestOnlyLegacySdkWrites()) return null
        return try {
            recordGateway(client)
        } catch (error: IllegalStateException) {
            val metadata = PocketBaseClientProvider.metadataFor(client)
            if (metadata == null || metadata.binding == null) {
                null
            } else {
                throw error
            }
        }
    }

    protected fun requirePassGateway(pass: SyncPassContext): PocketBaseRecordGateway =
        pass.gateway ?: throw SyncAdapterException("$collectionName requires a canonical guarded PocketBase gateway")

    private suspend fun updateByPbIdOrRecover(
        pass: SyncPassContext,
        localId: String,
        pbId: String,
        body: String,
        allowRemoteMerge: Boolean = true,
    ): PushResolution {
        val client = pass.client
        if (allowsTestOnlyLegacySdkWrites()) {
            return testOnlyUpdateByPbIdOrRecover(client, localId, pbId, body, allowRemoteMerge)
        }
        return guardedUpdateByPbIdOrRecover(
            requirePassGateway(pass),
            localId,
            pbId,
            body,
            allowRemoteMerge,
        )
    }

    /** Isolated fake adapters can model SDK records without exposing this path to production DI. */
    private suspend fun testOnlyUpdateByPbIdOrRecover(
        client: PocketbaseClient,
        localId: String,
        pbId: String,
        body: String,
        allowRemoteMerge: Boolean,
    ): PushResolution {
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure { lookupError ->
                if (lookupError is CancellationException) throw lookupError
                log.e { "Test-only local-id lookup failed for $collectionName" }
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
                resolveRemoteWinner(existing, allowRemoteMerge)
            }
            recordUpdatedAt(existing) == localUpdatedAt && testOnlyCanonicalPayloadEquals(body, existing) -> {
                updatePbIdSafely(localId, recoveredPbId)
                PushResolution.Pushed
            }
            recordUpdatedAt(existing) < localUpdatedAt -> runCatching {
                updateRecord(client, recoveredPbId, body)
            }.onFailure { retryError ->
                if (retryError is CancellationException) throw retryError
                log.e { "Test-only recovered update failed for $collectionName" }
            }.fold(
                onSuccess = {
                    updatePbIdSafely(localId, recoveredPbId)
                    PushResolution.Pushed
                },
                onFailure = { PushResolution.Failed },
            )
            else -> divergentRemoteResolution(allowRemoteMerge)
        }
    }

    private suspend fun createOrRecover(
        pass: SyncPassContext,
        localId: String,
        updatedAt: Long,
        body: String,
        allowRemoteMerge: Boolean = true,
    ): PushResolution {
        val client = pass.client
        if (allowsTestOnlyLegacySdkWrites()) {
            return testOnlyCreateOrRecover(client, localId, updatedAt, body, allowRemoteMerge)
        }
        return guardedCreateOrRecover(
            requirePassGateway(pass),
            localId,
            updatedAt,
            body,
            allowRemoteMerge,
        )
    }

    private suspend fun testOnlyCreateOrRecover(
        client: PocketbaseClient,
        localId: String,
        updatedAt: Long,
        body: String,
        allowRemoteMerge: Boolean,
    ): PushResolution {
        val created = runCatching { createRecord(client, body) }
        if (created.isSuccess) {
            created.getOrNull()?.id?.let { updatePbIdSafely(localId, it) }
            return PushResolution.Pushed
        }
        log.w { "Create failed for $collectionName; looking up an existing server row by local id" }
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure {
                if (it is CancellationException) throw it
                log.e { "Local-id lookup after create failure failed for $collectionName" }
            }
            .getOrNull()
            ?: return PushResolution.Failed
        val serverId = existing.id ?: return PushResolution.Failed
        return when {
            recordUpdatedAt(existing) > updatedAt -> {
                resolveRemoteWinner(existing, allowRemoteMerge)
            }
            recordUpdatedAt(existing) == updatedAt && testOnlyCanonicalPayloadEquals(body, existing) -> {
                updatePbIdSafely(localId, serverId)
                PushResolution.Pushed
            }
            recordUpdatedAt(existing) < updatedAt -> runCatching {
                updateRecord(client, serverId, body)
            }.onFailure {
                if (it is CancellationException) throw it
                log.e { "Test-only SDK update failed for $collectionName after a create conflict" }
            }.fold(
                onSuccess = {
                    updatePbIdSafely(localId, serverId)
                    PushResolution.Pushed
                },
                onFailure = { PushResolution.Failed },
            )
            else -> divergentRemoteResolution(allowRemoteMerge)
        }
    }

    private suspend fun guardedUpdateByPbIdOrRecover(
        gateway: PocketBaseRecordGateway,
        localId: String,
        pbId: String,
        body: String,
        allowRemoteMerge: Boolean,
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
                    resolveRemoteWinner(record, allowRemoteMerge)
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
                        resolveRejectedGuardedWrite(gateway, localId, bodyObject, updated, allowRemoteMerge)
                    }
                }
                else -> divergentRemoteResolution(allowRemoteMerge)
            }
        }
        if (!preflight.isNotFound) return PushResolution.Failed
        return resolveRejectedGuardedWrite(gateway, localId, bodyObject, preflight, allowRemoteMerge)
    }

    private suspend fun guardedCreateOrRecover(
        gateway: PocketBaseRecordGateway,
        localId: String,
        updatedAt: Long,
        body: String,
        allowRemoteMerge: Boolean,
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
        return resolveExistingAfterCreateConflict(
            gateway, localId, updatedAt, bodyObject, existing, record, allowRemoteMerge,
        )
    }

    private suspend fun resolveRejectedGuardedWrite(
        gateway: PocketBaseRecordGateway,
        localId: String,
        body: JsonObject,
        rejected: GatewayResponse<JsonObject>,
        allowRemoteMerge: Boolean,
    ): PushResolution {
        log.w {
            "Guarded $collectionName update rejected with HTTP ${rejected.status.value}; " +
                safePocketBaseFailureSummary(rejected.rawBody)
        }
        val lookup = gateway.findByLocalId(collectionName, localId)
        val existing = lookup.body ?: return if (lookup.isSuccess) {
            guardedCreateOrRecover(
                gateway,
                localId,
                body["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L,
                body.toString(),
                allowRemoteMerge,
            )
        } else {
            log.w { "Guarded $collectionName update rejected with HTTP ${rejected.status.value}; lookup failed with HTTP ${lookup.status.value}" }
            PushResolution.Failed
        }
        val record = recordFromJson(existing)
        val localUpdatedAt = body["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
        return when {
            recordUpdatedAt(record) > localUpdatedAt -> {
                resolveRemoteWinner(record, allowRemoteMerge)
            }
            recordUpdatedAt(record) == localUpdatedAt && canonicalPayloadEquals(body, existing) -> {
                existing["id"]?.toString()?.trim('"')?.let { updatePbIdSafely(localId, it) }
                PushResolution.Pushed
            }
            recordUpdatedAt(record) < localUpdatedAt -> {
                val recoveredPbId = existing["id"]?.toString()?.trim('"')
                    ?: return PushResolution.Failed
                val updated = gateway.updateJson(collectionName, recoveredPbId, body)
                if (updated.isSuccess) {
                    updatePbIdSafely(localId, recoveredPbId)
                    PushResolution.Pushed
                } else {
                    log.w {
                        "Recovered $collectionName update rejected with HTTP ${updated.status.value}; " +
                            safePocketBaseFailureSummary(updated.rawBody)
                    }
                    PushResolution.Failed
                }
            }
            else -> divergentRemoteResolution(allowRemoteMerge)
        }
    }

    private suspend fun resolveExistingAfterCreateConflict(
        gateway: PocketBaseRecordGateway,
        localId: String,
        updatedAt: Long,
        body: JsonObject,
        existingJson: JsonObject,
        existing: Record,
        allowRemoteMerge: Boolean,
    ): PushResolution = when {
        recordUpdatedAt(existing) > updatedAt -> {
            resolveRemoteWinner(existing, allowRemoteMerge)
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
        else -> divergentRemoteResolution(allowRemoteMerge)
    }

    private suspend fun resolveRemoteWinner(
        record: Record,
        allowRemoteMerge: Boolean,
    ): PushResolution {
        if (!allowRemoteMerge) {
            throw AuthoritativeSeedConflictException(
                "Authoritative seed rejected a newer remote $collectionName row",
            )
        }
        mergeRemoteIfNewer(toEntity(record))
        return PushResolution.RemoteWon
    }

    private fun divergentRemoteResolution(allowRemoteMerge: Boolean): PushResolution {
        if (!allowRemoteMerge) {
            throw AuthoritativeSeedConflictException(
                "Authoritative seed found an equal-timestamp divergent $collectionName row",
            )
        }
        return PushResolution.Failed
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
                log.w { "Skipped mark-synced for $collectionName because the local row changed during push" }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.w { "Failed to mark a $collectionName record as synced; it will be retried" }
        }
    }

    private suspend fun updatePbIdSafely(
        localId: String,
        pbId: String
    ) {
        try {
            updatePbId(localId, pbId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.e { "Failed to save the remote id for a $collectionName record" }
        }
    }

    private enum class PushResolution { Pushed, RemoteWon, Failed }

    private companion object {
        const val PAGE_SIZE = 200
    }
}

internal fun JsonElement.canonicalFingerprintText(): String = when (this) {
    is JsonObject -> entries.sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "$key:${value.canonicalFingerprintText()}"
        }
    is JsonArray -> joinToString(prefix = "[", postfix = "]") { it.canonicalFingerprintText() }
    else -> toString()
}
