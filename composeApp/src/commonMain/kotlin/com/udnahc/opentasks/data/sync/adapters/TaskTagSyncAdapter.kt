package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.TaskTagRecord
import com.udnahc.opentasks.data.sync.records.taskTagLocalId
import com.udnahc.opentasks.data.sync.records.toTaskTag
import com.udnahc.opentasks.data.sync.records.toTaskTagRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskTagSyncAdapter(private val dao: TagDao) : BaseSyncAdapter<TaskTag, TaskTagRecord>() {

    override val collectionName = "task_tags"
    override val order = 20

    override suspend fun getUnsynced() = dao.getUnsyncedTaskTags()
    override suspend fun getAllOnce() = dao.getAllTaskTagsOnce()

    override suspend fun getById(localId: String): TaskTag? {
        val (taskId, tagId) = splitLocalId(localId) ?: return null
        return dao.findTaskTagByIdAnyState(taskId, tagId)
    }

    override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean): Int {
        val (taskId, tagId) = splitLocalId(localId) ?: return 0
        return dao.markTaskTagSyncedIfUnchanged(taskId, tagId, updatedAt, isDeleted)
    }

    override suspend fun updatePbId(localId: String, pbId: String) {
        val (taskId, tagId) = splitLocalId(localId) ?: return
        dao.updateTaskTagPbId(taskId, tagId, pbId)
    }

    override suspend fun markUnsynced(localId: String) {
        val (taskId, tagId) = splitLocalId(localId) ?: return
        dao.markTaskTagUnsynced(taskId, tagId)
    }

    override suspend fun hardDeleteLocalNeverSynced(entity: TaskTag) = dao.hardDeleteTaskTag(entity)
    override suspend fun upsert(entity: TaskTag) = dao.upsertTaskTag(entity)

    override fun localId(entity: TaskTag) = taskTagLocalId(entity.taskId, entity.tagId)
    override fun pbId(entity: TaskTag) = entity.pbId
    override fun isDeleted(entity: TaskTag) = entity.isDeleted
    override fun isSynced(entity: TaskTag) = entity.isSynced
    override fun updatedAt(entity: TaskTag) = entity.updatedAt

    override fun recordLocalId(record: TaskTagRecord) = record.localId
    override fun recordIsDeleted(record: TaskTagRecord) = record.isDeleted
    override fun recordUpdatedAt(record: TaskTagRecord) = record.updatedAtUtc

    override fun toRecord(entity: TaskTag) = entity.toTaskTagRecord()
    override fun toEntity(record: TaskTagRecord) = record.toTaskTag()
    override fun toJsonBody(entity: TaskTag) = Json.encodeToString(entity.toTaskTagRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<TaskTagRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<TaskTagRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<TaskTagRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<TaskTagRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): TaskTagRecord? =
        client.records.getList<TaskTagRecord>(collectionName, 1, 1, filterBy = Filter("localId='$localId'"))
            .items.firstOrNull()

    private fun splitLocalId(localId: String): Pair<String, String>? {
        val separator = localId.indexOf(':')
        if (separator <= 0 || separator == localId.lastIndex) return null
        return localId.substring(0, separator) to localId.substring(separator + 1)
    }
}
