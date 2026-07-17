package com.udnahc.opentasks.data.sync

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import io.ktor.http.URLProtocol
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerSeedExecutorTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("server-seed-test", ".db")
        database = Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
        databaseFile.delete()
    }

    @Test
    fun `seeds active rows and tombstones for all collections in dependency order`() = runTest {
        setPending()
        val calls = mutableListOf<String>()
        val adapters = collectionNames.mapIndexed { index, name ->
            SeedAdapter(name, index * 5, calls).apply {
                rows += SeedRow("$name-active", deleted = false)
                rows += SeedRow("$name-tombstone", deleted = true)
            }
        }
        var inventoryReads = 0
        val executor = ServerSeedExecutor(database, adapters) {
            inventoryReads += 1
            if (inventoryReads == 1) inventory(emptyMap()) else inventory(adapters.associate { it.collectionName to it.remoteRows() })
        }

        executor.resume(client())

        assertEquals(collectionNames, calls)
        assertTrue(adapters.all { adapter -> adapter.rows.all { it.synced } })
        assertEquals(SyncMode.NORMAL.name, database.appSettingsDao().getValue(SyncSettingsKeys.MODE))
    }

    @Test
    fun `unknown newer or divergent inventory retains pending mode without seed writes`() = runTest {
        setPending()
        val adapter = SeedAdapter("categories", 0, mutableListOf()).apply {
            rows += SeedRow("local", deleted = false)
        }
        val variants = listOf(
            row("unknown", false, 1),
            row("local", false, 2),
            row("local", false, 1, value = "divergent"),
        )
        variants.forEach { invalid ->
            val executor = ServerSeedExecutor(database, listOf(adapter)) { inventory(mapOf("categories" to listOf(invalid))) }

            assertFailsWith<SyncAdapterException> { executor.resume(client()) }
            assertEquals(0, adapter.seedCalls)
            assertEquals(SyncMode.EMPTY_SERVER_SEED_PENDING.name, database.appSettingsDao().getValue(SyncSettingsKeys.MODE))
        }
    }

    @Test
    fun `failed final invariant keeps migration pending for a later resume`() = runTest {
        setPending()
        val adapter = SeedAdapter("categories", 0, mutableListOf()).apply {
            rows += SeedRow("local", deleted = false)
            complete = false
        }
        val executor = ServerSeedExecutor(database, listOf(adapter)) { inventory(mapOf("categories" to emptyList())) }

        assertFailsWith<SyncAdapterException> { executor.resume(client()) }

        assertEquals(1, adapter.seedCalls)
        assertEquals(SyncMode.EMPTY_SERVER_SEED_PENDING.name, database.appSettingsDao().getValue(SyncSettingsKeys.MODE))
    }

    @Test
    fun `identity change rejects resume before adapter writes`() = runTest {
        setPending(identity = "expected")
        val adapter = SeedAdapter("categories", 0, mutableListOf())
        val executor = ServerSeedExecutor(database, listOf(adapter)) { inventory(emptyMap(), identity = "different") }

        assertFailsWith<SyncAdapterException> { executor.resume(client()) }

        assertEquals(0, adapter.seedCalls)
        assertEquals(SyncMode.EMPTY_SERVER_SEED_PENDING.name, database.appSettingsDao().getValue(SyncSettingsKeys.MODE))
    }

    private suspend fun setPending(identity: String = "server") {
        database.appSettingsDao().setValue(AppSettings(SyncSettingsKeys.MODE, SyncMode.EMPTY_SERVER_SEED_PENDING.name))
        database.appSettingsDao().setValue(AppSettings(SyncSettingsKeys.SERVER_INSTANCE_ID, identity))
    }

    private fun inventory(
        rows: Map<String, List<JsonObject>>,
        identity: String = "server",
    ) = PocketBaseServerInventory(
        serverInstanceId = identity,
        recordsByCollection = collectionNames.associateWith { rows[it].orEmpty() },
    )

    private fun client() = PocketbaseClient({
        protocol = URLProtocol.HTTP
        host = "seed.test"
        port = 8090
    })

    private class SeedAdapter(
        override val collectionName: String,
        override val order: Int,
        private val calls: MutableList<String>,
    ) : BaseSyncAdapter<SeedRow, SeedRecord>() {
        val rows = mutableListOf<SeedRow>()
        var seedCalls = 0
        var complete = true

        override suspend fun getUnsynced() = rows.filterNot { it.synced }
        override suspend fun getAllOnce() = rows.toList()
        override suspend fun getById(localId: String) = rows.firstOrNull { it.id == localId }
        override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean): Int = 0
        override suspend fun updatePbId(localId: String, pbId: String) = Unit
        override suspend fun markUnsynced(localId: String) = Unit
        override suspend fun hardDeleteLocalNeverSynced(entity: SeedRow) = Unit
        override suspend fun upsert(entity: SeedRow) = Unit
        override suspend fun mergeRemoteIfNewer(entity: SeedRow) = RemoteMergeResult.KeptLocal
        override fun localId(entity: SeedRow) = entity.id
        override fun pbId(entity: SeedRow): String? = null
        override fun isDeleted(entity: SeedRow) = entity.deleted
        override fun isSynced(entity: SeedRow) = entity.synced
        override fun updatedAt(entity: SeedRow) = entity.updatedAt
        override fun recordLocalId(record: SeedRecord) = record.localId
        override fun recordIsDeleted(record: SeedRecord) = record.deleted
        override fun recordUpdatedAt(record: SeedRecord) = record.updatedAt
        override fun toRecord(entity: SeedRow) = SeedRecord(entity.id, entity.deleted, entity.updatedAt, entity.value)
        override fun toEntity(record: SeedRecord) = SeedRow(record.localId, record.deleted, true, record.updatedAt, record.value)
        override fun recordFromJson(json: JsonObject) = SeedRecord(
            json["localId"]?.jsonPrimitive?.content.orEmpty(),
            json["isDeleted"]?.jsonPrimitive?.content == "true",
            json["localUpdatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            json["value"]?.jsonPrimitive?.content.orEmpty(),
        )
        override suspend fun fetchAllRecords(client: PocketbaseClient) = emptyList<SeedRecord>()
        override suspend fun verifyCollection(client: PocketbaseClient) = Unit
        override suspend fun createRecord(client: PocketbaseClient, body: String) = SeedRecord()
        override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) = SeedRecord()
        override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): SeedRecord? = null
        override fun toJsonBody(entity: SeedRow) = row(entity.id, entity.deleted, entity.updatedAt, entity.value).toString()

        override suspend fun validateSeedInventory(rows: List<JsonObject>): Boolean = rows.all { remote ->
            val row = recordFromJson(remote)
            val local = getById(row.localId) ?: return@all false
            row.updatedAt < local.updatedAt ||
                row.updatedAt == local.updatedAt && row.deleted == local.deleted && row.value == local.value
        }

        override suspend fun seedAll(client: PocketbaseClient) {
            seedCalls += 1
            calls += collectionName
            rows.replaceAll { it.copy(synced = true) }
        }

        override suspend fun isSeedComplete(rows: List<JsonObject>): Boolean =
            complete && this.rows.all { it.synced } && rows.map { recordFromJson(it).localId }.toSet() == this.rows.map { it.id }.toSet()

        fun remoteRows() = rows.map { row(it.id, it.deleted, it.updatedAt, it.value) }
    }

    private data class SeedRow(
        val id: String,
        val deleted: Boolean,
        val synced: Boolean = false,
        val updatedAt: Long = 1,
        val value: String = "value",
    )

    private class SeedRecord(
        val localId: String = "",
        val deleted: Boolean = false,
        val updatedAt: Long = 0,
        val value: String = "",
    ) : BaseModel()

    private companion object {
        val collectionNames = listOf("categories", "tags", "tasks", "attachments", "task_tags", "notes", "countdowns")

        fun row(localId: String, deleted: Boolean, updatedAt: Long, value: String = "value") = buildJsonObject {
            put("id", "remote-$localId")
            put("localId", localId)
            put("isDeleted", deleted)
            put("localUpdatedAt", updatedAt)
            put("value", value)
        }
    }
}
