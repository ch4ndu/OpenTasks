package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.TagRecord
import com.udnahc.opentasks.data.sync.records.toTag
import com.udnahc.opentasks.data.sync.records.toTagRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TagSyncAdapter(private val dao: TagDao) : BaseSyncAdapter<Tag, TagRecord>() {

    override val collectionName = "tags"
    override val order = 5

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllTagsOnce()
    override suspend fun getById(localId: String) = dao.findTagByIdAnyState(localId)
    override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean) =
        dao.markSyncedIfUnchanged(localId, updatedAt, isDeleted)
    override suspend fun updatePbId(localId: String, pbId: String) = dao.updatePbId(localId, pbId)
    override suspend fun markUnsynced(localId: String) = dao.markUnsynced(localId)
    override suspend fun hardDeleteLocalNeverSynced(entity: Tag) = dao.deleteTag(entity)
    override suspend fun upsert(entity: Tag) = dao.upsert(entity)

    override fun localId(entity: Tag) = entity.id
    override fun pbId(entity: Tag) = entity.pbId
    override fun isDeleted(entity: Tag) = entity.isDeleted
    override fun isSynced(entity: Tag) = entity.isSynced
    override fun updatedAt(entity: Tag) = entity.updatedAt

    override fun recordLocalId(record: TagRecord) = record.localId
    override fun recordIsDeleted(record: TagRecord) = record.isDeleted
    override fun recordUpdatedAt(record: TagRecord) = record.updatedAtUtc

    override fun toRecord(entity: Tag) = entity.toTagRecord()
    override fun toEntity(record: TagRecord) = record.toTag()
    override fun toJsonBody(entity: Tag) = Json.encodeToString(entity.toTagRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<TagRecord>(collectionName, 200)

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<TagRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<TagRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): TagRecord? =
        client.records.getList<TagRecord>(collectionName, 1, 1, filterBy = Filter("localId='$localId'"))
            .items.firstOrNull()
}
