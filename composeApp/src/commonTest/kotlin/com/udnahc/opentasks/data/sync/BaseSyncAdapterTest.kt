package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.domain.action.settings.ConfigurePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase.Companion.KEY_POCKETBASE_URL
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    fun equalTimestampDivergentLocalPayloadFailsClosed() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "pb-one", value = "local", synced = false, updatedAt = 20)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
        )

        adapter.pullAll(client)
        assertFailsWith<SyncAdapterException> {
            adapter.pushAll(client)
        }

        assertEquals("remote", adapter.remote.single().value)
        assertFalse(adapter.local.single().synced)
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
    fun seedModeCreatesNeverSyncedTombstoneInsteadOfHardDeletingIt() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", deleted = true, synced = false, updatedAt = 30)),
            remote = mutableListOf(),
        )

        adapter.seedAll(client)

        assertEquals(0, adapter.hardDeletedCount)
        assertTrue(adapter.local.single().synced)
        assertTrue(adapter.remote.single().deleted)
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

    @Test
    fun stalePbIdRecoversByLocalIdBeforeUpdatingRemote() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "stale-pb", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("current-pb")),
        )

        adapter.pushAll(client)

        assertEquals("current-pb", adapter.local.single().pbId)
        assertEquals("local", adapter.remote.single().value)
        assertTrue(adapter.local.single().synced)
    }

    @Test
    fun guardedStalePbIdRecoversByOwnerScopedLocalIdBeforeUpdatingRemote() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val gateway = PocketBaseRecordGateway(
            client = HttpClient(MockEngine { request ->
                requests += request.method to request.url.encodedPath
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/stale-pb") -> respond(
                        content = "{}",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    request.method == HttpMethod.Get -> respond(
                        content = """{"items":[{"id":"current-pb","account":"account-a","localId":"one","value":"remote","isDeleted":false,"localUpdatedAt":20}],"page":1,"totalPages":1}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    request.method == HttpMethod.Patch && request.url.encodedPath.endsWith("/current-pb") -> respond(
                        content = """{"id":"current-pb","account":"account-a","localId":"one","value":"local","isDeleted":false,"localUpdatedAt":30}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    else -> error("Unexpected request ${request.method.value} ${request.url}")
                }
            }),
            baseUrl = "https://example.test",
            ownerBinding = CacheBinding(
                canonicalEndpoint = "https://example.test",
                serverInstanceId = "server",
                accountId = "account-a",
                capabilityVersion = 2,
                boundaryEpoch = 1,
            ),
        )
        val adapter = GuardedFakeAdapter(
            local = mutableListOf(
                FakeEntity(
                    id = "one",
                    pbId = "stale-pb",
                    value = "local",
                    synced = false,
                    updatedAt = 30,
                )
            ),
            gateway = gateway,
        )

        adapter.pushAll(client)

        assertEquals("current-pb", adapter.local.single().pbId)
        assertTrue(adapter.local.single().synced)
        assertTrue(requests.contains(HttpMethod.Patch to "/api/collections/fake/records/current-pb"))
    }

    @Test
    fun suspiciouslySmallRemoteResponseDoesNotMarkManySyncedRowsUnsynced() = runBlocking {
        val local = (1..20).map { index ->
            FakeEntity(id = "local-$index", pbId = "pb-$index", value = "$index", synced = true, updatedAt = 30)
        }.toMutableList()
        val adapter = FakeAdapter(
            local = local,
            remote = mutableListOf(FakeRecord(localId = "local-1", value = "remote", updatedAt = 30).withId("pb-1")),
        )

        assertFailsWith<SyncDegradedException> {
            adapter.pullAll(client)
        }

        assertTrue(adapter.local.all { it.synced })
    }

    @Test
    fun degradedSmallPullSkipsThatCollectionsPush() = runBlocking {
        val provider = authenticatedProvider()
        val adapter = FakeAdapter(
            local = (1..20).map { index ->
                FakeEntity(
                    id = "local-$index",
                    pbId = "pb-$index",
                    value = "$index",
                    synced = true,
                    updatedAt = 30,
                )
            }.toMutableList(),
            remote = mutableListOf(FakeRecord(localId = "local-1", value = "1", updatedAt = 30).withId("pb-1")),
        )
        val service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<SyncException> { service.syncAll() }

        assertEquals(0, adapter.pushCount)
    }

    @Test
    fun emptyRemoteCollectionReportsDegradedSyncWithoutMarkingRowsUnsynced() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", pbId = "pb-one", value = "local", synced = true, updatedAt = 30)),
            remote = mutableListOf(),
        )

        assertFailsWith<SyncDegradedException> {
            adapter.pullAll(client)
        }
        assertTrue(adapter.local.single().synced)
    }

    @Test
    fun degradedRemoteRowsAreSkippedWhileValidRowsSync() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "missing", pbId = "pb-missing", value = "local", synced = true, updatedAt = 30)),
            remote = mutableListOf(
                FakeRecord(localId = "valid", value = "remote", updatedAt = 30).withId("pb-valid"),
                FakeRecord(localId = "orphan", value = "remote", updatedAt = 30).withId("pb-orphan"),
            ),
            invalidRemoteIds = setOf("orphan"),
        )

        assertFailsWith<SyncDegradedException> {
            adapter.pullAll(client)
        }
        assertEquals("remote", adapter.local.single { it.id == "valid" }.value)
        assertTrue(adapter.local.none { it.id == "orphan" })
        assertTrue(adapter.local.single { it.id == "missing" }.synced)
    }

    @Test
    fun pullFailureIsPropagated() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            failFetch = true,
        )

        assertFailsWith<SyncAdapterException> {
            adapter.pullAll(client)
        }
        Unit
    }

    @Test
    fun createFailureDoesNotMarkEntitySynced() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 20)),
            remote = mutableListOf(),
            failCreate = true,
        )

        assertFailsWith<SyncAdapterException> {
            adapter.pushAll(client)
        }
        assertFalse(adapter.local.single().synced)
    }

    @Test
    fun duplicateCreateRecoversByLocalIdAndUpdatesServerRow() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
            failCreate = true,
        )

        adapter.pushAll(client)

        assertEquals("pb-one", adapter.local.single().pbId)
        assertEquals("local", adapter.remote.single().value)
        assertTrue(adapter.local.single().synced)
    }

    @Test
    fun equalTimestampDivergentCreateConflictFailsClosed() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 30).withId("pb-one")),
            failCreate = true,
        )

        assertFailsWith<SyncAdapterException> { adapter.pushAll(client) }

        assertEquals("remote", adapter.remote.single().value)
        assertFalse(adapter.local.single().synced)
    }

    @Test
    fun syncServiceContinuesThroughAdaptersAndReportsFinalFailure() = runBlocking {
        val provider = authenticatedProvider()
        val failing = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            failFetch = true,
        )
        val succeeding = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "two", value = "local", synced = false, updatedAt = 20)),
            remote = mutableListOf(),
            collectionName = "other",
        )
        val service = SyncService(provider, listOf(failing, succeeding), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<SyncException> {
            service.syncAll()
        }
        assertTrue(succeeding.local.single().synced)
    }

    @Test
    fun syncServiceSkipsPushWhenPullFails() = runBlocking {
        val provider = authenticatedProvider()
        val adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(FakeRecord(localId = "one", value = "remote", updatedAt = 20).withId("pb-one")),
            failFetch = true,
        )
        val service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<SyncException> {
            service.syncAll()
        }
        assertFalse(adapter.local.single().synced)
        assertEquals("remote", adapter.remote.single().value)
        assertEquals(0, adapter.pushCount)
    }

    @Test
    fun parentPullFailuresSkipDependentOperations() = runBlocking {
        val provider = authenticatedProvider()
        val categories = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            failFetch = true,
            collectionName = "categories",
            order = 0,
        )
        val tasks = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "task", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(),
            collectionName = "tasks",
            order = 10,
        )
        val tags = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            failFetch = true,
            collectionName = "tags",
            order = 5,
        )
        val taskTags = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "task:tag", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(),
            collectionName = "task_tags",
            order = 20,
        )
        val service = SyncService(provider, listOf(categories, tags, tasks, taskTags), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<SyncException> {
            service.syncAll()
        }
        assertEquals(1, tasks.pullCount)
        assertEquals(0, tasks.pushCount)
        assertEquals(0, taskTags.pullCount)
        assertEquals(0, taskTags.pushCount)
    }

    @Test
    fun queuedPassCompletesAfterPreviousTransientFailure() = runBlocking {
        val provider = authenticatedProvider()
        lateinit var service: SyncService
        lateinit var adapter: FakeAdapter
        var requestedPending = false
        val pendingPassCompleted = CompletableDeferred<Unit>()
        adapter = FakeAdapter(
            local = mutableListOf(FakeEntity(id = "one", value = "local", synced = false, updatedAt = 30)),
            remote = mutableListOf(),
            failFetch = true,
            onPull = {
                if (!requestedPending) {
                    requestedPending = true
                    launch {
                        service.syncAll()
                        pendingPassCompleted.complete(Unit)
                    }
                    adapter.failFetch = false
                }
            },
        )
        service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<SyncException> { service.syncAll() }
        pendingPassCompleted.await()

        assertEquals(2, adapter.pullCount)
        assertTrue(adapter.local.single().synced)
    }

    @Test
    fun exclusiveResetWaitsForActiveAccountMutationBeforeDisconnectingAndClearing() = runBlocking {
        val provider = authenticatedProvider()
        val pullStarted = CompletableDeferred<Unit>()
        val releasePull = CompletableDeferred<Unit>()
        val pendingCancelled = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            onPull = {
                pullStarted.complete(Unit)
                releasePull.await()
            },
        )
        val service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())
        val activeSync = launch { service.syncAll() }
        pullStarted.await()
        var cleared = false

        val reset = launch {
            service.runExclusiveReset(
                cancelPendingSync = { pendingCancelled.complete(Unit) },
            ) {
                cleared = true
            }
        }

        // The account mutation gate lets the in-flight sync finish before the
        // reset disconnects its authenticated client.
        assertTrue(provider.isConfigured)
        assertFalse(cleared)
        assertEquals(1, adapter.pullCount)

        releasePull.complete(Unit)
        activeSync.join()
        pendingCancelled.await()
        assertFalse(provider.isConfigured)
        reset.join()
        assertTrue(cleared)
    }

    @Test
    fun failedExclusiveResetKeepsPocketBaseDisconnected() = runBlocking {
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val service = SyncService(provider, emptyList(), accountMutationGate = MutexAccountMutationGate())

        assertFailsWith<IllegalStateException> {
            service.runExclusiveReset(cancelPendingSync = {}) {
                error("cleanup failed")
            }
        }

        assertFalse(provider.isConfigured)
    }

    @Test
    fun configurePocketBaseUrlNoOpsWhenNoUrlIsSaved() = runBlocking {
        val provider = PocketBaseClientProvider()
        val configured = ConfigurePocketBaseUrlAction(
            FakeAppSettingsRepository(),
            provider,
            buildTimePocketBaseUrl = "",
        )()

        assertFalse(configured)
        assertFalse(provider.isConfigured)
    }

    @Test
    fun configurePocketBaseUrlUsesBuildTimeUrlWhenNoUrlIsSaved() = runBlocking {
        val provider = PocketBaseClientProvider()
        val configured = ConfigurePocketBaseUrlAction(
            FakeAppSettingsRepository(),
            provider,
            buildTimePocketBaseUrl = "http://build.example:8090",
        )()

        assertTrue(configured)
        assertEquals("build.example", provider.endpoint?.host)
        assertEquals(8090, provider.endpoint?.port)
    }

    @Test
    fun configurePocketBaseUrlUsesBuildTimeUrlWhenSavedUrlIsBlank() = runBlocking {
        val provider = PocketBaseClientProvider()
        val configured = ConfigurePocketBaseUrlAction(
            FakeAppSettingsRepository(mapOf(KEY_POCKETBASE_URL to "")),
            provider,
            buildTimePocketBaseUrl = "http://build.example:8090",
        )()

        assertTrue(configured)
        assertEquals("build.example", provider.endpoint?.host)
    }

    @Test
    fun configurePocketBaseUrlPrefersSavedUrlOverBuildTimeUrl() = runBlocking {
        val provider = PocketBaseClientProvider()
        val configured = ConfigurePocketBaseUrlAction(
            FakeAppSettingsRepository(mapOf(KEY_POCKETBASE_URL to "http://saved.example:8090")),
            provider,
            buildTimePocketBaseUrl = "http://build.example:8090",
        )()

        assertTrue(configured)
        assertEquals("saved.example", provider.endpoint?.host)
    }

    @Test
    fun failedUrlVerificationPreservesOldUrlAndProvider() = runBlocking {
        val settings = FakeAppSettingsRepository(mapOf(KEY_POCKETBASE_URL to "http://old.example:8090"))
        val provider = PocketBaseClientProvider().apply { configure("http://old.example:8090") }
        val oldEndpoint = provider.endpoint
        val verifier = PocketBaseConnectionVerifier(
            provider,
            listOf(FakeAdapter(mutableListOf(), mutableListOf())),
            healthCheck = { throw IllegalStateException("health failed") },
        )
        val action = SavePocketBaseUrlAction(
            settings,
            provider,
            verifier,
            SyncService(
                provider,
                listOf(FakeAdapter(mutableListOf(), mutableListOf())),
                accountMutationGate = MutexAccountMutationGate(),
            ),
        )

        assertFailsWith<PocketBaseConnectionException> {
            action("http://new.example:8090")
        }

        assertEquals("http://old.example:8090", settings.getValue(KEY_POCKETBASE_URL))
        assertEquals(oldEndpoint, provider.endpoint)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun unboundInitialSyncPreservesOldUrlAndProvider() = runBlocking {
        val settings = FakeAppSettingsRepository(mapOf(KEY_POCKETBASE_URL to "http://old.example:8090"))
        val provider = PocketBaseClientProvider().apply { configure("http://old.example:8090") }
        val oldEndpoint = provider.endpoint
        val verifier = PocketBaseConnectionVerifier(
            provider,
            listOf(FakeAdapter(mutableListOf(), mutableListOf())),
            healthCheck = {},
        )
        val action = SavePocketBaseUrlAction(
            settings,
            provider,
            verifier,
            SyncService(
                provider,
                listOf(FakeAdapter(mutableListOf(), mutableListOf(), failFetch = true)),
                accountMutationGate = MutexAccountMutationGate(),
            ),
        )

        assertFailsWith<IllegalStateException> {
            action("http://new.example:8090")
        }

        assertEquals("http://old.example:8090", settings.getValue(KEY_POCKETBASE_URL))
        assertEquals(oldEndpoint, provider.endpoint)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun connectionVerifierFailsWhenRequiredCollectionIsInaccessible() = runBlocking {
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val verifier = PocketBaseConnectionVerifier(
            provider,
            listOf(
                FakeAdapter(mutableListOf(), mutableListOf()),
                FakeAdapter(mutableListOf(), mutableListOf(), failVerify = true),
            ),
            healthCheck = {},
        )

        assertFailsWith<SyncException> {
            verifier.verify()
        }
        Unit
    }

    @Test
    fun connectionVerifierFailsWhenHealthCheckFails() = runBlocking {
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val verifier = PocketBaseConnectionVerifier(
            provider,
            listOf(FakeAdapter(mutableListOf(), mutableListOf())),
            healthCheck = { throw IllegalStateException("health failed") },
        )

        assertFailsWith<PocketBaseConnectionException> {
            verifier.verify()
        }
        Unit
    }
    private fun authenticatedProvider(): PocketBaseClientProvider = PocketBaseClientProvider().apply {
        activate(
            CacheBinding(
                canonicalEndpoint = "http://localhost:8090",
                serverInstanceId = "server",
                accountId = "account-a",
                capabilityVersion = 2,
                boundaryEpoch = 1,
            ),
            token = "test-token",
        )
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

private open class FakeAdapter(
    val local: MutableList<FakeEntity>,
    val remote: MutableList<FakeRecord>,
    var failFetch: Boolean = false,
    val failCreate: Boolean = false,
    val failVerify: Boolean = false,
    override val collectionName: String = "fake",
    override val order: Int = 10,
    val invalidRemoteIds: Set<String> = emptySet(),
    val onPull: suspend () -> Unit = {},
) : BaseSyncAdapter<FakeEntity, FakeRecord>() {
    var hardDeletedCount = 0
    var pullCount = 0
    var pushCount = 0

    override fun allowsTestOnlyLegacySdkWrites(): Boolean = true

    override suspend fun getUnsynced(): List<FakeEntity> {
        pushCount += 1
        return local.filter { !it.synced }
    }
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

    override suspend fun mergeRemoteIfNewer(entity: FakeEntity): RemoteMergeResult {
        val current = getById(entity.id)
        if (current != null && current.updatedAt >= entity.updatedAt) return RemoteMergeResult.KeptLocal
        upsert(entity)
        return RemoteMergeResult.Applied
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
    override fun recordFromJson(json: JsonObject): FakeRecord {
        val record = FakeRecord(
            localId = json["localId"]?.jsonPrimitive?.content.orEmpty(),
            value = json["value"]?.jsonPrimitive?.content.orEmpty(),
            deleted = json["isDeleted"]?.jsonPrimitive?.boolean ?: false,
            updatedAt = json["localUpdatedAt"]?.jsonPrimitive?.long ?: 0L,
        )
        return json["id"]?.jsonPrimitive?.content?.let(record::withId) ?: record
    }

    override suspend fun fetchAllRecords(client: PocketbaseClient): List<FakeRecord> {
        pullCount += 1
        if (failFetch) {
            onPull()
            throw IllegalStateException("fetch failed")
        }
        onPull()
        return remote.toList()
    }

    override suspend fun verifyCollection(client: PocketbaseClient) {
        if (failVerify) throw IllegalStateException("verify failed")
    }

    override suspend fun createRecord(client: PocketbaseClient, body: String): FakeRecord {
        if (failCreate) throw IllegalStateException("create failed")
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

    override suspend fun validateRemoteRecord(record: FakeRecord): String? =
        if (record.localId in invalidRemoteIds) {
            "Skipping orphan fake ${record.localId}"
        } else {
            null
        }

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

private class GuardedFakeAdapter(
    local: MutableList<FakeEntity>,
    private val gateway: PocketBaseRecordGateway,
) : FakeAdapter(local = local, remote = mutableListOf()) {
    override fun allowsTestOnlyLegacySdkWrites(): Boolean = false

    override fun recordGateway(client: PocketbaseClient): PocketBaseRecordGateway = gateway
}
