package com.udnahc.opentasks.data.sync

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import io.ktor.http.URLProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class BaseSyncAdapterTest {
    private val client = PocketbaseClient({
        protocol = URLProtocol.HTTP
        host = "localhost"
        port = 8090
    })

    @Test
    fun remoteNewerOverwritesUnsyncedLocal() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 10)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
        )

        adapter.pullAll(client)

        assertEquals(FakeEntity(id = "one", pbId = "pb-one", value = "remote", synced = true, updatedAt = 20), adapter.local.single())
    }

    @Test
    fun equalTimestampUnsyncedLocalPushesLocal() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "pb-one", value = "local", synced = false, updatedAt = 20)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
        )

        adapter.pullAll(client)
        adapter.pushAll(client)

        assertEquals("local", adapter.remote.single().value)
        assertTrue(adapter.local.single().synced)
    }

    @Test
    fun syncedDeletePushesTombstone() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "pb-one", value = "local", deleted = true, synced = false, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
        )

        adapter.pushAll(client)

        assertTrue(adapter.remote.single().deleted)
        assertTrue(adapter.local.single().synced)
        assertEquals(0, adapter.hardDeletedCount)
    }

    @Test
    fun neverSyncedTombstoneIsHardDeletedLocally() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", deleted = true, synced = false, updatedAt = 30)),
            remote = mutableListOf(),
        )

        adapter.pushAll(client)

        assertTrue(adapter.local.isEmpty())
        assertEquals(1, adapter.hardDeletedCount)
    }

    @Test
    fun missingActiveServerRowIsMarkedUnsyncedForRecreation() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "pb-one", value = "local", synced = true, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "other", value = "remote", updatedAt = 30).withId("pb-other")),
        )

        adapter.pullAll(client)

        assertFalse(adapter.local.first { it.id == "one" }.synced)
    }
}

private data class FakeEntity(
    val id: String,
    val pbId: String? = null,
    val value: String = "",
    val deleted: Boolean = false,
    val synced: Boolean = false,
    val updatedAt: Long = 0,
)

private open class FakeRecord(
    val localId: String = "",
    val value: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long = 0,
) : BaseModel() {
    fun withId(pbId: String): FakeRecordWithId = FakeRecordWithId(pbId, localId, value, deleted, updatedAt)
}

private class FakeRecordWithId(
    override val id: String?,
    localId: String,
    value: String,
    deleted: Boolean,
    updatedAt: Long,
) : FakeRecord(localId, value, deleted, updatedAt)

private class FakeAdapter(
    val local: MutableList<FakeEntity>,
    val remote: MutableList<FakeRecord>,
) : BaseSyncAdapter<FakeEntity, FakeRecord>() {
    var hardDeletedCount = 0

    override val collectionName = "fake"

    override suspend fun getUnsynced() = local.filter { !it.synced }
    override suspend fun getAllOnce() = local.toList()
    override suspend fun getById(localId: String) = local.firstOrNull { it.id == localId }

    override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean): Int {
        val index = local.indexOfFirst { it.id == localId && it.updatedAt == updatedAt && it.deleted == isDeleted }
        if (index < 0) return 0
        local[index] = local[index].copy(synced = true)
        return 1
    }

    override suspend fun updatePbId(localId: String, pbId: String) {
        val index = local.indexOfFirst { it.id == localId }
        if (index >= 0) local[index] = local[index].copy(pbId = pbId)
    }

    override suspend fun markUnsynced(localId: String) {
        val index = local.indexOfFirst { it.id == localId }
        if (index >= 0) local[index] = local[index].copy(synced = false)
    }

    override suspend fun hardDeleteLocalNeverSynced(entity: FakeEntity) {
        hardDeletedCount += 1
        local.removeAll { it.id == entity.id }
    }

    override suspend fun upsert(entity: FakeEntity) {
        local.removeAll { it.id == entity.id }
        local.add(entity)
    }

    override fun localId(entity: FakeEntity) = entity.id
    override fun pbId(entity: FakeEntity) = entity.pbId
    override fun isDeleted(entity: FakeEntity) = entity.deleted
    override fun isSynced(entity: FakeEntity) = entity.synced
    override fun updatedAt(entity: FakeEntity) = entity.updatedAt

    override fun recordLocalId(record: FakeRecord) = record.localId
    override fun recordIsDeleted(record: FakeRecord) = record.deleted
    override fun recordUpdatedAt(record: FakeRecord) = record.updatedAt

    override fun toRecord(entity: FakeEntity) = FakeRecord(entity.id, entity.value, entity.deleted, entity.updatedAt)
    override fun toEntity(record: FakeRecord) = FakeEntity(record.localId, record.id, record.value, record.deleted, synced = true, record.updatedAt)

    override suspend fun fetchAllRecords(client: PocketbaseClient) = remote.toList()

    override suspend fun createRecord(client: PocketbaseClient, body: String): FakeRecord {
        val entity = decode(body)
        val record = FakeRecord(entity.id, entity.value, entity.deleted, entity.updatedAt).withId("pb-${entity.id}")
        remote.add(record)
        return record
    }

    override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String): FakeRecord {
        val entity = decode(body)
        val index = remote.indexOfFirst { it.id == pbId }
        if (index < 0) throw IllegalStateException("Not found: 404")
        val record = FakeRecord(entity.id, entity.value, entity.deleted, entity.updatedAt).withId(pbId)
        remote[index] = record
        return record
    }

    override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String) =
        remote.firstOrNull { it.localId == localId }

    override fun toJsonBody(entity: FakeEntity): String =
        buildJsonObject {
            put("localId", entity.id)
            put("value", entity.value)
            put("isDeleted", entity.deleted)
            put("localUpdatedAt", entity.updatedAt)
        }.toString()

    private fun decode(body: String): FakeEntity {
        val obj = Json.parseToJsonElement(body).jsonObject
        return FakeEntity(
            id = obj.getValue("localId").jsonPrimitive.content,
            value = obj.getValue("value").jsonPrimitive.content,
            deleted = obj.getValue("isDeleted").jsonPrimitive.boolean,
            updatedAt = obj.getValue("localUpdatedAt").jsonPrimitive.long,
        )
    }
}
