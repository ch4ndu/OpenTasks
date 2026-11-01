package com.udnahc.opentasks.data.sync.adapters

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseEndpoint
import com.udnahc.opentasks.data.sync.PocketBaseRecordGateway
import com.udnahc.opentasks.data.sync.PocketBaseRecordGatewayFactory
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.SyncOutcome
import com.udnahc.opentasks.data.sync.SyncPassContext
import com.udnahc.opentasks.data.sync.SyncPassContextFactory
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.data.sync.SyncWriterTransactionRunner
import com.udnahc.opentasks.data.sync.records.toTagRecord
import com.udnahc.opentasks.data.sync.records.toTaskRecord
import com.udnahc.opentasks.data.sync.records.toTaskTagRecord
import com.udnahc.opentasks.data.sync.syncRecordJson
import com.udnahc.opentasks.testutil.testTag
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.testutil.testTaskTag
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskTagDeletionSyncTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("task-tag-deletion-sync-test", ".db")
        database = Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
        databaseFile.delete()
    }

    @Test
    fun uploadedTaggedTaskDeletionAcknowledgesLinkAndNextPassSucceeds() = runTest {
        val task = testTask(pbId = "remote-task", isSynced = true, sourceExternalId = "calendar-event")
        val tag = testTag(pbId = "remote-tag", isSynced = true, name = "Calendar Import")
        val link = testTaskTag(pbId = "remote-link", isSynced = true)
        database.taskDao().insert(task)
        database.tagDao().insertTag(tag)
        database.tagDao().insertTaskTag(link)
        val records = mutableMapOf(
            "tags" to remoteJson(syncRecordJson.encodeToString(tag.toTagRecord()), "remote-tag"),
            "tasks" to remoteJson(syncRecordJson.encodeToString(task.toTaskRecord()), "remote-task"),
            "task_tags" to remoteJson(syncRecordJson.encodeToString(link.toTaskTagRecord()), "remote-link"),
        )
        val pushedCollections = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            val collection = request.url.encodedPath.substringAfter("/api/collections/").substringBefore('/')
            val remote = records.getValue(collection)
            val content = when (request.method) {
                HttpMethod.Get -> if (request.url.encodedPath.endsWith("/records")) pageJson(remote) else remote.toString()
                HttpMethod.Patch -> {
                    val body = (request.body as TextContent).text
                    val updated = remoteJson(body, remote.getValue("id").jsonPrimitive.content)
                    records[collection] = updated
                    pushedCollections += collection
                    updated.toString()
                }
                else -> error("Unexpected sync request ${request.method}")
            }
            respond(content, headers = jsonHeaders)
        })
        val provider = PocketBaseClientProvider()
        provider.activate(binding, "test-token")
        val mutationGate = MutexAccountMutationGate()
        val gateway = PocketBaseRecordGateway(http, binding.canonicalEndpoint, ownerBinding = binding)
        val service = SyncService(
            pbProvider = provider,
            adapters = listOf(
                TaskTagSyncAdapter(database.tagDao(), database.taskDao()),
                TaskSyncAdapter(database.taskDao(), database.attachmentDao(), database.tagDao()),
                TagSyncAdapter(database.tagDao()),
            ),
            accountMutationGate = mutationGate,
            passContextFactory = SyncPassContextFactory(database, object : PocketBaseRecordGatewayFactory() {
                override fun create(
                    client: PocketbaseClient,
                    endpoint: PocketBaseEndpoint,
                    binding: CacheBinding,
                ) = gateway
            }),
        )

        try {
            service.syncAll()
            assertEquals(SyncOutcome.Success, service.outcome.value)
            assertTrue(pushedCollections.isEmpty())

            TaskRepositoryImpl(
                database.taskDao(),
                object : SyncTrigger { override suspend fun triggerSync() = Unit },
                database,
                mutationGate = mutationGate,
            ).deleteGraph(task.id)
            val deletion = assertNotNull(database.tagDao().findTaskTagByIdAnyState(task.id, tag.id))
            assertTrue(deletion.isDeleted)
            assertFalse(deletion.isSynced)
            assertTrue(deletion.updatedAt > link.updatedAt)

            service.syncAll()

            val acknowledged = assertNotNull(database.tagDao().findTaskTagByIdAnyState(task.id, tag.id))
            assertEquals(deletion.copy(isSynced = true), acknowledged)
            assertEquals(listOf("tasks", "task_tags"), pushedCollections)
            assertTrue(records.getValue("tasks").getValue("isDeleted").jsonPrimitive.boolean)
            assertTrue(records.getValue("task_tags").getValue("isDeleted").jsonPrimitive.boolean)
            assertTrue(database.taskDao().getUnsynced().isEmpty())
            assertTrue(database.tagDao().getUnsyncedTaskTags().isEmpty())
            assertEquals(SyncOutcome.Success, service.outcome.value)

            service.syncAll()

            assertEquals(SyncOutcome.Success, service.outcome.value)
            assertEquals(acknowledged, database.tagDao().findTaskTagByIdAnyState(task.id, tag.id))
            assertEquals(listOf("tasks", "task_tags"), pushedCollections)
        } finally {
            provider.disconnect()
            http.close()
        }
    }

    @Test
    fun activeOrphanWithoutLocalTombstoneStillFailsPullAndDaoMerge() = runTest {
        database.taskDao().insert(testTask(isDeleted = true))
        database.tagDao().insertTag(testTag())
        val remote = testTaskTag(pbId = "remote-link", isSynced = true)

        withPullPass(remote) { pass ->
            assertFailsWith<SyncDegradedException> {
                TaskTagSyncAdapter(database.tagDao(), database.taskDao()).pullAll(pass)
            }
        }
        assertEquals(RemoteMergeResult.MissingParent, database.tagDao().mergeRemoteTaskTagIfNewer(remote))
        assertNull(database.tagDao().findTaskTagByIdAnyState(remote.taskId, remote.tagId))
    }

    @Test
    fun localDeletionMustBeStrictlyNewerThanActiveOrphan() = runTest {
        database.taskDao().insert(testTask(isDeleted = true))
        database.tagDao().insertTag(testTag())
        val local = testTaskTag(pbId = "remote-link", isDeleted = true, updatedAt = 100L)
        database.tagDao().insertTaskTag(local)

        for (remoteTimestamp in listOf(100L, 101L)) {
            val remote = local.copy(isDeleted = false, isSynced = true, updatedAt = remoteTimestamp)
            withPullPass(remote) { pass ->
                assertFailsWith<SyncDegradedException> {
                    TaskTagSyncAdapter(database.tagDao(), database.taskDao()).pullAll(pass)
                }
            }
            assertEquals(RemoteMergeResult.MissingParent, database.tagDao().mergeRemoteTaskTagIfNewer(remote))
            assertEquals(local, database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
        }
    }

    @Test
    fun writerBoundaryRechecksTombstoneAfterAdapterValidation() = runTest {
        database.taskDao().insert(testTask(isDeleted = true))
        database.tagDao().insertTag(testTag())
        val local = testTaskTag(pbId = "remote-link", isDeleted = true, updatedAt = 200L)
        database.tagDao().insertTaskTag(local)
        val remote = local.copy(isDeleted = false, isSynced = true, updatedAt = 100L)
        val changed = local.copy(isDeleted = false, updatedAt = 300L)
        var writerReached = false

        withPullPass(remote, beforeWriter = {
            writerReached = true
            database.tagDao().updateTaskTag(changed)
        }) { pass ->
            assertFailsWith<SyncDegradedException> {
                TaskTagSyncAdapter(database.tagDao(), database.taskDao()).pullAll(pass)
            }
        }

        assertTrue(writerReached)
        assertEquals(changed, database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
    }

    @Test
    fun deletedTagParentAlsoRetainsOnlyTheWinningLocalDeletion() = runTest {
        database.taskDao().insert(testTask())
        database.tagDao().insertTag(testTag(isDeleted = true))
        val local = testTaskTag(pbId = "remote-link", isDeleted = true, updatedAt = 200L)
        database.tagDao().insertTaskTag(local)
        val remote = local.copy(isDeleted = false, isSynced = true, updatedAt = 100L)

        withPullPass(remote) { pass ->
            TaskTagSyncAdapter(database.tagDao(), database.taskDao()).pullAll(pass)
        }

        assertEquals(local, database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
    }

    @Test
    fun newerRemoteDeletionReconcilesExistingLinkUnderDeletedParentWithoutPushing() = runTest {
        database.taskDao().insert(testTask(isDeleted = true, isSynced = true, updatedAt = 300L))
        database.tagDao().insertTag(testTag(isSynced = true))
        val local = testTaskTag(pbId = "remote-link", isDeleted = true, isSynced = false, updatedAt = 200L)
        val remote = local.copy(isSynced = true, updatedAt = 300L)
        database.tagDao().insertTaskTag(local)
        val adapter = TaskTagSyncAdapter(database.tagDao(), database.taskDao())

        withPullPass(remote) { pass ->
            adapter.pullAll(pass)
            adapter.pushAll(pass)
            assertEquals(remote, database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
            assertTrue(database.tagDao().getUnsyncedTaskTags().isEmpty())
            adapter.pullAll(pass)
            adapter.pushAll(pass)
            assertTrue(database.tagDao().getUnsyncedTaskTags().isEmpty())
        }
    }

    @Test
    fun equalAndOlderRemoteDeletionsPreserveLocalUntilPushAcknowledges() = runTest {
        database.taskDao().insert(testTask(isDeleted = true, isSynced = true))
        database.tagDao().insertTag(testTag(isSynced = true, isDeleted = true))
        val local = testTaskTag(pbId = "remote-link", isDeleted = true, isSynced = false, updatedAt = 200L)
        val adapter = TaskTagSyncAdapter(database.tagDao(), database.taskDao())

        for (remoteTimestamp in listOf(200L, 100L)) {
            database.tagDao().upsertTaskTag(local)
            val writes = mutableListOf<JsonObject>()
            withPullPass(local.copy(isSynced = true, updatedAt = remoteTimestamp), onWrite = { writes += it }) { pass ->
                adapter.pullAll(pass)
                assertEquals(local, database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
                adapter.pushAll(pass)
                assertEquals(local.copy(isSynced = true), database.tagDao().findTaskTagByIdAnyState(local.taskId, local.tagId))
                assertTrue(database.tagDao().getUnsyncedTaskTags().isEmpty())
                assertEquals(if (remoteTimestamp == local.updatedAt) 0 else 1, writes.size)
                val writeCount = writes.size
                adapter.pullAll(pass)
                adapter.pushAll(pass)
                assertEquals(writeCount, writes.size)
            }
        }
    }

    @Test
    fun remoteOrphanDeletionWithoutLocalLinkRemainsSafelySkipped() = runTest {
        val remote = testTaskTag(pbId = "remote-link", isDeleted = true, isSynced = true, updatedAt = 300L)
        withPullPass(remote) { pass ->
            TaskTagSyncAdapter(database.tagDao(), database.taskDao()).pullAll(pass)
        }
        assertEquals(RemoteMergeResult.MissingParent, database.tagDao().mergeRemoteTaskTagIfNewer(remote))
        assertNull(database.tagDao().findTaskTagByIdAnyState(remote.taskId, remote.tagId))
    }

    private suspend fun withPullPass(
        remote: TaskTag,
        beforeWriter: suspend () -> Unit = {},
        onWrite: ((JsonObject) -> Unit)? = null,
        block: suspend (SyncPassContext) -> Unit,
    ) {
        var currentRemote = remoteJson(syncRecordJson.encodeToString(remote.toTaskTagRecord()), "remote-link")
        val http = HttpClient(MockEngine { request ->
            val content = when (request.method) {
                HttpMethod.Get -> if (request.url.encodedPath.endsWith("/records")) pageJson(currentRemote) else currentRemote.toString()
                HttpMethod.Patch -> {
                    val recordWrite = checkNotNull(onWrite) { "Unexpected task-tag push" }
                    currentRemote = remoteJson((request.body as TextContent).text, "remote-link")
                    recordWrite(currentRemote)
                    currentRemote.toString()
                }
                else -> error("Unexpected task-tag request ${request.method}")
            }
            respond(content, headers = jsonHeaders)
        })
        val provider = PocketBaseClientProvider()
        val client = provider.activate(binding, "test-token")
        val pass = SyncPassContext(
            client,
            PocketBaseRecordGateway(http, binding.canonicalEndpoint, ownerBinding = binding),
            SyncWriterTransactionRunner { merge ->
                beforeWriter()
                database.useWriterConnection { connection ->
                    connection.immediateTransaction { merge() }
                }
            },
            allowNormalConflictRecovery = true,
        )
        try {
            block(pass)
        } finally {
            provider.disconnect()
            http.close()
        }
    }

    private fun remoteJson(body: String, id: String) = JsonObject(
        Json.parseToJsonElement(body).jsonObject + mapOf(
            "id" to JsonPrimitive(id),
            "account" to JsonPrimitive(binding.accountId),
        ),
    )

    private fun pageJson(record: JsonObject): String =
        """{"page":1,"perPage":200,"totalPages":1,"totalItems":1,"items":[$record]}"""

    private companion object {
        val binding = CacheBinding("https://example.test:443", "server", "account-a", 2, 1L)
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
