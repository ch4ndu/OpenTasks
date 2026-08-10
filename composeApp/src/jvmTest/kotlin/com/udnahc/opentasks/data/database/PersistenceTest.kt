package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.turbine.test
import com.udnahc.opentasks.data.attachment.AttachmentImageDecodeException
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.AttachmentSyncState
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.model.ATTACHMENT_OWNER_TASK
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.AttachmentRepositoryImpl
import com.udnahc.opentasks.data.repository.AppSettingsRepositoryImpl
import com.udnahc.opentasks.data.repository.CategoryRepositoryImpl
import com.udnahc.opentasks.data.repository.TagRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.data.sync.SyncDegradedException
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.adapters.AttachmentFileDownloadException
import com.udnahc.opentasks.data.sync.adapters.AttachmentSyncAdapter
import com.udnahc.opentasks.data.sync.records.AttachmentRecord
import com.udnahc.opentasks.data.sync.records.toAttachment
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.testAttachment
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTag
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.testutil.testTaskTag
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
    private lateinit var mutationGate: MutexAccountMutationGate

    private object NoOpSyncTrigger : SyncTrigger {
        override suspend fun triggerSync() = Unit
    }

    @BeforeTest
    fun createDatabase() {
        mutationGate = MutexAccountMutationGate()
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
    fun migrationOneToTwoAddsMissingSyncColumns() {
        val migrationFile = File.createTempFile("opentasks-migration-1-2", ".db")
        val connection = BundledSQLiteDriver().open(migrationFile.absolutePath)
        try {
            connection.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY)")
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL PRIMARY KEY)")
            connection.execSQL(
                """
                INSERT INTO categories (
                    id, name, icon, sortOrder, isSynced, isDeleted, createdAt
                ) VALUES ('cat', 'Inbox', 'inbox', 0, 0, 0, 1234)
                """.trimIndent()
            )

            MIGRATION_1_2.migrate(connection)

            connection.prepare("SELECT pbId FROM tasks").use { statement ->
                assertFalse(statement.step())
            }
            connection.prepare("SELECT pbId, updatedAt FROM categories WHERE id = 'cat'").use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
                assertEquals(1234L, statement.getLong(1))
            }
            connection.prepare("SELECT pbId FROM notes").use { statement ->
                assertFalse(statement.step())
            }
        } finally {
            connection.close()
            migrationFile.delete()
        }
    }

    @Test
    fun migrationElevenToTwelvePreservesExistingTaskRowsAndAddsNullableFields() {
        val migrationFile = File.createTempFile("opentasks-migration-11-12", ".db")
        val connection = BundledSQLiteDriver().open(migrationFile.absolutePath)
        try {
            connection.execSQL(
                """
                CREATE TABLE tasks (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    subtasks TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    deadline INTEGER,
                    endDeadline INTEGER,
                    notifyBeforeValue INTEGER NOT NULL,
                    notifyBeforeUnit TEXT NOT NULL,
                    recurrenceType TEXT NOT NULL,
                    recurrenceInterval INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    isStarred INTEGER NOT NULL,
                    section TEXT,
                    isUrgent INTEGER NOT NULL,
                    isImportant INTEGER NOT NULL,
                    categoryId TEXT NOT NULL,
                    isAllDay INTEGER NOT NULL,
                    sourceExternalId TEXT,
                    location TEXT NOT NULL,
                    url TEXT NOT NULL,
                    organizer TEXT NOT NULL,
                    eventStatus TEXT NOT NULL,
                    attendees TEXT NOT NULL,
                    durationReminders TEXT NOT NULL,
                    dateReminders TEXT NOT NULL,
                    pbId TEXT,
                    isSynced INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX index_tasks_isDeleted_updatedAt ON tasks(isDeleted, updatedAt)")
            connection.execSQL(
                """
                INSERT INTO tasks (
                    id, title, content, subtasks, priority, deadline, endDeadline,
                    notifyBeforeValue, notifyBeforeUnit, recurrenceType, recurrenceInterval,
                    status, isStarred, section, isUrgent, isImportant, categoryId, isAllDay,
                    sourceExternalId, location, url, organizer, eventStatus, attendees,
                    durationReminders, dateReminders, pbId, isSynced, isDeleted, createdAt, updatedAt
                ) VALUES (
                    'task', 'Saved', 'body', '[]', 'HIGH', 100, 200,
                    5, 'DAYS', 'MONTHLY', 2,
                    'DONE', 1, 'Next', 1, 0, 'inbox', 0,
                    'external', 'office', 'https://example.com', 'ops', 'CONFIRMED', 'a@example.com',
                    '30,0', '60,0', 'pb-task', 1, 0, 1234, 5678
                )
                """.trimIndent(),
            )

            MIGRATION_11_12.migrate(connection)

            connection.prepare(
                "SELECT title, content, deadline, recurrenceType, recurrenceInterval, status, pbId, updatedAt, recurrenceAnchorDay, completedAt FROM tasks WHERE id = 'task'"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("Saved", statement.getText(0))
                assertEquals("body", statement.getText(1))
                assertEquals(100L, statement.getLong(2))
                assertEquals("MONTHLY", statement.getText(3))
                assertEquals(2L, statement.getLong(4))
                assertEquals("DONE", statement.getText(5))
                assertEquals("pb-task", statement.getText(6))
                assertEquals(5678L, statement.getLong(7))
                assertTrue(statement.isNull(8))
                assertTrue(statement.isNull(9))
            }
            connection.prepare("PRAGMA index_list(tasks)").use { statement ->
                var foundExistingIndex = false
                while (statement.step()) {
                    if (statement.getText(1) == "index_tasks_isDeleted_updatedAt") foundExistingIndex = true
                }
                assertTrue(foundExistingIndex)
            }
        } finally {
            connection.close()
            migrationFile.delete()
        }
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
            database = database,
            mutationGate = mutationGate,
        )
        val localDeadline = 1_778_000_000_000L
        val task = testTask(id = "task", deadline = localDeadline, createdAt = localDeadline, updatedAt = localDeadline)
            .copy(completedAt = localDeadline)

        repository.insert(task)
        val raw = database.taskDao().getTaskById("task")
        assertEquals(localToUtc(localDeadline), raw?.deadline)
        assertEquals(localToUtc(localDeadline), raw?.completedAt)
        assertEquals(localToUtc(localDeadline), raw?.createdAt)

        val read = repository.getTaskById("task")
        assertEquals(localDeadline, read?.deadline)
        assertEquals(localDeadline, read?.completedAt)
        assertEquals(localDeadline, read?.createdAt)

        repository.deleteGraph(task.id)
        val deletedRaw = database.taskDao().findTaskByIdAnyState("task")
        assertTrue(deletedRaw?.isDeleted == true)
        assertFalse(deletedRaw.isSynced)
        assertEquals(null, repository.getTaskById("task"))
    }

    @Test
    fun taskGraphDeleteCommitsParentAndChildTombstonesAtOneWriterBoundary() = runTest {
        val task = testTask(id = "task-graph", pbId = "pb-task", isSynced = true, updatedAt = 10L)
        val tag = testTag(id = "tag-graph")
        val attachment = testAttachment(
            id = "attachment-graph",
            ownerId = task.id,
            localPath = "/tmp/task-graph.jpg",
            thumbnailPath = "/tmp/task-graph-thumb.jpg",
            updatedAt = 30L,
        )
        database.taskDao().insert(task)
        database.tagDao().insertTag(tag)
        database.tagDao().insertTaskTag(testTaskTag(taskId = task.id, tagId = tag.id, pbId = "pb-task-tag", updatedAt = 20L))
        database.attachmentDao().insert(attachment)
        val repository = TaskRepositoryImpl(database.taskDao(), NoOpSyncTrigger, database, mutationGate = mutationGate)

        val result = repository.deleteGraph(task.id)

        assertTrue(result is com.udnahc.opentasks.data.repository.TaskGraphDeletionResult.Deleted)
        assertNull(database.taskDao().getTaskById(task.id))
        assertTrue(database.taskDao().findTaskByIdAnyState(task.id)?.isDeleted == true)
        assertTrue(database.tagDao().getTaskTagsForTaskAnyState(task.id).single().isDeleted)
        assertTrue(database.attachmentDao().getForOwnerAnyState(ATTACHMENT_OWNER_TASK, task.id).single().isDeleted)
        assertFalse(database.taskDao().findTaskByIdAnyState(task.id)?.isSynced ?: true)
        assertFalse(database.tagDao().getTaskTagsForTaskAnyState(task.id).single().isSynced)
        assertFalse(database.attachmentDao().getForOwnerAnyState(ATTACHMENT_OWNER_TASK, task.id).single().isSynced)
    }

    @Test
    fun taskGraphDeleteFailureRollsBackChildTombstonesAndLeavesFilesForRetry() = runTest {
        val task = testTask(id = "task-rollback", updatedAt = 10L)
        val tag = testTag(id = "tag-rollback")
        val attachment = testAttachment(id = "attachment-rollback", ownerId = task.id, updatedAt = 20L)
        database.taskDao().insert(task)
        database.tagDao().insertTag(tag)
        database.tagDao().insertTaskTag(testTaskTag(taskId = task.id, tagId = tag.id))
        database.attachmentDao().insert(attachment)
        val repository = TaskRepositoryImpl(
            database.taskDao(),
            NoOpSyncTrigger,
            database,
            beforeTaskGraphParentTombstone = { error("simulated transaction failure") },
            mutationGate = mutationGate,
        )

        assertFailsWith<IllegalStateException> { repository.deleteGraph(task.id) }

        assertFalse(database.taskDao().getTaskById(task.id)?.isDeleted ?: true)
        assertFalse(database.tagDao().getTaskTagsForTaskAnyState(task.id).single().isDeleted)
        assertFalse(database.attachmentDao().getForOwnerAnyState(ATTACHMENT_OWNER_TASK, task.id).single().isDeleted)
    }

    @Test
    fun postCommitTaskFileCleanupFailureKeepsDurableTombstones() = runTest {
        val task = testTask(id = "task-file-failure")
        val attachment = testAttachment(
            id = "attachment-file-failure",
            ownerId = task.id,
            localPath = "/tmp/task-file-failure.jpg",
            thumbnailPath = "/tmp/task-file-failure-thumb.jpg",
        )
        database.taskDao().insert(task)
        database.attachmentDao().insert(attachment)
        val repository = TaskRepositoryImpl(database.taskDao(), NoOpSyncTrigger, database, mutationGate = mutationGate)
        val storage = object : AttachmentFileStorage by FakeAttachmentFileStorage() {
            override suspend fun delete(path: String) = error("simulated file cleanup failure")
        }

        DeleteTaskAction(
            repository,
            storage,
            ScheduleTaskRemindersAction(NotificationScheduler(), repository),
            mutationGate = MutexAccountMutationGate(),
        )(task.id)

        assertTrue(database.taskDao().findTaskByIdAnyState(task.id)?.isDeleted == true)
        assertTrue(database.attachmentDao().getForOwnerAnyState(ATTACHMENT_OWNER_TASK, task.id).single().isDeleted)
    }

    @Test
    fun taskDaoDeadlineQueriesExcludeDoneDeletedAndUndatedRows() = runTest {
        database.taskDao().insert(testTask(id = "included", deadline = 20L, status = TaskStatus.TODO))
        database.taskDao().insert(testTask(id = "done", deadline = 20L, status = TaskStatus.DONE))
        database.taskDao().insert(testTask(id = "deleted", deadline = 20L, isDeleted = true))
        database.taskDao().insert(testTask(id = "undated", deadline = null))

        assertEquals(listOf("included"), database.taskDao().getTasksWithDeadlines().map { it.id })
        assertEquals(listOf("included"), database.taskDao().getTasksInDateRange(10L, 30L).map { it.id })
        assertEquals(
            listOf("included", "done"),
            database.taskDao().getTasksInDateRangeIncludingCompleted(10L, 30L).map { it.id },
        )
    }

    @Test
    fun oneShotTaskQueriesKeepTombstonesOutAndSeparateWidgetDoneSemantics() = runTest {
        database.taskDao().insert(testTask(id = "active", deadline = 20L))
        database.taskDao().insert(testTask(id = "done", deadline = 30L, status = TaskStatus.DONE))
        database.taskDao().insert(testTask(id = "deleted", deadline = 10L, isDeleted = true))

        assertEquals(listOf("active", "done"), database.taskDao().getActiveTasksOnce().map { it.id }.sorted())
        assertEquals(listOf("active"), database.taskDao().getIncompleteTasksOnce().map { it.id })
    }

    @Test
    fun countdownReminderReconciliationQueryIncludesTombstones() = runTest {
        database.countdownDao().insert(testCountdown(id = "active", isDeleted = false))
        database.countdownDao().insert(testCountdown(id = "deleted", isDeleted = true))

        assertEquals(
            setOf("active", "deleted"),
            database.countdownDao().getAllCountdownsForReminderReconciliationUtc().map { it.id }.toSet(),
        )
    }

    @Test
    fun taskTagRestorePreservesCreatedAtAtTheDaoBoundary() = runTest {
        val task = testTask(id = "task-tag-created")
        val tag = testTag(id = "tag-tag-created")
        database.taskDao().insert(task)
        database.tagDao().insertTag(tag)
        database.tagDao().insertTaskTag(
            testTaskTag(taskId = task.id, tagId = tag.id, createdAt = 100L, updatedAt = 100L),
        )
        val repository = TagRepositoryImpl(database.tagDao(), NoOpSyncTrigger, mutationGate = mutationGate)

        repository.insertTaskTag(
            testTaskTag(taskId = task.id, tagId = tag.id, createdAt = 500L, updatedAt = 500L),
        )

        assertEquals(
            100L,
            database.tagDao().findTaskTagByIdAnyState(task.id, tag.id)?.createdAt,
        )
    }

    @Test
    fun neverSyncedTagTombstoneRetainsRemoteTaskTagAndOnlyDeletesFullyLocalCascade() = runTest {
        val task = testTask(id = "tag-cascade-task")
        val protectedTag = testTag(id = "protected-tag").copy(
            isDeleted = true,
            isSynced = false,
            pbId = null,
        )
        val localOnlyTag = testTag(id = "local-only-tag").copy(
            isDeleted = true,
            isSynced = false,
            pbId = null,
        )
        database.taskDao().insert(task)
        database.tagDao().insertTag(protectedTag)
        database.tagDao().insertTag(localOnlyTag)
        database.tagDao().insertTaskTag(
            testTaskTag(taskId = task.id, tagId = protectedTag.id, pbId = "remote-task-tag"),
        )
        database.tagDao().insertTaskTag(testTaskTag(taskId = task.id, tagId = localOnlyTag.id))

        assertTrue(database.tagDao().hasRemoteIdentityTaskTagForTag(protectedTag.id))
        assertFalse(database.tagDao().deleteTagIfNoRemoteTaskTags(protectedTag))
        assertNotNull(database.tagDao().findTagByIdAnyState(protectedTag.id))
        assertNotNull(database.tagDao().findTaskTagByIdAnyState(task.id, protectedTag.id))

        assertTrue(database.tagDao().deleteTagIfNoRemoteTaskTags(localOnlyTag))
        assertNull(database.tagDao().findTagByIdAnyState(localOnlyTag.id))
        assertNull(database.tagDao().findTaskTagByIdAnyState(task.id, localOnlyTag.id))
    }

    @Test
    fun observingOneSettingDoesNotReemitForAnUnrelatedSettingWrite() = runTest {
        val repository = AppSettingsRepositoryImpl(database.appSettingsDao())

        repository.observeValue("observed").test {
            assertNull(awaitItem())
            database.appSettingsDao().setValue(AppSettings("other", "value"))
            expectNoEvents()
            repository.setValue("observed", "updated")
            assertEquals("updated", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun categoryRepositoryOrdersAndSoftDeletesCategories() = runTest {
        val repository = CategoryRepositoryImpl(
            categoryDao = database.categoryDao(),
            syncTrigger = NoOpSyncTrigger,
            mutationGate = mutationGate,
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
            mutationGate = mutationGate,
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

        database.attachmentDao().insert(
            testAttachment(
                id = "note-image",
                ownerType = "note",
                ownerId = "note",
                thumbnailPath = "/thumb/note.jpg",
            )
        )

        repository.observeTaskImageSummaries().test {
            val summary = awaitItem().single()
            assertEquals(2, summary.imageCount)
            assertEquals("/thumb/first.jpg", summary.firstThumbnailPath)
            assertEquals(AttachmentSyncState.FAILED, summary.worstSyncState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearLocalDataDeletesEveryEntityAndStoredFileThenRecreatesInbox() = runTest {
        database.categoryDao().insert(testCategory(id = "category-clear"))
        database.taskDao().insert(testTask(id = "task-clear", categoryId = "category-clear"))
        database.noteDao().insert(testNote(id = "note-clear"))
        database.tagDao().insertTag(testTag(id = "tag-clear"))
        database.tagDao().insertTaskTag(testTaskTag(taskId = "task-clear", tagId = "tag-clear"))
        database.attachmentDao().insert(testAttachment(id = "attachment-clear", ownerId = "task-clear"))
        database.countdownDao().insert(testCountdown(id = "countdown-clear"))
        database.appSettingsDao().setValue(AppSettings("pocketbase_url", "http://localhost:8090"))
        val storage = FakeAttachmentFileStorage().apply {
            addFile("/tmp/attachment-clear.jpg")
            addFile("/tmp/attachment-clear_thumb.jpg")
        }
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val service = SyncService(provider, emptyList(), accountMutationGate = mutationGate)
        val trigger = TriggerSyncAction(provider, service)
        val action = clearLocalDataAction(storage, service, trigger)

        action()

        assertTrue(storage.clearAllCalled)
        assertFalse(provider.isConfigured)
        assertTrue(database.attachmentDao().getAllOnce().isEmpty())
        assertTrue(database.taskDao().getAllTasksOnce().isEmpty())
        assertTrue(database.noteDao().getAllNotesOnce().isEmpty())
        assertTrue(database.tagDao().getAllTagsOnce().isEmpty())
        assertTrue(database.tagDao().getAllTaskTagsOnce().isEmpty())
        assertTrue(database.countdownDao().getAllCountdownsOnce().isEmpty())
        assertNull(database.appSettingsDao().getValue("pocketbase_url"))
        val inbox = database.categoryDao().getAllCategoriesOnce().single()
        assertEquals("00000000-0000-0000-0000-000000000001", inbox.id)
        assertEquals("Inbox", inbox.name)
    }

    @Test
    fun clearLocalDataFailureLeavesPocketBaseDisconnected() = runTest {
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val service = SyncService(provider, emptyList(), accountMutationGate = mutationGate)
        val trigger = TriggerSyncAction(provider, service)
        val failingStorage = object : AttachmentFileStorage by FakeAttachmentFileStorage() {
            override suspend fun clearAll() {
                error("storage cleanup failed")
            }
        }

        assertFailsWith<IllegalStateException> {
            clearLocalDataAction(failingStorage, service, trigger)()
        }

        assertFalse(provider.isConfigured)
    }

    @Test
    fun repositoryWriteAfterResetDoesNotReconnectPocketBase() = runTest {
        val provider = PocketBaseClientProvider().apply { configure("http://localhost:8090") }
        val service = SyncService(provider, emptyList(), accountMutationGate = mutationGate)
        val trigger = TriggerSyncAction(provider, service)
        clearLocalDataAction(FakeAttachmentFileStorage(), service, trigger)()
        val repository = TaskRepositoryImpl(database.taskDao(), trigger, database, mutationGate = mutationGate)

        repository.insert(testTask(id = "post-reset"))

        assertNotNull(database.taskDao().getTaskById("post-reset"))
        assertFalse(provider.isConfigured)
    }

    private fun clearLocalDataAction(
        storage: AttachmentFileStorage,
        service: SyncService,
        trigger: TriggerSyncAction,
    ) = ClearLocalDataAction(
        database = database,
        attachmentFileStorage = storage,
        syncService = service,
        triggerSyncAction = trigger,
        mutationGate = MutexAccountMutationGate(),
    )

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
