package com.udnahc.opentasks.data.sync.adapters

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.testAttachment
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            file = "remote.jpg",
            updatedAtUtc = local.updatedAt,
        )

        assertFalse(createAdapter(storage).shouldSkipIncomingRecord(record, local))
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

    private fun createAdapter(storage: FakeAttachmentFileStorage) = AttachmentSyncAdapter(
        dao = database.attachmentDao(),
        taskDao = database.taskDao(),
        fileStorage = storage,
    )
}
