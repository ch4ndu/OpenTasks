package com.udnahc.opentasks.data.sync.adapters

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.attachment.AttachmentFilePolicy
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentFileTooLargeException
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseRecordGateway
import com.udnahc.opentasks.data.sync.AuthoritativeLocalSeedSourceException
import com.udnahc.opentasks.data.sync.SyncAdapterException
import com.udnahc.opentasks.data.sync.SyncAuthenticationRejectedException
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.testAttachment
import com.udnahc.opentasks.testutil.testTask
import java.io.File
import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttachmentSyncAdapterTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("attachment-sync-test", ".db")
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
    fun downloadFailurePreservesLocalFileMetadataAndAppliesRemoteState() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/local.jpg",
            thumbnailPath = "/tmp/local-thumb.jpg",
            remoteFileName = "old-remote.jpg",
            mimeType = "image/local",
            fileName = "local.jpg",
            fileSizeBytes = 321L,
            width = 640,
            height = 480,
            sortOrder = 1,
            syncState = AttachmentSyncState.SYNCED,
            pbId = "old-pb-id",
            updatedAt = 100L,
        )
        database.attachmentDao().insert(local)
        val incoming = testAttachment(
            id = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            remoteFileName = "new-remote.jpg",
            mimeType = "image/remote",
            fileName = "remote.jpg",
            fileSizeBytes = 999L,
            width = 10,
            height = 20,
            sortOrder = 7,
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
            pbId = "new-pb-id",
            createdAt = 50L,
            updatedAt = 200L,
        )
        val adapter = createAdapter(FakeAttachmentFileStorage())

        adapter.upsertRemoteDownloadFailure(incoming, AttachmentFileDownloadException(503), local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertEquals(local.localPath, stored.localPath)
        assertEquals(local.thumbnailPath, stored.thumbnailPath)
        assertEquals(local.mimeType, stored.mimeType)
        assertEquals(local.fileName, stored.fileName)
        assertEquals(local.fileSizeBytes, stored.fileSizeBytes)
        assertEquals(local.width, stored.width)
        assertEquals(local.height, stored.height)
        assertEquals("new-remote.jpg", stored.remoteFileName)
        assertEquals("new-pb-id", stored.pbId)
        assertEquals(7, stored.sortOrder)
        assertEquals(200L, stored.updatedAt)
        assertEquals(AttachmentSyncState.FAILED, stored.syncState)
        assertEquals("download_http_5xx", stored.lastSyncError)
        assertFalse(stored.isSynced)
    }

    @Test
    fun policyBlockPreservesUsableLocalFileMetadata() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/local.jpg",
            thumbnailPath = "/tmp/local-thumb.jpg",
            mimeType = "image/local",
            fileName = "local.jpg",
            fileSizeBytes = 321L,
            width = 640,
            height = 480,
            syncState = AttachmentSyncState.SYNCED,
            pbId = "old-pb-id",
            updatedAt = 100L,
        )
        database.attachmentDao().insert(local)
        val incoming = testAttachment(
            id = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            remoteFileName = "oversized.jpg",
            fileSizeBytes = Long.MAX_VALUE,
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
            pbId = "new-pb-id",
            createdAt = local.createdAt,
            updatedAt = 200L,
        )

        createAdapter(FakeAttachmentFileStorage()).upsertRemotePolicyBlock(incoming, local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertEquals(local.localPath, stored.localPath)
        assertEquals(local.thumbnailPath, stored.thumbnailPath)
        assertEquals(local.mimeType, stored.mimeType)
        assertEquals(local.fileName, stored.fileName)
        assertEquals(local.fileSizeBytes, stored.fileSizeBytes)
        assertEquals(local.width, stored.width)
        assertEquals(local.height, stored.height)
        assertEquals("oversized.jpg", stored.remoteFileName)
        assertEquals("new-pb-id", stored.pbId)
        assertEquals(200L, stored.updatedAt)
        assertEquals(AttachmentSyncState.BLOCKED, stored.syncState)
        assertEquals("blocked_policy", stored.lastSyncError)
        assertFalse(stored.isSynced)
    }

    @Test
    fun failedRemoteDownloadRetriesAtEqualTimestampEvenWhenLocalFileExists() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/local.jpg",
            syncState = AttachmentSyncState.FAILED,
            lastSyncError = "download_failed",
            isSynced = false,
            pbId = "pb-id",
            updatedAt = 200L,
        )
        val storage = FakeAttachmentFileStorage().apply { addFile(local.localPath) }
        val record = AttachmentRecord(
            localId = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            file = "remote.jpg",
            mimeType = local.mimeType,
            fileName = local.fileName,
            fileSizeBytes = local.fileSizeBytes,
            width = local.width,
            height = local.height,
            sortOrder = local.sortOrder,
            isDeleted = local.isDeleted,
            createdAtUtc = local.createdAt,
            updatedAtUtc = local.updatedAt,
        )

        assertFalse(createAdapter(storage).shouldSkipIncomingRecord(record, local))
    }

    @Test
    fun equalTimestampMetadataDivergenceFailsBeforeAttachmentFileAccess() = runTest {
        val local = testAttachment(id = "attachment", remoteFileName = "old.jpg", updatedAt = 200L)
        var existsCalls = 0
        val storage = object : AttachmentFileStorage by FakeAttachmentFileStorage() {
            override suspend fun exists(path: String): Boolean {
                existsCalls += 1
                return true
            }
        }
        val divergent = AttachmentRecord(
            localId = local.id,
            ownerType = local.ownerType,
            ownerId = "different-owner",
            kind = local.kind,
            file = "server-owned-name.jpg",
            mimeType = local.mimeType,
            fileName = local.fileName,
            fileSizeBytes = local.fileSizeBytes,
            width = local.width,
            height = local.height,
            sortOrder = local.sortOrder,
            isDeleted = local.isDeleted,
            createdAtUtc = local.createdAt,
            updatedAtUtc = local.updatedAt,
        )

        assertFailsWith<SyncDegradedException> {
            createAdapter(storage).shouldSkipIncomingRecord(divergent, local)
        }

        assertEquals(0, existsCalls)
    }

    @Test
    fun newerRemoteTombstoneWithAFileFailsBeforeAttachmentFileAccess() = runTest {
        val local = testAttachment(id = "attachment", remoteFileName = "old.jpg", updatedAt = 200L)
        var existsCalls = 0
        val storage = object : AttachmentFileStorage by FakeAttachmentFileStorage() {
            override suspend fun exists(path: String): Boolean {
                existsCalls += 1
                return true
            }
        }
        val malformedTombstone = AttachmentRecord(
            localId = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            file = "must-be-cleared.jpg",
            mimeType = local.mimeType,
            fileName = local.fileName,
            fileSizeBytes = local.fileSizeBytes,
            width = local.width,
            height = local.height,
            sortOrder = local.sortOrder,
            isDeleted = true,
            createdAtUtc = local.createdAt,
            updatedAtUtc = local.updatedAt + 1L,
        )

        assertFailsWith<SyncDegradedException> {
            createAdapter(storage).shouldSkipIncomingRecord(malformedTombstone, local)
        }

        assertEquals(0, existsCalls)
    }

    @Test
    fun equalTimestampRemoteDownloadKeepsPlatformDerivedLocalMediaMetadataOutOfTheComparison() = runTest {
        val local = testAttachment(
            id = "attachment",
            remoteFileName = "remote.jpg",
            mimeType = "image/webp",
            fileName = "locally-generated.webp",
            fileSizeBytes = 99L,
            width = 64,
            height = 48,
            updatedAt = 200L,
        )
        val storage = FakeAttachmentFileStorage().apply { addFile(local.localPath) }
        val remote = AttachmentRecord(
            localId = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            file = "remote.jpg",
            mimeType = "image/jpeg",
            fileName = "source.jpg",
            fileSizeBytes = 123L,
            width = 128,
            height = 96,
            sortOrder = local.sortOrder,
            isDeleted = local.isDeleted,
            createdAtUtc = local.createdAt,
            updatedAtUtc = local.updatedAt,
        )

        assertTrue(createAdapter(storage).shouldSkipIncomingRecord(remote, local))
    }

    @Test
    fun oversizedLocalAttachmentNeverStartsMultipartWrite() = runTest {
        val parent = testTask(id = "task", pbId = "task-remote", isSynced = true)
        val attachment = testAttachment(
            id = "attachment",
            ownerId = parent.id,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
        )
        database.taskDao().insert(parent)
        database.attachmentDao().insert(attachment)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(
                attachment.localPath,
                ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1),
            )
        }
        val requests = mutableListOf<HttpMethod>()
        val gateway = PocketBaseRecordGateway(HttpClient(MockEngine { request ->
            requests += request.method
            error("Oversized attachment must not reach the gateway")
        }), "https://example.test")

        assertFailsWith<SyncAdapterException> {
            GatewayAttachmentSyncAdapter(database, storage, gateway)
                .pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))
        }

        assertTrue(requests.isEmpty())
        assertEquals(
            AttachmentSyncState.FAILED,
            database.attachmentDao().findByIdAnyState(attachment.id)?.syncState,
        )
    }

    @Test
    fun fakeAttachmentStorageAcceptsTheExactCapAndRejectsOneMoreByte() = runTest {
        val storage = FakeAttachmentFileStorage()
        val exactPath = "/tmp/exact.jpg"
        val oversizedPath = "/tmp/oversized.jpg"
        storage.addFile(exactPath, ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt()))
        storage.addFile(oversizedPath, ByteArray(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt() + 1))

        assertEquals(AttachmentFilePolicy.MAX_UPLOAD_BYTES.toInt(), storage.readBytes(exactPath)?.size)
        assertFailsWith<AttachmentFileTooLargeException> { storage.readBytes(oversizedPath) }
    }

    @Test
    fun attachmentPushRethrowsTypedAuthenticationRejectionBeforeLaterRows() = runTest {
        val parent = testTask(id = "task", pbId = "task-remote", isSynced = true)
        val first = testAttachment(
            id = "attachment-first",
            ownerId = parent.id,
            pbId = "attachment-first-remote",
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
        )
        val later = testAttachment(
            id = "attachment-later",
            ownerId = parent.id,
            pbId = "attachment-later-remote",
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
        )
        database.taskDao().insert(parent)
        database.attachmentDao().insert(first)
        database.attachmentDao().insert(later)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(first.localPath)
            addFile(later.localPath)
        }
        var requests = 0
        val gateway = PocketBaseRecordGateway(HttpClient(MockEngine {
            requests += 1
            respond("", HttpStatusCode.Unauthorized)
        }), "https://example.test")

        assertFailsWith<SyncAuthenticationRejectedException> {
            GatewayAttachmentSyncAdapter(database, storage, gateway)
                .pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))
        }

        assertEquals(1, requests)
        assertFalse(database.attachmentDao().findByIdAnyState(later.id)?.isSynced == true)
    }

    @Test
    fun successfulRetryReplacesMetadataAndDeletesSupersededFiles() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/old.jpg",
            thumbnailPath = "/tmp/old-thumb.jpg",
            syncState = AttachmentSyncState.FAILED,
            lastSyncError = "download_failed",
            isSynced = false,
            pbId = "pb-id",
            updatedAt = 200L,
        )
        database.attachmentDao().insert(local)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(local.localPath)
            addFile(local.thumbnailPath)
        }
        val replacementFile = storage.storeRemoteImage("replacement.jpg", byteArrayOf(1, 2, 3))
        val incoming = testAttachment(
            id = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            remoteFileName = "replacement.jpg",
            sortOrder = 4,
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
            pbId = local.pbId,
            createdAt = local.createdAt,
            updatedAt = local.updatedAt,
        )

        createAdapter(storage).upsertRemoteDownloadSuccess(incoming, replacementFile, local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertEquals(replacementFile.localPath, stored.localPath)
        assertEquals(replacementFile.thumbnailPath, stored.thumbnailPath)
        assertEquals(replacementFile.fileName, stored.fileName)
        assertEquals(replacementFile.mimeType, stored.mimeType)
        assertEquals(replacementFile.fileSizeBytes, stored.fileSizeBytes)
        assertEquals(replacementFile.width, stored.width)
        assertEquals(replacementFile.height, stored.height)
        assertEquals(AttachmentSyncState.SYNCED, stored.syncState)
        assertTrue(stored.isSynced)
        assertFalse(storage.exists(local.localPath))
        assertFalse(storage.exists(local.thumbnailPath))
        assertTrue(storage.exists(replacementFile.localPath))
        assertTrue(storage.exists(replacementFile.thumbnailPath))
    }

    @Test
    fun downloadedArtifactIsRemovedWhenConcurrentNewerLocalEditWins() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/local.jpg",
            thumbnailPath = "/tmp/local-thumb.jpg",
            updatedAt = 300L,
            syncState = AttachmentSyncState.LOCAL_ONLY,
        )
        database.attachmentDao().insert(local)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(local.localPath)
            addFile(local.thumbnailPath)
        }
        val downloaded = storage.storeRemoteImage("losing-remote.jpg", byteArrayOf(1, 2, 3))
        val remote = testAttachment(
            id = local.id,
            ownerType = local.ownerType,
            ownerId = local.ownerId,
            kind = local.kind,
            remoteFileName = "losing-remote.jpg",
            pbId = "remote-id",
            updatedAt = 200L,
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
        )

        createAdapter(storage).upsertRemoteDownloadSuccess(remote, downloaded, local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertEquals(local.updatedAt, stored.updatedAt)
        assertEquals(local.localPath, stored.localPath)
        assertTrue(storage.exists(local.localPath))
        assertFalse(storage.exists(downloaded.localPath))
        assertFalse(storage.exists(downloaded.thumbnailPath))
    }

    @Test
    fun remoteTombstoneStillDeletesExistingLocalFiles() = runTest {
        val local = testAttachment(
            id = "attachment",
            localPath = "/tmp/local.jpg",
            thumbnailPath = "/tmp/local-thumb.jpg",
            syncState = AttachmentSyncState.SYNCED,
            pbId = "pb-id",
        )
        database.attachmentDao().insert(local)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(local.localPath)
            addFile(local.thumbnailPath)
        }
        val tombstone = testAttachment(
            id = local.id,
            isDeleted = true,
            syncState = AttachmentSyncState.SYNCED,
            pbId = local.pbId,
            updatedAt = local.updatedAt + 1L,
        )

        createAdapter(storage).upsertRemoteTombstone(tombstone, local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertTrue(stored.isDeleted)
        assertEquals(AttachmentSyncState.SYNCED, stored.syncState)
        assertFalse(storage.exists(local.localPath))
        assertFalse(storage.exists(local.thumbnailPath))
    }

    @Test
    fun downloadFailureDoesNotOverwriteAConcurrentNewerLocalTombstone() = runTest {
        val snapshot = testAttachment(
            id = "attachment",
            localPath = "/tmp/old.jpg",
            remoteFileName = "old-remote.jpg",
            updatedAt = 100L,
            syncState = AttachmentSyncState.SYNCED,
            isSynced = true,
        )
        val newerTombstone = snapshot.copy(
            isDeleted = true,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            remoteFileName = "local-tombstone.jpg",
            updatedAt = 300L,
        )
        database.attachmentDao().insert(newerTombstone)
        val incoming = snapshot.copy(
            remoteFileName = "remote.jpg",
            pbId = "remote-id",
            updatedAt = 200L,
        )

        createAdapter(FakeAttachmentFileStorage()).upsertRemoteDownloadFailure(
            incoming,
            AttachmentFileDownloadException(503),
            snapshot,
        )

        assertEquals(newerTombstone, database.attachmentDao().findByIdAnyState(newerTombstone.id))
    }

    @Test
    fun policyBlockDoesNotOverwriteAConcurrentNewerLocalEdit() = runTest {
        val snapshot = testAttachment(
            id = "attachment",
            localPath = "/tmp/old.jpg",
            remoteFileName = "old-remote.jpg",
            updatedAt = 100L,
            syncState = AttachmentSyncState.SYNCED,
            isSynced = true,
        )
        val newerLocal = snapshot.copy(
            localPath = "/tmp/newer-local.jpg",
            remoteFileName = "newer-local.jpg",
            sortOrder = 9,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            updatedAt = 300L,
        )
        database.attachmentDao().insert(newerLocal)
        val incoming = snapshot.copy(
            remoteFileName = "blocked.jpg",
            fileSizeBytes = Long.MAX_VALUE,
            pbId = "remote-id",
            updatedAt = 200L,
        )

        createAdapter(FakeAttachmentFileStorage()).upsertRemotePolicyBlock(incoming, snapshot)

        assertEquals(newerLocal, database.attachmentDao().findByIdAnyState(newerLocal.id))
    }

    @Test
    fun sameFileOutcomeDoesNotOverwriteAConcurrentNewerLocalTombstone() = runTest {
        val snapshot = testAttachment(
            id = "attachment",
            localPath = "/tmp/same.jpg",
            remoteFileName = "same.jpg",
            updatedAt = 100L,
            syncState = AttachmentSyncState.SYNCED,
            isSynced = true,
        )
        val newerTombstone = snapshot.copy(
            isDeleted = true,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            updatedAt = 300L,
        )
        database.attachmentDao().insert(newerTombstone)
        val incoming = snapshot.copy(pbId = "remote-id", updatedAt = 200L)

        createAdapter(FakeAttachmentFileStorage()).upsertRemoteSameFile(incoming)

        assertEquals(newerTombstone, database.attachmentDao().findByIdAnyState(newerTombstone.id))
    }

    @Test
    fun equalTimestampDivergentMultipartPayloadFailsClosedBeforeUpload() = runTest {
        val parent = testTask(id = "task", pbId = "task-remote", isSynced = true)
        val attachment = testAttachment(
            id = "attachment",
            ownerId = parent.id,
            pbId = "remote-id",
            fileName = "local.jpg",
            remoteFileName = "remote.jpg",
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            updatedAt = 100L,
        )
        database.taskDao().insert(parent)
        database.attachmentDao().insert(attachment)
        val storage = FakeAttachmentFileStorage().apply { addFile(attachment.localPath) }
        val requests = mutableListOf<HttpMethod>()
        val gateway = PocketBaseRecordGateway(HttpClient(MockEngine { request ->
            requests += request.method
            respond(
                content =
                    """{"id":"remote-id","localId":"attachment","ownerType":"task","ownerId":"task","kind":"image","file":"remote.jpg","mimeType":"image/jpeg","fileName":"remote.jpg","fileSizeBytes":100,"width":100,"height":100,"sortOrder":0,"isDeleted":false,"localCreatedAt":100,"localUpdatedAt":100}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }), "https://example.test")

        assertFailsWith<SyncAdapterException> {
            GatewayAttachmentSyncAdapter(database, storage, gateway)
                .pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))
        }

        assertEquals(listOf(HttpMethod.Get), requests)
        assertFalse(database.attachmentDao().findByIdAnyState(attachment.id)?.isSynced ?: true)
    }

    @Test
    fun equalTimestampDivergentMultipartTombstoneFailsClosedBeforeUpload() = runTest {
        val attachment = testAttachment(
            id = "attachment-tombstone",
            pbId = "remote-id",
            isDeleted = true,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            updatedAt = 100L,
        )
        database.attachmentDao().insert(attachment)
        val requests = mutableListOf<HttpMethod>()
        val gateway = PocketBaseRecordGateway(HttpClient(MockEngine { request ->
            requests += request.method
            respond(
                content =
                    """{"id":"remote-id","localId":"attachment-tombstone","ownerType":"task","ownerId":"task-1","kind":"image","file":"still-active.jpg","mimeType":"image/jpeg","fileName":"attachment-tombstone.jpg","fileSizeBytes":100,"width":100,"height":100,"sortOrder":0,"isDeleted":false,"localCreatedAt":100,"localUpdatedAt":100}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }), "https://example.test")

        assertFailsWith<SyncAdapterException> {
            GatewayAttachmentSyncAdapter(database, FakeAttachmentFileStorage(), gateway)
                .pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))
        }

        assertEquals(listOf(HttpMethod.Get), requests)
        assertFalse(database.attachmentDao().findByIdAnyState(attachment.id)?.isSynced ?: true)
    }

    @Test
    fun seedModeCreatesNeverSyncedTombstoneThroughGuardedGateway() = runTest {
        val tombstone = testAttachment(
            id = "seed-tombstone",
            ownerId = "missing-parent",
            isDeleted = true,
            isSynced = false,
            syncState = AttachmentSyncState.LOCAL_ONLY,
            updatedAt = 100L,
        )
        database.attachmentDao().insert(tombstone)
        val requests = mutableListOf<String>()
        val gateway = PocketBaseRecordGateway(HttpClient(MockEngine { request ->
            requests += request.url.encodedPath
            val body = when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("attachments/records") ->
                    """{"id":"remote-id","localId":"seed-tombstone","ownerType":"task","ownerId":"missing-parent","kind":"image","file":"","mimeType":"image/jpeg","fileName":"seed-tombstone.jpg","fileSizeBytes":100,"width":100,"height":100,"sortOrder":0,"isDeleted":true,"localCreatedAt":100,"localUpdatedAt":100}"""
                else -> """{"items":[]}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }), "https://example.test")

        GatewayAttachmentSyncAdapter(database, FakeAttachmentFileStorage(), gateway)
            .seedAll(PocketBaseClientProvider().createClient("http://localhost:8090"))

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(tombstone.id))
        assertTrue(stored.isSynced)
        assertEquals("remote-id", stored.pbId)
        assertTrue(requests.any { it.endsWith("attachments/records") })
    }

    @Test
    fun authoritativeSourceValidationRejectsMissingBytesWithoutMutatingAttachment() = runTest {
        val parent = testTask(id = "parent")
        val attachment = testAttachment(id = "missing-bytes", ownerId = parent.id)
        database.taskDao().insert(parent)
        database.attachmentDao().insert(attachment)

        assertFailsWith<AuthoritativeLocalSeedSourceException> {
            createAdapter(FakeAttachmentFileStorage()).validateLocalSeedSource()
        }

        assertEquals(attachment, database.attachmentDao().findByIdAnyState(attachment.id))
    }

    @Test
    fun authoritativeSourceValidationRejectsEveryNonUploadableActiveState() = runTest {
        val parent = testTask(id = "parent")
        database.taskDao().insert(parent)
        val storage = FakeAttachmentFileStorage()
        val invalidStates = listOf(
            AttachmentSyncState.BLOCKED to null,
            AttachmentSyncState.NEEDS_DOWNLOAD to null,
            AttachmentSyncState.FAILED to "download_failed",
            AttachmentSyncState.FAILED to "download_http_4xx",
            AttachmentSyncState.FAILED to "download_http_5xx",
        )

        invalidStates.forEachIndexed { index, (state, error) ->
            val attachment = testAttachment(
                id = "invalid-$index",
                ownerId = parent.id,
                syncState = state,
                lastSyncError = error,
            )
            storage.addFile(attachment.localPath)
            database.attachmentDao().insert(attachment)

            assertFailsWith<AuthoritativeLocalSeedSourceException>("$state/$error") {
                createAdapter(storage).validateLocalSeedSource()
            }

            assertEquals(attachment, database.attachmentDao().findByIdAnyState(attachment.id))
            database.attachmentDao().delete(attachment)
        }
    }

    @Test
    fun authoritativeSourceValidationRejectsActiveAttachmentWithoutLocalParent() = runTest {
        val attachment = testAttachment(id = "orphan", ownerId = "missing-parent")
        val storage = FakeAttachmentFileStorage().apply { addFile(attachment.localPath) }
        database.attachmentDao().insert(attachment)

        assertFailsWith<AuthoritativeLocalSeedSourceException> {
            createAdapter(storage).validateLocalSeedSource()
        }
    }

    @Test
    fun authoritativeSourceValidationAcceptsLocalGraphWithoutRemoteIdsAndFilelessTombstone() = runTest {
        val parent = testTask(id = "parent", pbId = null, isSynced = false)
        val active = testAttachment(id = "active", ownerId = parent.id, pbId = null)
        val tombstone = testAttachment(
            id = "tombstone",
            ownerId = "missing-parent",
            localPath = "",
            thumbnailPath = "",
            isDeleted = true,
            pbId = null,
        )
        val storage = FakeAttachmentFileStorage().apply { addFile(active.localPath) }
        database.taskDao().insert(parent)
        database.attachmentDao().insert(active)
        database.attachmentDao().insert(tombstone)

        createAdapter(storage).validateLocalSeedSource()

        assertEquals(active, database.attachmentDao().findByIdAnyState(active.id))
        assertEquals(tombstone, database.attachmentDao().findByIdAnyState(tombstone.id))
    }

    @Test
    fun authoritativeSeedThrowsTypedLocalSourceErrorInsteadOfSkippingBlockedAttachment() = runTest {
        val parent = testTask(id = "parent", pbId = "parent-remote", isSynced = true)
        val blocked = testAttachment(
            id = "blocked",
            ownerId = parent.id,
            syncState = AttachmentSyncState.BLOCKED,
            isSynced = false,
        )
        val storage = FakeAttachmentFileStorage().apply { addFile(blocked.localPath) }
        database.taskDao().insert(parent)
        database.attachmentDao().insert(blocked)

        assertFailsWith<AuthoritativeLocalSeedSourceException> {
            createAdapter(storage).seedAllAuthoritative(
                PocketBaseClientProvider().createClient("http://localhost:8090"),
            )
        }

        assertEquals(blocked, database.attachmentDao().findByIdAnyState(blocked.id))
    }

    private fun createAdapter(storage: AttachmentFileStorage) = AttachmentSyncAdapter(
        dao = database.attachmentDao(),
        taskDao = database.taskDao(),
        fileStorage = storage,
    )

    private class GatewayAttachmentSyncAdapter(
        database: AppDatabase,
        storage: FakeAttachmentFileStorage,
        private val gateway: PocketBaseRecordGateway,
    ) : AttachmentSyncAdapter(database.attachmentDao(), database.taskDao(), storage) {
        override fun recordGateway(client: PocketbaseClient): PocketBaseRecordGateway = gateway
    }
}
