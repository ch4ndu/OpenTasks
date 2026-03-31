package com.udnahc.opentasks.data.sync.adapters

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.sync.BaseSyncAdapter
import com.udnahc.opentasks.data.sync.records.CategoryRecord
import com.udnahc.opentasks.data.sync.records.toCategory
import com.udnahc.opentasks.data.sync.records.toCategoryRecord
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CategorySyncAdapter(private val dao: CategoryDao) : BaseSyncAdapter<Category, CategoryRecord>() {

    override val collectionName = "categories"
    override val order = 0 // Push before tasks (tasks reference categories)

    override suspend fun getUnsynced() = dao.getUnsynced()
    override suspend fun getAllOnce() = dao.getAllCategoriesOnce()
    override suspend fun getById(localId: String) = dao.getCategoryById(localId)
    override suspend fun markSynced(localId: String) = dao.markSynced(localId)
    override suspend fun updatePbId(localId: String, pbId: String) = dao.updatePbId(localId, pbId)
    override suspend fun deleteEntity(entity: Category) = dao.delete(entity)
    override suspend fun upsert(entity: Category) = dao.upsert(entity)

    override fun localId(entity: Category) = entity.id
    override fun pbId(entity: Category) = entity.pbId
    override fun isDeleted(entity: Category) = entity.isDeleted
    override fun isSynced(entity: Category) = entity.isSynced
    override fun updatedAt(entity: Category) = entity.updatedAt

    override fun recordLocalId(record: CategoryRecord) = record.localId
    override fun recordIsDeleted(record: CategoryRecord) = record.isDeleted
    override fun recordUpdatedAt(record: CategoryRecord) = record.localUpdatedAt

    override fun toRecord(entity: Category) = entity.toCategoryRecord()
    override fun toEntity(record: CategoryRecord) = record.toCategory()
    override fun toJsonBody(entity: Category) = Json.encodeToString(entity.toCategoryRecord())

    override suspend fun fetchAllRecords(client: PocketbaseClient) =
        client.records.getFullList<CategoryRecord>(collectionName, 200)

    override suspend fun createRecord(client: PocketbaseClient, body: String) =
        client.records.create<CategoryRecord>(collectionName, body)

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) =
        client.records.update<CategoryRecord>(collectionName, pbId, body)

    override suspend fun deleteRecord(client: PocketbaseClient, pbId: String) =
        client.records.delete(collectionName, pbId)

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): CategoryRecord? =
        client.records.getList<CategoryRecord>(collectionName, 1, 1, filterBy = Filter("localId='$localId'"))
            .items.firstOrNull()
}
