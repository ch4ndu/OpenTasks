package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.CategoryRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testTask
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistenceTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

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
            triggerSyncAction = TriggerSyncAction(PocketBaseClientProvider(), SyncService(PocketBaseClientProvider(), emptyList())),
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
            triggerSyncAction = TriggerSyncAction(PocketBaseClientProvider(), SyncService(PocketBaseClientProvider(), emptyList())),
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
}
