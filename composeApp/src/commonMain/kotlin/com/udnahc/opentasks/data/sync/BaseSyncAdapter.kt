package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import com.udnahc.opentasks.data.extensions.utcNow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("SyncAdapter")

abstract class BaseSyncAdapter<Entity, Record : BaseModel> {

    protected val gatewayJson = Json { ignoreUnknownKeys = true }

    /** PocketBase collection name (e.g., "tasks", "categories"). */
    abstract val collectionName: String

    /** Complete application payload contract for production JSON records. */
    protected open val recordPayloadSchema: SyncRecordPayloadSchema? = null

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
     * Copy only sync metadata during a normal equal-timestamp rebase.  Each
     * adapter keeps its complete local entity, including local-only fields.
     */
    protected open fun copyForSyncRecovery(
        entity: Entity,
        remoteId: String,
        updatedAt: Long,
    ): Entity = error("$collectionName does not provide a sync recovery copy")
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
            val body = runCatching {
                val parsed = Json.parseToJsonElement(stripId(toJsonBody(entity)))
                val payload = parsed as? JsonObject
                    ?: throw SyncRecordPayloadException("Local $collectionName payload is not a JSON object")
                recordPayloadSchema?.project(payload, requireId = false) ?: payload
            }
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
            rows += result.items.map { recordFromJson(validateRecordPayload(it)) }
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

                var pushPbId = entityPbId
                if (entityIsDeleted && entityPbId == null) {
                    when (val lookup = lookupRemoteByLocalIdForTombstone(pass, entityLocalId)) {
                        is TombstoneRemoteLookup.Found -> {
                            if (!adoptRemoteIdentityForTombstoneIfUnchanged(
                                    pass,
                                    entityLocalId,
                                    entityUpdatedAt,
                                    lookup.remoteId,
                                )
                            ) {
                                continue
                            }
                            pushPbId = lookup.remoteId
                        }
                        TombstoneRemoteLookup.Absent -> {
                            when (hardDeleteLocalNeverSyncedIfUnchanged(pass, entity)) {
                                HardDeleteDecision.Deleted,
                                HardDeleteDecision.Changed,
                                -> continue
                                HardDeleteDecision.Protected -> Unit
                            }
                        }
                    }
                }

                val body = stripId(toJsonBody(entity))

                if (pushPbId != null) {
                    val initial = updateByPbIdOrRecover(
                        pass,
                        entityLocalId,
                        pushPbId,
                        body,
                        allowConflictRecovery = pass.allowNormalConflictRecovery,
                    )
                    val result = when (initial) {
                        is PushResolution.Conflict -> retryAfterEqualConflict(pass, initial)
                        else -> initial
                    }
                    when (result) {
                        PushResolution.Pushed ->
                            markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                        is PushResolution.RebasedPushed ->
                            markSyncedAfterPush(entityLocalId, result.updatedAt, result.isDeleted)
                        PushResolution.RemoteWon -> Unit
                        PushResolution.Failed,
                        is PushResolution.Conflict,
                        -> failures += SyncAdapterException("Failed to update a $collectionName record")
                    }
                } else {
                    val initial = createOrRecover(
                        pass,
                        entityLocalId,
                        entityUpdatedAt,
                        body,
                        allowConflictRecovery = pass.allowNormalConflictRecovery,
                    )
                    val result = when (initial) {
                        is PushResolution.Conflict -> retryAfterEqualConflict(pass, initial)
                        else -> initial
                    }
                    when (result) {
                        PushResolution.Pushed -> {
                            markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                        }
                        is PushResolution.RebasedPushed ->
                            markSyncedAfterPush(entityLocalId, result.updatedAt, result.isDeleted)
                        PushResolution.RemoteWon -> Unit
                        PushResolution.Failed,
                        is PushResolution.Conflict,
                        -> failures += SyncAdapterException("Failed to create a $collectionName record")
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

    private suspend fun lookupRemoteByLocalIdForTombstone(
        pass: SyncPassContext,
        localId: String,
    ): TombstoneRemoteLookup {
        if (localId.isBlank()) {
            throw SyncAdapterException("Cannot recover a $collectionName tombstone with a blank local id")
        }
        if (allowsTestOnlyLegacySdkWrites()) {
            return findRecordByLocalId(pass.client, localId)?.let { record ->
                TombstoneRemoteLookup.Found(validatedRemoteId(record, localId))
            } ?: TombstoneRemoteLookup.Absent
        }

        val response = requirePassGateway(pass).findByLocalId(collectionName, localId)
        if (response.status.value == 401) throw SyncAuthenticationRejectedException()
        if (!response.isSuccess) {
            throw SyncAdapterException(
                "Unable to look up the $collectionName tombstone by local id (HTTP ${response.status.value})",
            )
        }
        val json = response.body ?: return TombstoneRemoteLookup.Absent
        val record = runCatching { recordFromJson(validateRecordPayload(json)) }.getOrElse { error ->
            throw SyncAdapterException("Unable to decode the recovered $collectionName record", error)
        }
        return TombstoneRemoteLookup.Found(validatedRemoteId(record, localId))
    }

    private fun validatedRemoteId(record: Record, expectedLocalId: String): String {
        if (recordLocalId(record) != expectedLocalId) {
            throw SyncAdapterException("Recovered $collectionName record has a mismatched local id")
        }
        return record.id?.trim()?.takeIf(String::isNotBlank)
            ?: throw SyncAdapterException("Recovered $collectionName record has no remote id")
    }

    private suspend fun adoptRemoteIdentityForTombstoneIfUnchanged(
        pass: SyncPassContext,
        localId: String,
        updatedAt: Long,
        remoteId: String,
    ): Boolean {
        var adopted = false
        pass.runWriterTransaction {
            val current = getById(localId)
            if (current == null ||
                !isDeleted(current) ||
                isSynced(current) ||
                pbId(current) != null ||
                updatedAt(current) != updatedAt
            ) {
                return@runWriterTransaction
            }
            updatePbId(localId, remoteId)
            adopted = getById(localId)?.let {
                pbId(it) == remoteId &&
                    isDeleted(it) &&
                    !isSynced(it) &&
                    updatedAt(it) == updatedAt
            } == true
        }
        return adopted
    }

    private suspend fun hardDeleteLocalNeverSyncedIfUnchanged(
        pass: SyncPassContext,
        entity: Entity,
    ): HardDeleteDecision {
        val expectedLocalId = localId(entity)
        val expectedUpdatedAt = updatedAt(entity)
        var decision = HardDeleteDecision.Changed
        pass.runWriterTransaction {
            val current = getById(expectedLocalId)
            when {
                current == null ||
                    !isDeleted(current) ||
                    isSynced(current) ||
                    pbId(current) != null ||
                    updatedAt(current) != expectedUpdatedAt -> Unit
                !shouldHardDeleteLocalNeverSynced(current) -> decision = HardDeleteDecision.Protected
                else -> {
                    hardDeleteLocalNeverSynced(current)
                    if (getById(expectedLocalId) == null) decision = HardDeleteDecision.Deleted
                }
            }
        }
        return decision
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
                    updateByPbIdOrRecover(
                        pass,
                        id,
                        it,
                        body,
                        allowRemoteMerge = allowRemoteMerge,
                        allowConflictRecovery = false,
                    )
                } ?: createOrRecover(
                    pass,
                    id,
                    timestamp,
                    body,
                    allowRemoteMerge = allowRemoteMerge,
                    allowConflictRecovery = false,
                )
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
            val record = runCatching { recordFromJson(validateRecordPayload(row)) }.getOrElse { return false }
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
            log.w { "Pull $collectionName rejected by sync consistency checks" }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.rethrowSyncAuthenticationRejected()
            log.e { "Failed to pull $collectionName" }
            throw SyncAdapterException("Failed to pull $collectionName", e)
        }
    }

    /** Validate the raw response before record defaults or enum fallbacks can hide a defect. */
    protected fun validateRecordPayload(
        json: JsonObject,
        requireId: Boolean = true,
    ): JsonObject {
        val schema = recordPayloadSchema ?: return json
        return try {
            schema.project(json, requireId = requireId)
            json
        } catch (error: SyncRecordPayloadException) {
            log.w { "Rejected raw $collectionName record payload: ${error.message.orEmpty()}" }
            throw SyncAdapterException("Invalid $collectionName record payload", error)
        }
    }

    private fun normalizedPayload(
        json: JsonObject,
        requireId: Boolean,
        ignoredFields: Set<String> = emptySet(),
    ): JsonObject? = runCatching {
        recordPayloadSchema?.project(json, requireId, ignoredFields)
            ?: JsonObject(json.filterKeys { it !in setOf("id", "account", "collectionId", "collectionName", "created", "updated") })
    }.getOrNull()

    private fun validateWriteAcknowledgement(
        response: JsonObject?,
        expectedLocalId: String,
        expectedBody: JsonObject,
        expectedUpdateId: String? = null,
        ignoredFields: Set<String> = emptySet(),
    ): Boolean {
        val returned = response ?: return false
        val returnedId = returned["id"]?.let { element ->
            (element as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeUnless { it is kotlinx.serialization.json.JsonNull || !it.isString }
                ?.content
        }?.trim().orEmpty()
        if (returnedId.isBlank() || expectedUpdateId?.let { returnedId != it } == true) return false
        val expected = normalizedPayload(expectedBody, requireId = false, ignoredFields) ?: return false
        val actual = normalizedPayload(returned, requireId = true, ignoredFields) ?: return false
        return actual["localId"] == expected["localId"] &&
            actual["localId"] == kotlinx.serialization.json.JsonPrimitive(expectedLocalId) &&
            actual == expected
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
     * equal timestamp already exists. The enabled normal pass rebases a
     * divergent equal timestamp locally; initial and seed paths remain strict.
     */
    private suspend fun mergeRemoteAndValidate(
        pass: SyncPassContext,
        record: Record,
    ): RemoteMergeResult {
        val remote = toEntity(record)
        val remoteBody = rawRemotePayload(record, remote)
        var result: RemoteMergeResult? = null
        pass.runWriterTransaction {
            val beforeMerge = getById(recordLocalId(record))
            if (pass.allowNormalConflictRecovery &&
                beforeMerge != null &&
                updatedAt(beforeMerge) == recordUpdatedAt(record) &&
                !canonicalPayloadEquals(
                    normalizedBody(beforeMerge)
                        ?: throw SyncDegradedException("Local $collectionName record is not a JSON object"),
                    remoteBody,
                )
            ) {
                val remoteId = record.id?.trim()?.takeIf(String::isNotBlank)
                    ?: throw SyncDegradedException(
                        "$collectionName equal-timestamp conflict has no validated remote id",
                    )
                rebaseLocalAfterEqualConflict(
                    beforeMerge,
                    recordLocalId(record),
                    remoteBody,
                    remoteId,
                    recordUpdatedAt(record),
                )
                result = RemoteMergeResult.KeptLocal
                return@runWriterTransaction
            }
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
                !canonicalPayloadEquals(
                    normalizedBody(current)
                        ?: throw SyncDegradedException("Local $collectionName record is not a JSON object"),
                    remoteBody,
                ) -> {
                    val localBody = normalizedBody(current)
                    val differingFields = (localBody?.keys.orEmpty() + remoteBody?.keys.orEmpty()).filter { field ->
                        localBody?.get(field) != remoteBody?.get(field)
                    }.sorted()
                    log.w {
                        "Pull $collectionName conflict: reason=equal_timestamp_divergence " +
                            "localId=${recordLocalId(record)} " +
                            "localSynced=${isSynced(current)} fields=${differingFields.joinToString(",")} " +
                            "omittedLocally=${differingFields.filter { it !in localBody?.keys.orEmpty() }.joinToString(",")} " +
                            "omittedRemotely=${differingFields.filter { it !in remoteBody?.keys.orEmpty() }.joinToString(",")}"
                    }
                    throw SyncDegradedException(
                        "$collectionName record has an equal-timestamp divergent payload",
                    )
                }
            }
        }
        return checkNotNull(result) { "Sync merge did not produce a result" }
    }

    private fun rawRemotePayload(record: Record, remote: Entity): JsonObject {
        val body = Json.parseToJsonElement(stripId(toJsonBody(remote))) as? JsonObject
            ?: throw SyncDegradedException("Remote $collectionName record is not a JSON object")
        val remoteId = record.id?.trim()?.takeIf(String::isNotBlank)
            ?: throw SyncDegradedException("Remote $collectionName record has no id")
        return buildJsonObject {
            put("id", JsonPrimitive(remoteId))
            body.forEach { (key, value) -> put(key, value) }
        }
    }

    /** Rebase the current local row without round-tripping it through a record DTO. */
    protected suspend fun recoverLocalAfterEqualConflict(
        pass: SyncPassContext,
        localId: String,
        remote: JsonObject,
        remoteId: String,
        remoteUpdatedAt: Long,
    ): Entity? {
        if (!pass.allowNormalConflictRecovery) return null
        var recovered: Entity? = null
        pass.runWriterTransaction {
            val current = getById(localId) ?: return@runWriterTransaction
            recovered = rebaseLocalAfterEqualConflict(
                current,
                localId,
                remote,
                remoteId,
                remoteUpdatedAt,
            )
        }
        return recovered
    }

    private suspend fun rebaseLocalAfterEqualConflict(
        current: Entity,
        expectedLocalId: String,
        remote: JsonObject,
        remoteId: String,
        remoteUpdatedAt: Long,
    ): Entity? {
        if (localId(current) != expectedLocalId || updatedAt(current) != remoteUpdatedAt) return null
        if (remoteId.isBlank() || canonicalPayloadEquals(normalizedBody(current) ?: return null, remote)) {
            return null
        }
        val currentUpdatedAt = updatedAt(current)
        if (currentUpdatedAt == Long.MAX_VALUE) {
            throw SyncDegradedException("Cannot advance $collectionName after Long.MAX_VALUE")
        }
        val nextUpdatedAt = maxOf(utcNow(), currentUpdatedAt + 1L)
        val rebased = copyForSyncRecovery(current, remoteId, nextUpdatedAt)
        upsert(rebased)
        val persisted = getById(expectedLocalId)
        if (persisted == null ||
            localId(persisted) != expectedLocalId ||
            pbId(persisted) != remoteId ||
            isSynced(persisted) ||
            updatedAt(persisted) != nextUpdatedAt ||
            persisted != rebased
        ) {
            throw SyncAdapterException("$collectionName equal-timestamp recovery was not persisted")
        }
        log.d { "Recovered equal-timestamp $collectionName row localId=$expectedLocalId" }
        return persisted
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
        allowConflictRecovery: Boolean = true,
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
            allowConflictRecovery,
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
        allowConflictRecovery: Boolean = true,
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
            allowConflictRecovery,
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
        allowConflictRecovery: Boolean,
    ): PushResolution {
        val bodyObject = Json.parseToJsonElement(body) as? JsonObject ?: return PushResolution.Failed
        if (normalizedPayload(bodyObject, requireId = false) == null) return PushResolution.Failed
        val preflight = gateway.getRecord(collectionName, pbId)
        if (preflight.isSuccess) {
            val existing = preflight.body ?: return PushResolution.Failed
            if (existing["id"]?.toString()?.trim('"') != pbId) return PushResolution.Failed
            val record = runCatching { recordFromJson(validateRecordPayload(existing)) }
                .getOrElse { return PushResolution.Failed }
            if (recordLocalId(record) != localId) return PushResolution.Failed
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
                    if (updated.isSuccess && validateWriteAcknowledgement(
                            updated.body,
                            expectedLocalId = localId,
                            expectedBody = bodyObject,
                            expectedUpdateId = pbId,
                        )
                    ) {
                        updatePbIdSafely(localId, pbId)
                        PushResolution.Pushed
                    } else {
                        if (updated.isSuccess) PushResolution.Failed else {
                            resolveRejectedGuardedWrite(
                                gateway,
                                localId,
                                bodyObject,
                                updated,
                                allowRemoteMerge,
                                allowConflictRecovery,
                            )
                        }
                    }
                }
                else -> divergentRemoteResolution(
                    allowRemoteMerge = allowRemoteMerge,
                    allowConflictRecovery = allowConflictRecovery,
                    conflict = ValidatedSyncConflict(
                        localId = localId,
                        remote = existing,
                        remoteId = pbId,
                        remoteUpdatedAt = recordUpdatedAt(record),
                    ),
                )
            }
        }
        if (!preflight.isNotFound) return PushResolution.Failed
        return resolveRejectedGuardedWrite(
            gateway,
            localId,
            bodyObject,
            preflight,
            allowRemoteMerge,
            allowConflictRecovery,
        )
    }

    private suspend fun guardedCreateOrRecover(
        gateway: PocketBaseRecordGateway,
        localId: String,
        updatedAt: Long,
        body: String,
        allowRemoteMerge: Boolean,
        allowConflictRecovery: Boolean,
    ): PushResolution {
        val bodyObject = Json.parseToJsonElement(body) as? JsonObject ?: return PushResolution.Failed
        if (normalizedPayload(bodyObject, requireId = false) == null) return PushResolution.Failed
        val created = gateway.createJson(collectionName, bodyObject)
        if (created.isSuccess && validateWriteAcknowledgement(
                created.body,
                expectedLocalId = localId,
                expectedBody = bodyObject,
            )
        ) {
            val createdId = created.body?.get("id")?.toString()?.trim('"')
                ?.takeIf(String::isNotBlank)
                ?: return PushResolution.Failed
            updatePbIdSafely(localId, createdId)
            return PushResolution.Pushed
        }
        val lookup = gateway.findByLocalId(collectionName, localId)
        val existing = lookup.body ?: return PushResolution.Failed
        val record = runCatching { recordFromJson(validateRecordPayload(existing)) }
            .getOrElse { return PushResolution.Failed }
        if (recordLocalId(record) != localId) return PushResolution.Failed
        return resolveExistingAfterCreateConflict(
            gateway,
            localId,
            updatedAt,
            bodyObject,
            existing,
            record,
            allowRemoteMerge,
            allowConflictRecovery,
        )
    }

    private suspend fun resolveRejectedGuardedWrite(
        gateway: PocketBaseRecordGateway,
        localId: String,
        body: JsonObject,
        rejected: GatewayResponse<JsonObject>,
        allowRemoteMerge: Boolean,
        allowConflictRecovery: Boolean,
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
                allowConflictRecovery,
            )
        } else {
            log.w { "Guarded $collectionName update rejected with HTTP ${rejected.status.value}; lookup failed with HTTP ${lookup.status.value}" }
            PushResolution.Failed
        }
        val record = runCatching { recordFromJson(validateRecordPayload(existing)) }
            .getOrElse { return PushResolution.Failed }
        val recoveredPbId = existing["id"]?.toString()?.trim('"')
            ?.takeIf(String::isNotBlank)
            ?: return PushResolution.Failed
        if (recordLocalId(record) != localId) return PushResolution.Failed
        val localUpdatedAt = body["localUpdatedAt"]?.toString()?.trim('"')?.toLongOrNull() ?: 0L
        return when {
            recordUpdatedAt(record) > localUpdatedAt -> {
                resolveRemoteWinner(record, allowRemoteMerge)
            }
            recordUpdatedAt(record) == localUpdatedAt && canonicalPayloadEquals(body, existing) -> {
                updatePbIdSafely(localId, recoveredPbId)
                PushResolution.Pushed
            }
            recordUpdatedAt(record) < localUpdatedAt -> {
                val updated = gateway.updateJson(collectionName, recoveredPbId, body)
                if (updated.isSuccess && validateWriteAcknowledgement(
                        updated.body,
                        expectedLocalId = localId,
                        expectedBody = body,
                        expectedUpdateId = recoveredPbId,
                    )
                ) {
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
            else -> divergentRemoteResolution(
                allowRemoteMerge = allowRemoteMerge,
                allowConflictRecovery = allowConflictRecovery,
                conflict = ValidatedSyncConflict(
                    localId = localId,
                    remote = existing,
                    remoteId = recoveredPbId,
                    remoteUpdatedAt = recordUpdatedAt(record),
                ),
            )
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
        allowConflictRecovery: Boolean,
    ): PushResolution = when {
        recordUpdatedAt(existing) > updatedAt -> {
            resolveRemoteWinner(existing, allowRemoteMerge)
        }
        recordUpdatedAt(existing) == updatedAt && canonicalPayloadEquals(body, existingJson) -> {
            val existingId = existingJson["id"]?.toString()?.trim('"')
                ?.takeIf(String::isNotBlank)
                ?: return PushResolution.Failed
            updatePbIdSafely(localId, existingId)
            PushResolution.Pushed
        }
        recordUpdatedAt(existing) < updatedAt -> {
            val existingId = existingJson["id"]?.toString()?.trim('"')
                ?.takeIf(String::isNotBlank)
                ?: return PushResolution.Failed
            val updated = gateway.updateJson(collectionName, existingId, body)
            if (updated.isSuccess && validateWriteAcknowledgement(
                    updated.body,
                    expectedLocalId = localId,
                    expectedBody = body,
                    expectedUpdateId = existingId,
                )
            ) {
                updatePbIdSafely(localId, existingId)
                PushResolution.Pushed
            } else {
                PushResolution.Failed
            }
        }
        else -> {
            val existingId = existingJson["id"]?.toString()?.trim('"')
                ?.takeIf(String::isNotBlank)
                ?: return PushResolution.Failed
            divergentRemoteResolution(
                allowRemoteMerge = allowRemoteMerge,
                allowConflictRecovery = allowConflictRecovery,
                conflict = ValidatedSyncConflict(
                    localId = localId,
                    remote = existingJson,
                    remoteId = existingId,
                    remoteUpdatedAt = recordUpdatedAt(existing),
                ),
            )
        }
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
        // A parent rejection or newer local write must remain pending instead of reporting a remote win.
        return when (mergeRemoteIfNewer(toEntity(record))) {
            RemoteMergeResult.Applied -> PushResolution.RemoteWon
            RemoteMergeResult.KeptLocal,
            RemoteMergeResult.MissingParent,
            -> PushResolution.Failed
        }
    }

    private suspend fun retryAfterEqualConflict(
        pass: SyncPassContext,
        conflict: PushResolution.Conflict,
    ): PushResolution {
        if (!pass.allowNormalConflictRecovery) return PushResolution.Failed
        recoverLocalAfterEqualConflict(
            pass = pass,
            localId = conflict.remote.localId,
            remote = conflict.remote.remote,
            remoteId = conflict.remote.remoteId,
            remoteUpdatedAt = conflict.remote.remoteUpdatedAt,
        ) ?: return PushResolution.Failed
        val current = getById(conflict.remote.localId) ?: return PushResolution.Failed
        val currentPbId = pbId(current) ?: return PushResolution.Failed
        val body = stripId(toJsonBody(current))
        return when (
            val retry = updateByPbIdOrRecover(
                pass = pass,
                localId = conflict.remote.localId,
                pbId = currentPbId,
                body = body,
                allowRemoteMerge = true,
                allowConflictRecovery = false,
            )
        ) {
            PushResolution.Pushed -> PushResolution.RebasedPushed(
                updatedAt = updatedAt(current),
                isDeleted = isDeleted(current),
            )
            PushResolution.RemoteWon -> PushResolution.RemoteWon
            PushResolution.Failed,
            is PushResolution.Conflict,
            is PushResolution.RebasedPushed,
            -> PushResolution.Failed
        }
    }

    private fun divergentRemoteResolution(
        allowRemoteMerge: Boolean,
        allowConflictRecovery: Boolean = false,
        conflict: ValidatedSyncConflict? = null,
    ): PushResolution {
        if (!allowRemoteMerge) {
            throw AuthoritativeSeedConflictException(
                "Authoritative seed found an equal-timestamp divergent $collectionName row",
            )
        }
        if (allowConflictRecovery && conflict != null) return PushResolution.Conflict(conflict)
        return PushResolution.Failed
    }

    protected open fun canonicalPayloadEquals(local: JsonObject, remote: JsonObject): Boolean {
        val localPayload = normalizedPayload(local, requireId = false) ?: return false
        val remotePayload = normalizedPayload(remote, requireId = true) ?: return false
        return localPayload == remotePayload
    }

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
            throw SyncAdapterException("Failed to mark a $collectionName record as synced", e)
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
            throw SyncAdapterException("Failed to save the remote id for a $collectionName record", e)
        }
    }

    private sealed interface TombstoneRemoteLookup {
        data object Absent : TombstoneRemoteLookup
        data class Found(val remoteId: String) : TombstoneRemoteLookup
    }

    private enum class HardDeleteDecision { Deleted, Protected, Changed }

    private sealed interface PushResolution {
        data object Pushed : PushResolution
        data object RemoteWon : PushResolution
        data object Failed : PushResolution
        data class Conflict(val remote: ValidatedSyncConflict) : PushResolution
        data class RebasedPushed(val updatedAt: Long, val isDeleted: Boolean) : PushResolution
    }

    private data class ValidatedSyncConflict(
        val localId: String,
        val remote: JsonObject,
        val remoteId: String,
        val remoteUpdatedAt: Long,
    )

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
