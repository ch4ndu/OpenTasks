package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.lighthousegames.logging.logging

private val log = logging("SyncAdapter")

abstract class BaseSyncAdapter<Entity, Record : BaseModel> {

    /** PocketBase collection name (e.g., "tasks", "categories"). */
    abstract val collectionName: String

    /** Push ordering -- lower values are pushed first. Categories should be 0 (pushed before tasks). */
    open val order: Int = 10

    // DAO operations
    abstract suspend fun getUnsynced(): List<Entity>
    abstract suspend fun getAllOnce(): List<Entity>
    abstract suspend fun getById(localId: String): Entity?
    abstract suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean): Int
    abstract suspend fun updatePbId(localId: String, pbId: String)
    abstract suspend fun markUnsynced(localId: String)
    abstract suspend fun hardDeleteLocalNeverSynced(entity: Entity)
    abstract suspend fun upsert(entity: Entity)

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

    // PocketBase operations (concrete adapters provide reified type wrappers)
    abstract suspend fun fetchAllRecords(client: PocketbaseClient): List<Record>
    abstract suspend fun verifyCollection(client: PocketbaseClient)
    abstract suspend fun createRecord(client: PocketbaseClient, body: String): Record
    abstract suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String): Record
    abstract suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): Record?

    /** Serialize entity to JSON body string for PocketBase. */
    abstract fun toJsonBody(entity: Entity): String

    /** Push all unsynced entities to server. */
    suspend fun pushAll(client: PocketbaseClient) {
        val unsynced = getUnsynced()
        val failures = mutableListOf<Throwable>()
        log.d { "Pushing ${unsynced.size} $collectionName" }
        for (entity in unsynced) {
            try {
                val entityLocalId = localId(entity)
                val entityPbId = pbId(entity)
                val entityUpdatedAt = updatedAt(entity)
                val entityIsDeleted = isDeleted(entity)

                if (entityIsDeleted && entityPbId == null) {
                    val toDelete = getById(entityLocalId)
                    if (toDelete != null) hardDeleteLocalNeverSynced(toDelete)
                    continue
                }

                val body = stripId(toJsonBody(entity))

                if (entityPbId != null) {
                    val updated = updateByPbIdOrRecover(client, entityLocalId, entityPbId, body)
                    if (updated) {
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                    } else {
                        failures += SyncAdapterException("Failed to update $collectionName $entityLocalId")
                    }
                } else {
                    val created = runCatching { createRecord(client, body) }
                    if (created.isSuccess) {
                        val newPbId = created.getOrNull()?.id
                        if (newPbId != null) {
                            try {
                                updatePbId(entityLocalId, newPbId)
                            } catch (e: Exception) {
                                log.e(e) { "Failed to save pbId for $collectionName $entityLocalId" }
                            }
                        }
                        markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                    } else {
                        val error = created.exceptionOrNull()
                        if (error != null) {
                            log.e(error) { "Failed to create $collectionName $entityLocalId" }
                            val recovered = recoverCreateFailureByLocalId(
                                client = client,
                                localId = entityLocalId,
                                updatedAt = entityUpdatedAt,
                                body = body,
                            )
                            if (recovered) {
                                markSyncedAfterPush(entityLocalId, entityUpdatedAt, entityIsDeleted)
                            } else {
                                failures += error
                            }
                        }
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

    /** Pull all records from server and merge locally. */
    suspend fun pullAll(client: PocketbaseClient) {
        try {
            val remoteRecords = fetchAllRecords(client)
            log.d { "Pulled ${remoteRecords.size} $collectionName" }

            val localSyncedSnapshot = getAllOnce().filter { isSynced(it) && !isDeleted(it) }

            for (record in remoteRecords) {
                val rLocalId = recordLocalId(record)
                val local = getById(rLocalId)

                if (local == null || recordUpdatedAt(record) > updatedAt(local)) {
                    upsert(toEntity(record))
                }
            }

            val remoteIds = remoteRecords.map { recordLocalId(it) }.toSet()
            if (remoteRecords.isEmpty() && localSyncedSnapshot.isNotEmpty()) {
                log.w { "Skipping $collectionName missing-row recovery: server returned 0 records but ${localSyncedSnapshot.size} local synced exist -- possible empty response" }
            } else if (remoteRecords.size < localSyncedSnapshot.size * 0.1 && localSyncedSnapshot.isNotEmpty()) {
                log.w { "Skipping $collectionName missing-row recovery: server returned ${remoteRecords.size} records but ${localSyncedSnapshot.size} local synced exist -- possible partial response" }
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
    ): Boolean {
        val updated = runCatching { updateRecord(client, pbId, body) }
        if (updated.isSuccess) return true

        val error = updated.exceptionOrNull()
        if (!error.isNotFound()) {
            if (error != null) log.e(error) { "Failed to update $collectionName $localId" }
            return false
        }

        log.w { "Stale pbId for $collectionName $localId; looking up by localId" }
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure { log.e(it) { "Failed localId lookup for $collectionName $localId" } }
            .getOrNull()
        val serverId = existing?.id ?: return runCatching { createRecord(client, body) }
            .onSuccess { created ->
                val newPbId = created.id
                if (newPbId != null) updatePbIdSafely(localId, newPbId)
            }
            .onFailure { log.e(it) { "Failed to recreate missing $collectionName $localId" } }
            .isSuccess

        updatePbIdSafely(localId, serverId)
        return runCatching { updateRecord(client, serverId, body) }
            .onFailure { log.e(it) { "Failed to update recovered $collectionName $localId" } }
            .isSuccess
    }

    private suspend fun recoverCreateFailureByLocalId(
        client: PocketbaseClient,
        localId: String,
        updatedAt: Long,
        body: String,
    ): Boolean {
        log.w { "Create failed for $collectionName $localId; looking up existing server row by localId" }
        val existing = runCatching { findRecordByLocalId(client, localId) }
            .onFailure { log.e(it) { "Failed localId lookup after create failure for $collectionName $localId" } }
            .getOrNull()
            ?: return false
        val serverId = existing.id ?: return false

        updatePbIdSafely(localId, serverId)
        if (recordUpdatedAt(existing) > updatedAt) {
            upsert(toEntity(existing))
            return true
        }

        return runCatching { updateRecord(client, serverId, body) }
            .onFailure { log.e(it) { "Failed to update existing $collectionName $localId after create conflict" } }
            .isSuccess
    }

    private suspend fun markSyncedAfterPush(localId: String, updatedAt: Long, isDeleted: Boolean) {
        try {
            val changed = markSyncedIfUnchanged(localId, updatedAt, isDeleted)
            if (changed == 0) {
                log.w { "Skipped markSynced for $collectionName $localId: local row changed during push" }
            }
        } catch (e: Exception) {
            log.w(e) { "Failed to markSynced for $collectionName $localId (will retry)" }
        }
    }

    private suspend fun updatePbIdSafely(localId: String, pbId: String) {
        try {
            updatePbId(localId, pbId)
        } catch (e: Exception) {
            log.e(e) { "Failed to save pbId for $collectionName $localId" }
        }
    }

    private fun Throwable?.isNotFound(): Boolean =
        this?.message?.contains(": 404") == true
}
