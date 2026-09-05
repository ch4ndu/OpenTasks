package com.udnahc.opentasks.data.sync.adapters

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseRecordGateway
import com.udnahc.opentasks.data.sync.SyncAdapterException
import com.udnahc.opentasks.data.sync.SyncAuthenticationRejectedException
import com.udnahc.opentasks.data.sync.SyncPassContext
import com.udnahc.opentasks.data.sync.SyncWriterTransactionRunner
import com.udnahc.opentasks.testutil.testNote
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoteSyncAdapterTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("note-sync-test", ".db")
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
    fun lostCreateResponseKeepsTheDeletionAcrossPullPushPull() = runTest {
        val tombstone = testNote(
            id = "note",
            title = "Original",
            content = "",
            isDeleted = true,
            isSynced = false,
            updatedAt = 200L,
        )
        database.noteDao().insert(tombstone)

        val binding = CacheBinding(
            canonicalEndpoint = "https://example.test",
            serverInstanceId = "server",
            accountId = "account-a",
            capabilityVersion = 2,
            boundaryEpoch = 1L,
        )
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        var remoteDeleted = false
        var remoteUpdatedAt = 100L
        val http = HttpClient(MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val remote = noteRecordJson(
                isDeleted = remoteDeleted,
                updatedAt = remoteUpdatedAt,
            )
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/api/collections/notes/records") &&
                    request.url.parameters["perPage"] == "200" -> respond(
                    content = pageJson(remote),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/api/collections/notes/records") &&
                    request.url.parameters["perPage"] == "1" -> respond(
                    content = pageJson(remote),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/api/collections/notes/records/remote-note") -> respond(
                    content = remote,
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                request.method == HttpMethod.Patch &&
                    request.url.encodedPath.endsWith("/api/collections/notes/records/remote-note") -> {
                    remoteDeleted = true
                    remoteUpdatedAt = 200L
                    respond(
                        content = noteRecordJson(isDeleted = true, updatedAt = 200L),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                else -> error("Unexpected note sync request ${request.method} ${request.url}")
            }
        })
        val gateway = PocketBaseRecordGateway(
            client = http,
            baseUrl = binding.canonicalEndpoint,
            ownerBinding = binding,
        )
        val client = PocketBaseClientProvider().createClient(binding.canonicalEndpoint)
        val pass = SyncPassContext(
            client = client,
            gateway = gateway,
            writerTransactionRunner = SyncWriterTransactionRunner { block ->
                database.useWriterConnection { connection ->
                    connection.immediateTransaction { block() }
                }
            },
        )

        try {
            val adapter = NoteSyncAdapter(database.noteDao())

            adapter.pullAll(pass)

            assertEquals(tombstone, database.noteDao().findNoteByIdAnyState(tombstone.id))

            adapter.pushAll(pass)

            val pushed = assertNotNull(database.noteDao().findNoteByIdAnyState(tombstone.id))
            assertEquals("remote-note", pushed.pbId)
            assertTrue(pushed.isDeleted)
            assertTrue(pushed.isSynced)
            assertTrue(remoteDeleted)

            adapter.pullAll(pass)

            assertEquals(pushed, database.noteDao().findNoteByIdAnyState(tombstone.id))
            assertEquals(5, requests.size)
            assertTrue(requests.contains(HttpMethod.Get to "/api/collections/notes/records/remote-note"))
            assertTrue(requests.contains(HttpMethod.Patch to "/api/collections/notes/records/remote-note"))
            assertEquals(null, database.noteDao().getNoteById(tombstone.id))
        } finally {
            http.close()
            client.httpClient.close()
        }
    }

    @Test
    fun unauthorizedTombstoneLookupRetainsTheLocalDeletion() = runTest {
        val failure = failedTombstoneLookup(
            status = HttpStatusCode.Unauthorized,
            content = "{}",
        )

        assertIs<SyncAuthenticationRejectedException>(failure)
    }

    @Test
    fun malformedSuccessfulTombstoneLookupRetainsTheLocalDeletion() = runTest {
        val failure = failedTombstoneLookup(
            status = HttpStatusCode.OK,
            content = "not-json",
        )

        assertIs<SyncAdapterException>(failure)
    }

    @Test
    fun mismatchedLocalIdInTombstoneLookupRetainsTheLocalDeletion() = runTest {
        val failure = failedTombstoneLookup(
            status = HttpStatusCode.OK,
            content = pageJson(noteRecordJson(isDeleted = false, updatedAt = 100L, localId = "other")),
        )

        assertIs<SyncAdapterException>(failure)
        assertTrue(
            generateSequence<Throwable>(failure) { it.cause }
                .any { it.message?.contains("mismatched local id") == true },
        )
    }

    private suspend fun failedTombstoneLookup(
        status: HttpStatusCode,
        content: String,
    ): Throwable {
        val tombstone = testNote(
            id = "note",
            title = "Deleted",
            content = "",
            isDeleted = true,
            isSynced = false,
            updatedAt = 200L,
        )
        database.noteDao().insert(tombstone)

        val binding = CacheBinding(
            canonicalEndpoint = "https://example.test",
            serverInstanceId = "server",
            accountId = "account-a",
            capabilityVersion = 2,
            boundaryEpoch = 1L,
        )
        var lookupFilter = ""
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val http = HttpClient(MockEngine { request ->
            requests += request.method to request.url.encodedPath
            lookupFilter = request.url.parameters["filter"].orEmpty()
            respond(
                content = content,
                status = status,
                headers = jsonHeaders,
            )
        })
        val gateway = PocketBaseRecordGateway(
            client = http,
            baseUrl = binding.canonicalEndpoint,
            ownerBinding = binding,
        )
        val client = PocketBaseClientProvider().createClient(binding.canonicalEndpoint)
        val pass = SyncPassContext(
            client = client,
            gateway = gateway,
            writerTransactionRunner = SyncWriterTransactionRunner { block ->
                database.useWriterConnection { connection ->
                    connection.immediateTransaction { block() }
                }
            },
        )

        return try {
            val failure = assertFails { NoteSyncAdapter(database.noteDao()).pushAll(pass) }

            assertEquals(tombstone, database.noteDao().findNoteByIdAnyState(tombstone.id))
            assertEquals(listOf(HttpMethod.Get to "/api/collections/notes/records"), requests)
            assertContains(lookupFilter, "account")
            assertContains(lookupFilter, "account-a")
            assertContains(lookupFilter, "localId")
            assertContains(lookupFilter, "note")
            failure
        } finally {
            http.close()
            client.httpClient.close()
        }
    }

    private fun pageJson(record: String): String =
        """{"page":1,"perPage":200,"totalPages":1,"totalItems":1,"items":[$record]}"""

    private fun noteRecordJson(
        isDeleted: Boolean,
        updatedAt: Long,
        localId: String = "note",
    ): String =
        """{"id":"remote-note","account":"account-a","localId":"$localId","title":"Original","content":"","isDeleted":$isDeleted,"localCreatedAt":100,"localUpdatedAt":$updatedAt}"""

    private companion object {
        val jsonHeaders = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        )
    }
}
