package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.AccountAuthenticationRejectionHandler
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

        assertFailsWith<SyncDegradedException> {
            adapter.pullAll(client)
        }

        assertEquals("remote", adapter.remote.single().value)
        assertFalse(adapter.local.single().synced)
    }

    @Test
    fun equalTimestampIdenticalCanonicalPayloadSucceedsWithoutDegradation() = runBlocking {
        val local = FakeEntity(id = "one", pbId = "pb-one", value = "same", synced = true, updatedAt = 20)
        val adapter = FakeAdapter(
            local = mutableListOf(local),
            remote = mutableListOf(FakeRecord(localId = "one", value = "same", updatedAt = 20).withId("pb-one")),
        )

        adapter.pullAll(client)

        assertEquals(local, adapter.local.single())
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
    fun authoritativeSeedNeverMergesNewerRemoteWinnerIntoLocalSnapshot() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(
                FakeEntity(id = "one", value = "local", synced = false, updatedAt = 20),
            ),
            remote = mutableListOf(
                FakeRecord(localId = "one", value = "remote", updatedAt = 30).withId("pb-one"),
            ),
            failCreate = true,
        )

        assertFailsWith<AuthoritativeSeedConflictException> {
            adapter.seedAllAuthoritative(client)
        }

        assertEquals("local", adapter.local.single().value)
        assertFalse(adapter.local.single().synced)
        assertEquals("remote", adapter.remote.single().value)
    }

    @Test
    fun authoritativeSeedRejectsEqualTimestampDivergentRemotePayload() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(
                FakeEntity(id = "one", value = "local", synced = false, updatedAt = 30),
            ),
            remote = mutableListOf(
                FakeRecord(localId = "one", value = "remote", updatedAt = 30).withId("pb-one"),
            ),
            failCreate = true,
        )

        assertFailsWith<AuthoritativeSeedConflictException> {
            adapter.seedAllAuthoritative(client)
        }

        assertEquals("local", adapter.local.single().value)
        assertFalse(adapter.local.single().synced)
        assertEquals("remote", adapter.remote.single().value)
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
    fun syncServiceRethrowsNestedAuthenticationRejectionWithoutStartingLaterAdapters() = runBlocking {
        val provider = authenticatedProvider()
        val boundaries = mutableListOf<AccountBoundary>()
        val rejectionHandler = object : AccountAuthenticationRejectionHandler {
            override suspend fun onAuthenticationRejected(boundary: AccountBoundary): Boolean {
                boundaries += boundary
                return true
            }
        }
        val rejected = object : FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            collectionName = "categories",
            order = 0,
        ) {
            override suspend fun fetchAllRecords(client: PocketbaseClient): List<FakeRecord> {
                pullCount += 1
                throw SyncAdapterException("wrapped rejection", SyncAuthenticationRejectedException())
            }
        }
        val later = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            collectionName = "notes",
            order = 1,
        )
        val service = SyncService(
            pbProvider = provider,
            adapters = listOf(rejected, later),
            accountMutationGate = MutexAccountMutationGate(),
            authenticationRejectionHandler = rejectionHandler,
        )

        assertFailsWith<SyncAuthenticationRejectedException> { service.syncAll() }

        assertEquals(1, rejected.pullCount)
        assertEquals(0, rejected.pushCount)
        assertEquals(0, later.pullCount)
        assertEquals(0, later.pushCount)
        assertEquals(provider.activeBoundary(), boundaries.single())
        assertEquals(SyncOutcome.ReauthenticationRequired, service.outcome.value)
    }

    @Test
    fun initialPullRethrowsAuthenticationRejectionBeforeLaterCollections() = runBlocking {
        val provider = authenticatedProvider()
        val rejected = object : FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            collectionName = "categories",
            order = 0,
        ) {
            override suspend fun fetchAllRecords(client: PocketbaseClient): List<FakeRecord> {
                pullCount += 1
                throw SyncAdapterException("wrapped rejection", SyncAuthenticationRejectedException())
            }
        }
        val later = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            collectionName = "notes",
            order = 1,
        )
        val service = SyncService(
            pbProvider = provider,
            adapters = listOf(rejected, later),
            accountMutationGate = MutexAccountMutationGate(),
        )

        assertFailsWith<SyncAuthenticationRejectedException> { service.initialPull() }

        assertEquals(1, rejected.pullCount)
        assertEquals(0, later.pullCount)
        assertEquals(SyncOutcome.Failed, service.outcome.value)
    }

    @Test
    fun syncServiceAllocatesOneGatewayForTheWholePass() = runBlocking {
        val provider = authenticatedProvider()
        var gatewayCreations = 0
        val gatewayFactory = object : PocketBaseRecordGatewayFactory() {
            override fun create(
                client: PocketbaseClient,
                endpoint: PocketBaseEndpoint,
                binding: CacheBinding,
            ): PocketBaseRecordGateway {
                gatewayCreations += 1
                return super.create(client, endpoint, binding)
            }
        }
        val service = SyncService(
            pbProvider = provider,
            adapters = listOf(
                FakeAdapter(mutableListOf(), mutableListOf(), collectionName = "categories", order = 0),
                FakeAdapter(mutableListOf(), mutableListOf(), collectionName = "notes", order = 1),
            ),
            accountMutationGate = MutexAccountMutationGate(),
            passContextFactory = SyncPassContextFactory(gatewayFactory = gatewayFactory),
        )

        service.syncAll()

        assertEquals(1, gatewayCreations)
    }

    @Test
    fun initialPullAllocatesOneGatewayForTheWholePass() = runBlocking {
        val provider = authenticatedProvider()
        var gatewayCreations = 0
        val gatewayFactory = object : PocketBaseRecordGatewayFactory() {
            override fun create(
                client: PocketbaseClient,
                endpoint: PocketBaseEndpoint,
                binding: CacheBinding,
            ): PocketBaseRecordGateway {
                gatewayCreations += 1
                return super.create(client, endpoint, binding)
            }
        }
        val service = SyncService(
            pbProvider = provider,
            adapters = listOf(
                FakeAdapter(mutableListOf(), mutableListOf(), collectionName = "categories", order = 0),
                FakeAdapter(mutableListOf(), mutableListOf(), collectionName = "notes", order = 1),
            ),
            accountMutationGate = MutexAccountMutationGate(),
            passContextFactory = SyncPassContextFactory(gatewayFactory = gatewayFactory),
        )

        service.initialPull()

        assertEquals(1, gatewayCreations)
    }

    @Test
    fun missingRowWriterFailureFailsGenericPullWithoutSilentlyChangingTheCandidate() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(
                FakeEntity(
                    id = "missing",
                    pbId = "pb-missing",
                    value = "local",
                    synced = true,
                    updatedAt = 30,
                ),
            ),
            remote = mutableListOf(
                FakeRecord(localId = "other", value = "remote", updatedAt = 30).withId("pb-other"),
            ),
        )
        val pass = SyncPassContext(
            client = client,
            gateway = null,
            writerTransactionRunner = SyncWriterTransactionRunner {
                throw IllegalStateException("writer transaction failed")
            },
        )

        assertFailsWith<SyncAdapterException> { adapter.pullAll(pass) }

        assertTrue(adapter.local.first { it.id == "missing" }.synced)
    }

    @Test
    fun missingRowsUseOneWriterTransactionAndRollbackTogetherWhenTheSecondMutationFails() = runBlocking {
        val adapter = FakeAdapter(
            local = mutableListOf(
                FakeEntity(id = "missing-a", pbId = "pb-a", value = "a", synced = true, updatedAt = 30),
                FakeEntity(id = "missing-b", pbId = "pb-b", value = "b", synced = true, updatedAt = 30),
            ),
            remote = mutableListOf(FakeRecord(localId = "remote", value = "remote", updatedAt = 30).withId("pb-remote")),
            silentlySkippedRemoteIds = setOf("remote"),
            failMarkUnsyncedOnCall = 2,
        )
        var writerTransactionCalls = 0
        val pass = SyncPassContext(
            client = client,
            gateway = null,
            writerTransactionRunner = SyncWriterTransactionRunner { block ->
                writerTransactionCalls += 1
                val snapshot = adapter.local.toList()
                try {
                    block()
                } catch (error: Throwable) {
                    adapter.local.clear()
                    adapter.local.addAll(snapshot)
                    throw error
                }
            },
        )

        assertFailsWith<SyncAdapterException> { adapter.pullAll(pass) }

        assertEquals(1, writerTransactionCalls)
        assertTrue(adapter.local.first { it.id == "missing-a" }.synced)
        assertTrue(adapter.local.first { it.id == "missing-b" }.synced)
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
    fun syncOutcomeBelongsToTheExecutingSerializedPass() = runBlocking {
        val provider = authenticatedProvider()
        lateinit var service: SyncService
        lateinit var queuedPass: Job
        val firstPullStarted = CompletableDeferred<Unit>()
        val queuedPassCreated = CompletableDeferred<Unit>()
        val releaseFirstPull = CompletableDeferred<Unit>()
        val secondPullStarted = CompletableDeferred<Unit>()
        val releaseSecondPull = CompletableDeferred<Unit>()
        var pullInvocation = 0
        val adapter = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            onPull = {
                pullInvocation += 1
                when (pullInvocation) {
                    1 -> {
                        firstPullStarted.complete(Unit)
                        queuedPass = launch { service.syncAll() }
                        queuedPassCreated.complete(Unit)
                        releaseFirstPull.await()
                    }
                    2 -> {
                        secondPullStarted.complete(Unit)
                        releaseSecondPull.await()
                    }
                }
            },
        )
        service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        val firstPass = launch { service.syncAll() }
        firstPullStarted.await()
        queuedPassCreated.await()
        assertEquals(SyncOutcome.Syncing, service.outcome.value)
        yield()
        releaseFirstPull.complete(Unit)
        secondPullStarted.await()

        assertEquals(SyncOutcome.Syncing, service.outcome.value)
        firstPass.join()
        assertEquals(SyncOutcome.Syncing, service.outcome.value)

        releaseSecondPull.complete(Unit)
        queuedPass.join()
        assertEquals(SyncOutcome.Success, service.outcome.value)
    }

    @Test
    fun cancellingAnActivePassRestoresIdleOutcome() = runBlocking {
        val provider = authenticatedProvider()
        val pullStarted = CompletableDeferred<Unit>()
        val holdPull = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            onPull = {
                pullStarted.complete(Unit)
                holdPull.await()
            },
        )
        val service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        val activePass = launch { service.syncAll() }
        pullStarted.await()
        assertEquals(SyncOutcome.Syncing, service.outcome.value)

        activePass.cancelAndJoin()

        assertEquals(SyncOutcome.Idle, service.outcome.value)
    }

    @Test
    fun cancellingAQueuedPassDoesNotChangeTheActiveOutcome() = runBlocking {
        val provider = authenticatedProvider()
        lateinit var service: SyncService
        lateinit var queuedPass: Job
        val firstPullStarted = CompletableDeferred<Unit>()
        val queuedPassCreated = CompletableDeferred<Unit>()
        val releaseFirstPull = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(
            local = mutableListOf(),
            remote = mutableListOf(),
            onPull = {
                firstPullStarted.complete(Unit)
                queuedPass = launch { service.syncAll() }
                queuedPassCreated.complete(Unit)
                releaseFirstPull.await()
            },
        )
        service = SyncService(provider, listOf(adapter), accountMutationGate = MutexAccountMutationGate())

        val activePass = launch { service.syncAll() }
        firstPullStarted.await()
        queuedPassCreated.await()
        yield()
        queuedPass.cancelAndJoin()

        assertEquals(SyncOutcome.Syncing, service.outcome.value)

        releaseFirstPull.complete(Unit)
        activePass.join()
        assertEquals(1, adapter.pullCount)
        assertEquals(SyncOutcome.Success, service.outcome.value)
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
    val silentlySkippedRemoteIds: Set<String> = emptySet(),
    val onPull: suspend () -> Unit = {},
    private val failMarkUnsyncedOnCall: Int? = null,
) : BaseSyncAdapter<FakeEntity, FakeRecord>() {
    var hardDeletedCount = 0
    var pullCount = 0
    var pushCount = 0
    private var markUnsyncedCalls = 0

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
        markUnsyncedCalls += 1
        if (markUnsyncedCalls == failMarkUnsyncedOnCall) {
            throw IllegalStateException("mark unsynced failed")
        }
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

    override suspend fun skipRemoteRecordSilently(record: FakeRecord): Boolean =
        record.localId in silentlySkippedRemoteIds

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
