package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.TaskRecord
import com.udnahc.opentasks.data.sync.records.toTask
import com.udnahc.opentasks.data.sync.records.toTaskRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskSyncAdapter(private val dao: TaskDao) : BaseSyncAdapter<Task, TaskRecord>() {

    override val collectionName = "tasks"

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllTasksOnce()
    override suspend fun getById(localId: String) = dao.findTaskByIdAnyState(localId)
    override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean) =
        dao.markSyncedIfUnchanged(localId, updatedAt, isDeleted)
    override suspend fun updatePbId(localId: String, pbId: String) = dao.updatePbId(localId, pbId)
    override suspend fun markUnsynced(localId: String) = dao.markUnsynced(localId)
    override suspend fun hardDeleteLocalNeverSynced(entity: Task) = dao.delete(entity)
    override suspend fun upsert(entity: Task) = dao.upsert(entity)

    override fun localId(entity: Task) = entity.id
    override fun pbId(entity: Task) = entity.pbId
    override fun isDeleted(entity: Task) = entity.isDeleted
    override fun isSynced(entity: Task) = entity.isSynced
    override fun updatedAt(entity: Task) = entity.updatedAt

    override fun recordLocalId(record: TaskRecord) = record.localId
    override fun recordIsDeleted(record: TaskRecord) = record.isDeleted
    override fun recordUpdatedAt(record: TaskRecord) = record.updatedAtUtc

    override fun toRecord(entity: Task) = entity.toTaskRecord()
    override fun toEntity(record: TaskRecord) = record.toTask()
    override fun toJsonBody(entity: Task) = Json.encodeToString(entity.toTaskRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<TaskRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<TaskRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<TaskRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<TaskRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): TaskRecord? =
        client.records.getList<TaskRecord>(collectionName, 1, 1, filterBy = Filter("localId='$localId'"))
            .items.firstOrNull()
}
