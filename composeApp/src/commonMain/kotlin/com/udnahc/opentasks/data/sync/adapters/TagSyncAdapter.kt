package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.TagDao
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import com.udnahc.opentasks.data.sync.PocketBaseFilter
import com.udnahc.opentasks.data.sync.records.TagRecord
import com.udnahc.opentasks.data.sync.records.toTag
import com.udnahc.opentasks.data.sync.records.toTagRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class TagSyncAdapter(private val dao: TagDao) : BaseSyncAdapter<Tag, TagRecord>() {

    override val collectionName = "tags"
    override val order = 5

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllTagsOnce()
    override suspend fun getById(localId: String) = dao.findTagByIdAnyState(localId)
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
    override suspend fun shouldHardDeleteLocalNeverSynced(entity: Tag): Boolean =
        !dao.hasRemoteIdentityTaskTagForTag(entity.id)

    override suspend fun hardDeleteLocalNeverSynced(entity: Tag) {
        dao.deleteTagIfNoRemoteTaskTags(entity)
    }
    override suspend fun upsert(entity: Tag) = dao.upsert(entity)
    override suspend fun mergeRemoteIfNewer(entity: Tag): RemoteMergeResult = dao.mergeRemoteIfNewer(entity)

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
    override fun recordFromJson(json: JsonObject): TagRecord = gatewayJson.decodeFromJsonElement(json)
    override fun toJsonBody(entity: Tag) = Json.encodeToString(entity.toTagRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<TagRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<TagRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(
        client: PocketbaseClient,
        body: String
    ) =
        client.records.create<TagRecord>(collectionName, body)

    override suspend fun updateRecord(
        client: PocketbaseClient,
        pbId: String,
        body: String
    ) =
        client.records.update<TagRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(
        client: PocketbaseClient,
        localId: String
    ): TagRecord? =
        client.records.getList<TagRecord>(
            collectionName,
            1,
            1,
            filterBy = Filter(PocketBaseFilter.localIdEquals(localId))
        )
            .items.firstOrNull()
}
