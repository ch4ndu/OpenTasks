package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.NoteRecord
import com.udnahc.opentasks.data.sync.records.toNote
import com.udnahc.opentasks.data.sync.records.toNoteRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.json.Json

class NoteSyncAdapter(private val dao: NoteDao) : BaseSyncAdapter<Note, NoteRecord>() {

    override val collectionName = "notes"
    override val order = 30

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllNotesOnce()
    override suspend fun getById(localId: String) = dao.findNoteByIdAnyState(localId)
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
    override suspend fun hardDeleteLocalNeverSynced(entity: Note) = dao.delete(entity)
    override suspend fun upsert(entity: Note) = dao.upsert(entity)

    override fun localId(entity: Note) = entity.id
    override fun pbId(entity: Note) = entity.pbId
    override fun isDeleted(entity: Note) = entity.isDeleted
    override fun isSynced(entity: Note) = entity.isSynced
    override fun updatedAt(entity: Note) = entity.updatedAt

    override fun recordLocalId(record: NoteRecord) = record.localId
    override fun recordIsDeleted(record: NoteRecord) = record.isDeleted
    override fun recordUpdatedAt(record: NoteRecord) = record.updatedAtUtc

    override fun toRecord(entity: Note) = entity.toNoteRecord()
    override fun toEntity(record: NoteRecord) = record.toNote()
    override fun toJsonBody(entity: Note) = Json.encodeToString(entity.toNoteRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<NoteRecord>(collectionName, 200)

    override suspend fun verifyCollection(client: PocketbaseClient) {
        client.records.getList<NoteRecord>(collectionName, 1, 1, skipTotal = true)
    }

    override suspend fun createRecord(
        client: PocketbaseClient,
        body: String
    ) =
        client.records.create<NoteRecord>(collectionName, body)

    override suspend fun updateRecord(
        client: PocketbaseClient,
        pbId: String,
        body: String
    ) =
        client.records.update<NoteRecord>(collectionName, pbId, body)

    override suspend fun findRecordByLocalId(
        client: PocketbaseClient,
        localId: String
    ): NoteRecord? =
        client.records.getList<NoteRecord>(
            collectionName,
            1,
            1,
            filterBy = Filter("localId='$localId'")
        )
            .items.firstOrNull()
}
