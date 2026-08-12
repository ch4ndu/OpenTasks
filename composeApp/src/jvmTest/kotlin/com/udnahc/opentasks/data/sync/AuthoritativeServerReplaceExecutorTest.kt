package com.udnahc.opentasks.data.sync

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.auth.AccountTransition
import com.udnahc.opentasks.data.auth.AccountTransitionPhase
import com.udnahc.opentasks.data.auth.AccountTransitionPurpose
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.LOCAL_CACHE_OWNER_ID
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.settings.RoomAccountStateStore
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthoritativeServerReplaceExecutorTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase
    private lateinit var stateStore: RoomAccountStateStore
    private val binding = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com:443",
        serverInstanceId = "server",
        accountId = "account-a",
        capabilityVersion = 2,
        boundaryEpoch = 4,
    )

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("authoritative-replace-test", ".db")
        database = Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        stateStore = RoomAccountStateStore(database)
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
        databaseFile.delete()
    }

    @Test
    fun `deletes a complete owner inventory in reverse dependency order before exact seed`() = runTest {
        val transition = transition(AccountTransitionPhase.REMOTE_DELETE_PENDING)
        persistPending(transition)
        val adapter = ReplacementAdapter("categories")
        val deleteCalls = mutableListOf<String>()
        var replacementInventoryReads = 0
        val executor = executor(
            adapter = adapter,
            replacementInventory = {
                replacementInventoryReads += 1
                if (replacementInventoryReads == 1) populatedInventory() else emptyInventory()
            },
            delete = { _, collection, row ->
                deleteCalls += "$collection:${row["id"]?.jsonPrimitive?.content}"
                GatewayResponse(HttpStatusCode.NoContent, Unit, "")
            },
            seedInventory = { emptyInventory() },
        )

        val result = executor.resume(client(), binding, transition)

        assertEquals(AccountTransitionPhase.NEEDS_ACTIVATION, result.phase)
        assertEquals(
            AuthoritativeServerReplaceExecutor.DELETE_ORDER,
            deleteCalls.map { it.substringBefore(':') },
        )
        assertEquals(SyncMode.NORMAL.name, database.appSettingsDao().getValue(SyncSettingsKeys.MODE))
        assertEquals(AccountTransitionPhase.NEEDS_ACTIVATION, stateStore.readTransition()?.phase)
    }

    @Test
    fun `local fingerprint is order independent and changes with complete row content`() = runTest {
        val adapter = ReplacementAdapter("categories").apply {
            rows += ReplacementRow("b", value = "second")
            rows += ReplacementRow("a", value = "first")
        }
        val executor = executor(
            adapter = adapter,
            replacementInventory = { emptyInventory() },
            delete = { _, _, _ -> GatewayResponse(HttpStatusCode.NoContent, Unit, "") },
            seedInventory = { emptyInventory() },
        )
        val first = executor.localInventoryFingerprint()
        adapter.rows.reverse()

        assertEquals(first, executor.localInventoryFingerprint())

        adapter.rows[0] = adapter.rows[0].copy(value = "edited")
        kotlin.test.assertNotEquals(first, executor.localInventoryFingerprint())
    }

    @Test
    fun `delete failure leaves pre-seed phase durable and a later process resumes from fresh inventory`() = runTest {
        val transition = transition(AccountTransitionPhase.REMOTE_DELETE_PENDING)
        persistPending(transition)
        val adapter = ReplacementAdapter("categories")
        val failing = executor(
            adapter = adapter,
            replacementInventory = { populatedInventory() },
            delete = { _, _, _ -> GatewayResponse(HttpStatusCode.InternalServerError, null, "") },
            seedInventory = { emptyInventory() },
        )

        assertFailsWith<SyncAdapterException> { failing.resume(client(), binding, transition) }
        assertEquals(AccountTransitionPhase.REMOTE_DELETE_PENDING, stateStore.readTransition()?.phase)

        var reads = 0
        val resumed = executor(
            adapter = adapter,
            replacementInventory = {
                reads += 1
                if (reads == 1) populatedInventory() else emptyInventory()
            },
            delete = { _, _, _ -> GatewayResponse(HttpStatusCode.NoContent, Unit, "") },
            seedInventory = { emptyInventory() },
        )

        assertEquals(
            AccountTransitionPhase.NEEDS_ACTIVATION,
            resumed.resume(client(), binding, transition).phase,
        )
    }

    @Test
    fun `concurrent final inventory divergence returns to full owner deletion phase`() = runTest {
        val transition = transition(AccountTransitionPhase.EXACT_SEED_PENDING)
        persistPending(transition)
        val adapter = ReplacementAdapter("categories").apply {
            rows += ReplacementRow("local")
        }
        var seedReads = 0
        val executor = executor(
            adapter = adapter,
            replacementInventory = { emptyInventory() },
            delete = { _, _, _ -> GatewayResponse(HttpStatusCode.NoContent, Unit, "") },
            seedInventory = {
                seedReads += 1
                if (seedReads == 1) {
                    emptyInventory()
                } else {
                    inventory(mapOf("categories" to listOf(remoteRow("other-client"))))
                }
            },
        )

        assertFailsWith<AuthoritativeReplacementConflictException> {
            executor.resume(client(), binding, transition)
        }

        assertEquals(AccountTransitionPhase.REMOTE_DELETE_PENDING, stateStore.readTransition()?.phase)
        assertEquals(
            SyncMode.AUTHORITATIVE_REPLACE_PENDING.name,
            database.appSettingsDao().getValue(SyncSettingsKeys.MODE),
        )
    }

    @Test
    fun `local source failure during exact seed preserves exact seed phase`() = runTest {
        val transition = transition(AccountTransitionPhase.EXACT_SEED_PENDING)
        persistPending(transition)
        val adapter = ReplacementAdapter("categories").apply {
            rows += ReplacementRow("local")
            authoritativeSeedFailure = AuthoritativeLocalSeedSourceException()
        }
        val executor = executor(
            adapter = adapter,
            replacementInventory = { emptyInventory() },
            delete = { _, _, _ -> GatewayResponse(HttpStatusCode.NoContent, Unit, "") },
            seedInventory = { emptyInventory() },
        )

        assertFailsWith<AuthoritativeLocalSeedSourceException> {
            executor.resume(client(), binding, transition)
        }

        assertEquals(AccountTransitionPhase.EXACT_SEED_PENDING, stateStore.readTransition()?.phase)
        assertEquals(
            SyncMode.AUTHORITATIVE_REPLACE_PENDING.name,
            database.appSettingsDao().getValue(SyncSettingsKeys.MODE),
        )
    }

    private fun executor(
        adapter: ReplacementAdapter,
        replacementInventory: suspend (PocketbaseClient) -> PocketBaseServerInventory,
        delete: suspend (PocketbaseClient, String, JsonObject) -> GatewayResponse<Unit>,
        seedInventory: suspend (PocketbaseClient) -> PocketBaseServerInventory,
    ): AuthoritativeServerReplaceExecutor {
        val seed = ServerSeedExecutor(database, listOf(adapter), seedInventory, stateStore)
        return AuthoritativeServerReplaceExecutor(
            adapters = listOf(adapter),
            seedExecutor = seed,
            migrationCoordinator = ServerMigrationCoordinator(database, stateStore),
            accountStateStore = stateStore,
            inventoryReader = replacementInventory,
            deleteRecord = delete,
        )
    }

    private suspend fun persistPending(transition: AccountTransition) {
        stateStore.persistBindingAndTransition(binding, transition)
        database.appSettingsDao().setValue(
            AppSettings(SyncSettingsKeys.MODE, SyncMode.AUTHORITATIVE_REPLACE_PENDING.name),
        )
    }

    private fun transition(phase: AccountTransitionPhase) = AccountTransition(
        sourceAccountId = LOCAL_CACHE_OWNER_ID,
        destinationAccountId = binding.accountId,
        canonicalEndpoint = binding.canonicalEndpoint,
        serverInstanceId = binding.serverInstanceId,
        capabilityVersion = binding.capabilityVersion,
        boundaryEpoch = binding.boundaryEpoch,
        phase = phase,
        purpose = AccountTransitionPurpose.LOCAL_AUTHORITATIVE_REPLACEMENT,
    )

    private fun populatedInventory(): PocketBaseServerInventory = inventory(
        AuthoritativeServerReplaceExecutor.DELETE_ORDER.associateWith { collection ->
            listOf(
                buildJsonObject {
                    put("id", "$collection-id")
                    put("localId", "$collection-local")
                    put("account", binding.accountId)
                    put("isDeleted", collection == "notes")
                },
            )
        },
    )

    private fun emptyInventory(): PocketBaseServerInventory = inventory(emptyMap())

    private fun inventory(rows: Map<String, List<JsonObject>>) = PocketBaseServerInventory(
        serverInstanceId = binding.serverInstanceId,
        recordsByCollection = PocketBaseServerInventoryReader.COLLECTIONS.associateWith { rows[it].orEmpty() },
        accountId = binding.accountId,
    )

    private fun remoteRow(id: String) = buildJsonObject {
        put("id", "$id-remote")
        put("localId", id)
        put("account", binding.accountId)
        put("isDeleted", false)
        put("localUpdatedAt", 1)
    }

    private fun client() = PocketbaseClient({
        protocol = URLProtocol.HTTP
        host = "replacement.test"
        port = 8090
    })

    private data class ReplacementRow(
        val id: String,
        val synced: Boolean = false,
        val value: String = "value",
    )

    private class ReplacementRecord(
        val localId: String = "",
    ) : BaseModel()

    private class ReplacementAdapter(
        override val collectionName: String,
    ) : BaseSyncAdapter<ReplacementRow, ReplacementRecord>() {
        val rows = mutableListOf<ReplacementRow>()
        var authoritativeSeedFailure: Throwable? = null
        override val order: Int = 0
        override suspend fun getUnsynced() = rows.filterNot { it.synced }
        override suspend fun getAllOnce() = rows.toList()
        override suspend fun getById(localId: String) = rows.firstOrNull { it.id == localId }
        override suspend fun markSyncedIfUnchanged(localId: String, updatedAt: Long, isDeleted: Boolean) = 0
        override suspend fun updatePbId(localId: String, pbId: String) = Unit
        override suspend fun markUnsynced(localId: String) = Unit
        override suspend fun hardDeleteLocalNeverSynced(entity: ReplacementRow) = Unit
        override suspend fun upsert(entity: ReplacementRow) = Unit
        override suspend fun mergeRemoteIfNewer(entity: ReplacementRow) = RemoteMergeResult.KeptLocal
        override fun localId(entity: ReplacementRow) = entity.id
        override fun pbId(entity: ReplacementRow): String? = null
        override fun isDeleted(entity: ReplacementRow) = false
        override fun isSynced(entity: ReplacementRow) = entity.synced
        override fun updatedAt(entity: ReplacementRow) = 1L
        override fun recordLocalId(record: ReplacementRecord) = record.localId
        override fun recordIsDeleted(record: ReplacementRecord) = false
        override fun recordUpdatedAt(record: ReplacementRecord) = 1L
        override fun toRecord(entity: ReplacementRow) = ReplacementRecord(entity.id)
        override fun toEntity(record: ReplacementRecord) = ReplacementRow(record.localId, synced = true)
        override fun recordFromJson(json: JsonObject) = ReplacementRecord(
            json["localId"]?.jsonPrimitive?.content.orEmpty(),
        )
        override suspend fun fetchAllRecords(client: PocketbaseClient) = emptyList<ReplacementRecord>()
        override suspend fun verifyCollection(client: PocketbaseClient) = Unit
        override suspend fun createRecord(client: PocketbaseClient, body: String) = ReplacementRecord()
        override suspend fun updateRecord(client: PocketbaseClient, pbId: String, body: String) = ReplacementRecord()
        override suspend fun findRecordByLocalId(client: PocketbaseClient, localId: String): ReplacementRecord? = null
        override fun toJsonBody(entity: ReplacementRow) = remoteRowStatic(entity.id, entity.value).toString()
        override suspend fun seedAll(client: PocketbaseClient) {
            rows.replaceAll { it.copy(synced = true) }
        }
        override suspend fun seedAllAuthoritative(client: PocketbaseClient) {
            authoritativeSeedFailure?.let { throw it }
            seedAll(client)
        }
        override suspend fun validateSeedInventory(rows: List<JsonObject>): Boolean = rows.all { remote ->
            getById(remote["localId"]?.jsonPrimitive?.content.orEmpty()) != null
        }
        override suspend fun isSeedComplete(rows: List<JsonObject>): Boolean =
            this.rows.all { it.synced } &&
                rows.map { it["localId"]?.jsonPrimitive?.content.orEmpty() }.toSet() == this.rows.map { it.id }.toSet()

        companion object {
            private fun remoteRowStatic(id: String, value: String) = buildJsonObject {
                put("localId", id)
                put("isDeleted", false)
                put("localUpdatedAt", 1)
                put("value", value)
            }
        }
    }
}
