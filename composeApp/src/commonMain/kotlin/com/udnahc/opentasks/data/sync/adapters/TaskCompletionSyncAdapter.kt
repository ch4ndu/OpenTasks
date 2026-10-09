package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.TaskCompletionDao
import com.udnahc.opentasks.data.model.TaskCompletion
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import com.udnahc.opentasks.data.sync.PocketBaseFilter
import com.udnahc.opentasks.data.sync.taskCompletionRecordPayloadSchema
import com.udnahc.opentasks.data.sync.syncRecordJson
import com.udnahc.opentasks.data.sync.records.TaskCompletionRecord
import com.udnahc.opentasks.data.sync.records.toTaskCompletion
import com.udnahc.opentasks.data.sync.records.toTaskCompletionRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class TaskCompletionSyncAdapter(private val dao: TaskCompletionDao) : BaseSyncAdapter<TaskCompletion, TaskCompletionRecord>() {

    override val collectionName = "task_completions"
    override val order = 50
    override val recordPayloadSchema = taskCompletionRecordPayloadSchema

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllTaskCompletionsOnce()
    override suspend fun getById(localId: String) = dao.findTaskCompletionByIdAnyState(localId)
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        updatedAt: Long,
        isDeleted: Boolean
    ) =
        dao.markSyncedIfUnchanged(localId, updatedAt, isDeleted)

    override suspend fun updatePbId(
        localId: String,
        pbId: String
    ) = dao.updatePbId(localId, pbId)

    override suspend fun markUnsynced(localId: String) = dao.markUnsynced(localId)
    override suspend fun hardDeleteLocalNeverSynced(entity: TaskCompletion) = dao.delete(entity)
    override suspend fun upsert(entity: TaskCompletion) = dao.upsert(entity)
    override suspend fun mergeRemoteIfNewer(entity: TaskCompletion): RemoteMergeResult = dao.mergeRemoteIfNewer(entity)
    override fun copyForSyncRecovery(entity: TaskCompletion, remoteId: String, updatedAt: Long) =
        entity.copy(pbId = remoteId, updatedAt = updatedAt, isSynced = false)

    override fun localId(entity: TaskCompletion) = entity.id
    override fun pbId(entity: TaskCompletion) = entity.pbId
    override fun isDeleted(entity: TaskCompletion) = entity.isDeleted
    override fun isSynced(entity: TaskCompletion) = entity.isSynced
    override fun updatedAt(entity: TaskCompletion) = entity.updatedAt

    override fun recordLocalId(record: TaskCompletionRecord) = record.localId
    override fun recordIsDeleted(record: TaskCompletionRecord) = record.isDeleted
    override fun recordUpdatedAt(record: TaskCompletionRecord) = record.updatedAtUtc

    override fun toRecord(entity: TaskCompletion) = entity.toTaskCompletionRecord()
    override fun toEntity(record: TaskCompletionRecord) = record.toTaskCompletion()
    override fun recordFromJson(json: JsonObject): TaskCompletionRecord = gatewayJson.decodeFromJsonElement(json)
    override fun toJsonBody(entity: TaskCompletion) = syncRecordJson.encodeToString(entity.toTaskCompletionRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<TaskCompletionRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<TaskCompletionRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(
        client: PocketbaseClient,
        body: String
    ) =
        client.records.create<TaskCompletionRecord>(collectionName, body)

    override suspend fun updateRecord(
        client: PocketbaseClient,
        pbId: String,
        body: String
    ) =
        client.records.update<TaskCompletionRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(
        client: PocketbaseClient,
        localId: String
    ): TaskCompletionRecord? =
        client.records.getList<TaskCompletionRecord>(
            collectionName,
            1,
            1,
            filterBy = Filter(PocketBaseFilter.localIdEquals(localId))
        )
            .items.firstOrNull()
}
