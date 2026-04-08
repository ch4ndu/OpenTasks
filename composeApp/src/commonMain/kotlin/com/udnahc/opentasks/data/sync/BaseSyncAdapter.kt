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
    abstract suspend fun markSynced(localId: String)
    abstract suspend fun updatePbId(localId: String, pbId: String)
    abstract suspend fun deleteEntity(entity: Entity)
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
    abstract suspend fun createRecord(client: PocketbaseClient, body: String): Record
    abstract suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String): Record
    abstract suspend fun deleteRecord(client: PocketbaseClient, pbId: String): Boolean
    abstract suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): Record?

    /** Serialize entity to JSON body string for PocketBase. */
    abstract fun toJsonBody(entity: Entity): String

    /** Push all unsynced entities to server. */
    suspend fun pushAll(client: PocketbaseClient) {
        val unsynced = getUnsynced()
        log.d { "Pushing ${unsynced.size} $collectionName" }
        for (entity in unsynced) {
            try {
                val entityLocalId = localId(entity)
                val entityPbId = pbId(entity)

                if (isDeleted(entity)) {
                    if (entityPbId != null) {
                        val deleted = runCatching { deleteRecord(client, entityPbId) }
                        if (deleted.isSuccess) {
                            val toDelete = getById(entityLocalId)
                            if (toDelete != null) deleteEntity(toDelete)
                        }
                    } else {
                        // Never synced, just remove locally
                        val toDelete = getById(entityLocalId)
                        if (toDelete != null) deleteEntity(toDelete)
                    }
                    continue
                }

                val body = stripId(toJsonBody(entity))

                if (entityPbId != null) {
                    updateRecord(client, entityPbId, body)
                    markSynced(entityLocalId)
                } else {
                    val created = runCatching { createRecord(client, body) }
                    if (created.isSuccess) {
                        val newPbId = created.getOrNull()?.id
                        if (newPbId != null) {
                            try {
                                updatePbId(entityLocalId, newPbId)
                            } catch (e: Exception) {
                                log.e { "Failed to save pbId for $collectionName $entityLocalId: ${e.message}" }
                            }
                        }
                        try {
                            markSynced(entityLocalId)
                        } catch (e: Exception) {
                            log.w { "Failed to markSynced for $collectionName $entityLocalId (will retry): ${e.message}" }
                        }
                    } else {
                        // Create failed -- likely duplicate localId. Look up existing record.
                        log.w { "Create failed for $collectionName, looking up by localId: ${created.exceptionOrNull()?.message}" }
                        val existing = findRecordByLocalId(client, entityLocalId)
                        val serverId = existing?.id
                        if (serverId != null) {
                            try {
                                updatePbId(entityLocalId, serverId)
                            } catch (e: Exception) {
                                log.e { "Failed to save pbId for $collectionName $entityLocalId: ${e.message}" }
                            }
                            updateRecord(client, serverId, body)
                            try {
                                markSynced(entityLocalId)
                            } catch (e: Exception) {
                                log.w { "Failed to markSynced for $collectionName $entityLocalId (will retry): ${e.message}" }
                            }
                        } else {
                            log.e { "Failed to push $collectionName $entityLocalId: ${created.exceptionOrNull()?.message}" }
                        }
                    }
                }
            } catch (e: Exception) {
                log.e { "Failed to push $collectionName ${localId(entity)}: ${e.message}" }
            }
        }
    }

    /** Pull all records from server and merge locally. */
    suspend fun pullAll(client: PocketbaseClient) {
        try {
            val remoteRecords = fetchAllRecords(client)
            log.d { "Pulled ${remoteRecords.size} $collectionName" }

            // Snapshot local synced records BEFORE processing remote changes
            val localSyncedSnapshot = getAllOnce().filter { isSynced(it) && !isDeleted(it) }

            for (record in remoteRecords) {
                val rLocalId = recordLocalId(record)
                val local = getById(rLocalId)

                if (recordIsDeleted(record)) {
                    if (local != null) deleteEntity(local)
                } else if (local == null || recordUpdatedAt(record) > updatedAt(local)) {
                    upsert(toEntity(record))
                }
            }

            // Cleanup: remove local synced records not found on server (using pre-upsert snapshot)
            val remoteIds = remoteRecords.filter { !recordIsDeleted(it) }.map { recordLocalId(it) }.toSet()
            if (remoteRecords.isEmpty() && localSyncedSnapshot.isNotEmpty()) {
                log.w { "Skipping $collectionName cleanup: server returned 0 records but ${localSyncedSnapshot.size} local synced exist -- possible empty response" }
            } else if (remoteRecords.size < localSyncedSnapshot.size * 0.1 && localSyncedSnapshot.isNotEmpty()) {
                log.w { "Skipping $collectionName cleanup: server returned ${remoteRecords.size} records but ${localSyncedSnapshot.size} local synced exist -- possible partial response" }
            } else {
                for (local in localSyncedSnapshot) {
                    if (localId(local) !in remoteIds) {
                        try {
                            log.d { "Cleanup deleting $collectionName ${localId(local)}: not found on server" }
                            deleteEntity(local)
                        } catch (e: Exception) {
                            log.e { "Failed to cleanup-delete $collectionName ${localId(local)}: ${e.message}" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.e { "Failed to pull $collectionName: ${e.message}" }
        }
    }

    /** Remove the PocketBase auto-assigned "id" field from JSON to avoid pk_change errors. */
    private fun stripId(jsonString: String): String {
        val obj = Json.parseToJsonElement(jsonString) as? JsonObject ?: return jsonString
        return buildJsonObject {
            obj.forEach { (key, value) -> if (key != "id") put(key, value) }
        }.toString()
    }
}
