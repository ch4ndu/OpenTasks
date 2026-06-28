package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.AttachmentRepositoryImpl
import com.udnahc.opentasks.data.repository.AppSettingsRepositoryImpl
import com.udnahc.opentasks.data.repository.CategoryRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.adapters.AttachmentFileDownloadException
import com.udnahc.opentasks.data.sync.adapters.AttachmentSyncAdapter
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.data.sync.records.toAttachment
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.testAttachment
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testTask
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    private object NoOpSyncTrigger : SyncTrigger {
        override suspend fun triggerSync() = Unit
    }

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("opentasks-test", ".db")
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
    fun taskDaoFiltersDeletedRowsAndOrdersByUpdatedAt() = runTest {
        database.taskDao().insert(testTask(id = "old", updatedAt = 10L))
        database.taskDao().insert(testTask(id = "new", updatedAt = 30L))
        database.taskDao().insert(testTask(id = "deleted", updatedAt = 40L, isDeleted = true))

        database.taskDao().getAllTasks().test {
            assertEquals(listOf("new", "old"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskRepositoryConvertsLocalTimestampsAndSoftDeletes() = runTest {
        val repository = TaskRepositoryImpl(
            taskDao = database.taskDao(),
            syncTrigger = NoOpSyncTrigger,
        )
        val localDeadline = 1_778_000_000_000L
        val task = testTask(id = "task", deadline = localDeadline, createdAt = localDeadline, updatedAt = localDeadline)

        repository.insert(task)
        val raw = database.taskDao().getTaskById("task")
        assertEquals(localToUtc(localDeadline), raw?.deadline)
        assertEquals(localToUtc(localDeadline), raw?.createdAt)

        val read = repository.getTaskById("task")
        assertEquals(localDeadline, read?.deadline)
        assertEquals(localDeadline, read?.createdAt)

        repository.delete(task)
        val deletedRaw = database.taskDao().findTaskByIdAnyState("task")
        assertTrue(deletedRaw?.isDeleted == true)
        assertFalse(deletedRaw.isSynced)
        assertEquals(null, repository.getTaskById("task"))
    }

    @Test
    fun taskDaoDeadlineQueriesExcludeDoneDeletedAndUndatedRows() = runTest {
        database.taskDao().insert(testTask(id = "included", deadline = 20L, status = TaskStatus.TODO))
        database.taskDao().insert(testTask(id = "done", deadline = 20L, status = TaskStatus.DONE))
        database.taskDao().insert(testTask(id = "deleted", deadline = 20L, isDeleted = true))
        database.taskDao().insert(testTask(id = "undated", deadline = null))

        assertEquals(listOf("included"), database.taskDao().getTasksWithDeadlines().map { it.id })
        assertEquals(listOf("included"), database.taskDao().getTasksInDateRange(10L, 30L).map { it.id })
    }

    @Test
    fun categoryRepositoryOrdersAndSoftDeletesCategories() = runTest {
        val repository = CategoryRepositoryImpl(
            categoryDao = database.categoryDao(),
            syncTrigger = NoOpSyncTrigger,
        )
        val first = testCategory(id = "first", name = "First", sortOrder = 2, createdAt = 1_000L, updatedAt = 1_000L)
        val second = testCategory(id = "second", name = "Second", sortOrder = 1, createdAt = 2_000L, updatedAt = 2_000L)

        repository.insert(first)
        repository.insert(second)

        repository.getAllCategories().test {
            val categories = awaitItem()
            assertEquals(listOf("second", "first"), categories.map { it.id })
            assertEquals(utcToLocal(localToUtc(2_000L)), categories.first().createdAt)
            cancelAndIgnoreRemainingEvents()
        }

        repository.delete(second)
        assertEquals(null, repository.getCategoryById("second"))
        assertTrue(database.categoryDao().findCategoryByIdAnyState("second")?.isDeleted == true)
    }

    @Test
    fun attachmentRepositorySummaryUsesSortOrderAndWorstSyncState() = runTest {
        val repository = AttachmentRepositoryImpl(
            dao = database.attachmentDao(),
            syncTrigger = NoOpSyncTrigger,
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "late",
                ownerId = "task",
                thumbnailPath = "/thumb/late.jpg",
                sortOrder = 2,
                createdAt = 20L,
                syncState = AttachmentSyncState.SYNCED,
                isSynced = true,
            )
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "first",
                ownerId = "task",
                thumbnailPath = "/thumb/first.jpg",
                sortOrder = 1,
                createdAt = 30L,
                syncState = AttachmentSyncState.FAILED,
            )
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "deleted",
                ownerId = "task",
                thumbnailPath = "/thumb/deleted.jpg",
                sortOrder = 0,
                isDeleted = true,
            )
        )

        repository.observeImageSummaries().test {
            val summary = awaitItem().single()
            assertEquals(2, summary.imageCount)
            assertEquals("/thumb/first.jpg", summary.firstThumbnailPath)
            assertEquals(AttachmentSyncState.FAILED, summary.worstSyncState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearLocalDataDeletesAttachmentRowsAndStoredFiles() = runTest {
        database.taskDao().insert(testTask(id = "task-clear"))
        database.attachmentDao().insert(testAttachment(id = "attachment-clear", ownerId = "task-clear"))
        val storage = FakeAttachmentFileStorage().apply {
            addFile("/tmp/attachment-clear.jpg")
            addFile("/tmp/attachment-clear_thumb.jpg")
        }
        val action = ClearLocalDataAction(
            taskDao = database.taskDao(),
            categoryDao = database.categoryDao(),
            noteDao = database.noteDao(),
            tagDao = database.tagDao(),
            attachmentDao = database.attachmentDao(),
            attachmentFileStorage = storage,
            appSettingsRepository = AppSettingsRepositoryImpl(database.appSettingsDao()),
        )

        action()

        assertTrue(storage.clearAllCalled)
        assertTrue(database.attachmentDao().getAllOnce().isEmpty())
        assertTrue(database.taskDao().getAllTasksOnce().isEmpty())
    }

    @Test
    fun attachmentPushHardDeletesNeverSyncedTombstoneBeforeParentGate() = runTest {
        database.attachmentDao().insert(
            testAttachment(
                id = "deleted-local-only",
                ownerId = "missing-parent",
                isDeleted = true,
                isSynced = false,
                syncState = AttachmentSyncState.LOCAL_ONLY,
                pbId = null,
            )
        )
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )

        adapter.pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))

        assertNull(database.attachmentDao().findByIdAnyState("deleted-local-only"))
    }

    @Test
    fun attachmentPushSkipsRemoteOriginDownloadAndBlockedRows() = runTest {
        database.attachmentDao().insert(
            testAttachment(
                id = "blocked",
                syncState = AttachmentSyncState.BLOCKED,
                lastSyncError = "blocked_policy",
                isSynced = false,
                pbId = "pb-blocked",
            )
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "needs-download",
                syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
                isSynced = false,
                pbId = "pb-needs-download",
            )
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "download-failed",
                syncState = AttachmentSyncState.FAILED,
                lastSyncError = "download_failed",
                isSynced = false,
                pbId = "pb-download-failed",
            )
        )
        database.attachmentDao().insert(
            testAttachment(
                id = "download-http-failed",
                syncState = AttachmentSyncState.FAILED,
                lastSyncError = "download_http_4xx",
                isSynced = false,
                pbId = "pb-download-http-failed",
            )
        )
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )

        adapter.pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))

        val blocked = database.attachmentDao().findByIdAnyState("blocked")
        val needsDownload = database.attachmentDao().findByIdAnyState("needs-download")
        val downloadFailed = database.attachmentDao().findByIdAnyState("download-failed")
        val downloadHttpFailed = database.attachmentDao().findByIdAnyState("download-http-failed")
        assertEquals(AttachmentSyncState.BLOCKED, blocked?.syncState)
        assertEquals("blocked_policy", blocked?.lastSyncError)
        assertEquals(AttachmentSyncState.NEEDS_DOWNLOAD, needsDownload?.syncState)
        assertNull(needsDownload?.lastSyncError)
        assertEquals(AttachmentSyncState.FAILED, downloadFailed?.syncState)
        assertEquals("download_failed", downloadFailed?.lastSyncError)
        assertEquals(AttachmentSyncState.FAILED, downloadHttpFailed?.syncState)
        assertEquals("download_http_4xx", downloadHttpFailed?.lastSyncError)
    }

    @Test
    fun attachmentPushMarksLocalOriginMissingFileFailed() = runTest {
        database.taskDao().insert(testTask(id = "task-1", pbId = "pb-task", isSynced = true))
        database.attachmentDao().insert(
            testAttachment(
                id = "missing-local-file",
                ownerId = "task-1",
                localPath = "/tmp/missing-local-file.jpg",
                syncState = AttachmentSyncState.LOCAL_ONLY,
                isSynced = false,
                pbId = null,
            )
        )
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )

        adapter.pushAll(PocketBaseClientProvider().createClient("http://localhost:8090"))

        val attachment = database.attachmentDao().findByIdAnyState("missing-local-file")
        assertEquals(AttachmentSyncState.FAILED, attachment?.syncState)
        assertEquals("local_file_missing", attachment?.lastSyncError)
        assertFalse(attachment?.isSynced ?: true)
    }

    @Test
    fun attachmentRemoteDecodeFailureIsBlocked() = runTest {
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )
        val incoming = testAttachment(
            id = "remote-corrupt-image",
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
            isSynced = false,
            pbId = "pb-remote-corrupt-image",
        )

        adapter.upsertRemoteDownloadFailure(incoming, AttachmentImageDecodeException())

        val attachment = assertNotNull(database.attachmentDao().findByIdAnyState(incoming.id))
        assertEquals(AttachmentSyncState.BLOCKED, attachment.syncState)
        assertEquals("blocked_decode_failed", attachment.lastSyncError)
        assertFalse(attachment.isSynced)
    }

    @Test
    fun attachmentRemoteHttpFailureIsRetryableFailed() = runTest {
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )
        val incoming = testAttachment(
            id = "remote-http-failure",
            syncState = AttachmentSyncState.NEEDS_DOWNLOAD,
            isSynced = false,
            pbId = "pb-remote-http-failure",
        )

        adapter.upsertRemoteDownloadFailure(incoming, AttachmentFileDownloadException(404))

        val attachment = assertNotNull(database.attachmentDao().findByIdAnyState(incoming.id))
        assertEquals(AttachmentSyncState.FAILED, attachment.syncState)
        assertEquals("download_http_4xx", attachment.lastSyncError)
        assertFalse(attachment.isSynced)
    }

    @Test
    fun attachmentPullDoesNotTreatDeletedLocalRowsAsMissingActiveFiles() = runTest {
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )
        val localTombstone = testAttachment(
            id = "deleted-local",
            localPath = "/tmp/deleted-local.jpg",
            isDeleted = true,
            updatedAt = 200L,
        )
        val olderRemoteActive = AttachmentRecord(
            localId = localTombstone.id,
            file = "remote.jpg",
            isDeleted = false,
            updatedAtUtc = 100L,
        )

        assertTrue(adapter.shouldSkipIncomingRecord(olderRemoteActive, localTombstone))
    }

    @Test
    fun attachmentRemoteTombstoneDeletesExistingLocalFilesBeforeUpsert() = runTest {
        val local = testAttachment(
            id = "remote-deleted",
            localPath = "/tmp/remote-deleted.jpg",
            thumbnailPath = "/tmp/remote-deleted-thumb.jpg",
            syncState = AttachmentSyncState.SYNCED,
            isSynced = true,
            pbId = "pb-remote-deleted",
        )
        database.attachmentDao().insert(local)
        val storage = FakeAttachmentFileStorage().apply {
            addFile(local.localPath)
            addFile(local.thumbnailPath)
        }
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = storage,
        )
        val remoteTombstone = AttachmentRecord(
            localId = local.id,
            isDeleted = true,
            updatedAtUtc = local.updatedAt + 100L,
        )

        adapter.upsertRemoteTombstone(remoteTombstone.toAttachment(), local)

        val stored = assertNotNull(database.attachmentDao().findByIdAnyState(local.id))
        assertFalse(storage.exists(local.localPath))
        assertFalse(storage.exists(local.thumbnailPath))
        assertTrue(stored.isDeleted)
        assertEquals(AttachmentSyncState.SYNCED, stored.syncState)
    }

    @Test
    fun attachmentMissingRowRecoveryMarksSyncedActiveRowsUnsynced() = runTest {
        database.attachmentDao().insert(
            testAttachment(
                id = "local-missing-remotely",
                isSynced = true,
                syncState = AttachmentSyncState.SYNCED,
                pbId = "pb-local",
            )
        )
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )

        adapter.recoverMissingRemoteRows(
            remoteRecords = listOf(AttachmentRecord(localId = "other", createdAtUtc = 1L, updatedAtUtc = 1L)),
            localSnapshot = database.attachmentDao().getAllOnce(),
        )

        val attachment = assertNotNull(database.attachmentDao().findByIdAnyState("local-missing-remotely"))
        assertFalse(attachment.isSynced)
        assertEquals(AttachmentSyncState.LOCAL_ONLY, attachment.syncState)
        assertEquals("pb-local", attachment.pbId)
    }

    @Test
    fun attachmentMissingRowRecoveryTreatsEmptyRemotePullAsDegraded() = runTest {
        database.attachmentDao().insert(
            testAttachment(
                id = "local-synced",
                isSynced = true,
                syncState = AttachmentSyncState.SYNCED,
                pbId = "pb-local",
            )
        )
        val adapter = AttachmentSyncAdapter(
            dao = database.attachmentDao(),
            taskDao = database.taskDao(),
            fileStorage = FakeAttachmentFileStorage(),
        )

        assertFailsWith<SyncDegradedException> {
            adapter.recoverMissingRemoteRows(
                remoteRecords = emptyList(),
                localSnapshot = database.attachmentDao().getAllOnce(),
            )
        }

        assertTrue(database.attachmentDao().findByIdAnyState("local-synced")?.isSynced == true)
    }
}
