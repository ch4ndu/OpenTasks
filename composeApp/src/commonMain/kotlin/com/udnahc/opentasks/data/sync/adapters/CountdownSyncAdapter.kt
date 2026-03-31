package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.CountdownRecord
import com.udnahc.opentasks.data.sync.records.toCountdown
import com.udnahc.opentasks.data.sync.records.toCountdownRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CountdownSyncAdapter(private val dao: CountdownDao) : BaseSyncAdapter<Countdown, CountdownRecord>() {

    override val collectionName = "countdowns"

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllCountdownsOnce()
    override suspend fun getById(localId: String) = dao.getCountdownById(localId)
    override suspend fun markSynced(localId: String) = dao.markSynced(localId)
    override suspend fun updatePbId(localId: String, pbId: String) = dao.updatePbId(localId, pbId)
    override suspend fun deleteEntity(entity: Countdown) = dao.delete(entity)
    override suspend fun upsert(entity: Countdown) = dao.upsert(entity)

    override fun localId(entity: Countdown) = entity.id
    override fun pbId(entity: Countdown) = entity.pbId
    override fun isDeleted(entity: Countdown) = entity.isDeleted
    override fun isSynced(entity: Countdown) = entity.isSynced
    override fun updatedAt(entity: Countdown) = entity.updatedAt

    override fun recordLocalId(record: CountdownRecord) = record.localId
    override fun recordIsDeleted(record: CountdownRecord) = record.isDeleted
    override fun recordUpdatedAt(record: CountdownRecord) = record.localUpdatedAt

    override fun toRecord(entity: Countdown) = entity.toCountdownRecord()
    override fun toEntity(record: CountdownRecord) = record.toCountdown()
    override fun toJsonBody(entity: Countdown) = Json.encodeToString(entity.toCountdownRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<CountdownRecord>(collectionName, 200)

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<CountdownRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<CountdownRecord>(collectionName, pbId, body)

    override suspend fun deleteRecord(client: PocketbaseClient, pbId: String) =
        client.records.delete(collectionName, pbId)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): CountdownRecord? =
        client.records.getList<CountdownRecord>(collectionName, 1, 1, filterBy = Filter("localId='$localId'"))
            .items.firstOrNull()
}
