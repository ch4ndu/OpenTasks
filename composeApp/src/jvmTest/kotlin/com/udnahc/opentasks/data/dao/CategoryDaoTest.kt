package com.udnahc.opentasks.data.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.sync.RemoteMergeResult
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryDaoTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("opentasks-category-test", ".db")
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
    fun pristineInboxPlaceholderYieldsToEqualTimestampRemotePayload() = runTest {
        val placeholder = Category(
            id = AppConstants.DEFAULT_INBOX_ID,
            name = "Inbox",
        )
        val remote = placeholder.copy(
            name = "Server Inbox",
            pbId = "remote-id",
            isSynced = true,
        )
        database.categoryDao().insert(placeholder)

        val result = database.categoryDao().mergeRemoteIfNewer(remote)

        assertEquals(RemoteMergeResult.Applied, result)
        assertEquals(remote, database.categoryDao().findCategoryByIdAnyState(remote.id))
    }

    @Test
    fun genuineEqualTimestampCategoryRemainsAConflictCandidate() = runTest {
        val local = Category(
            id = AppConstants.DEFAULT_INBOX_ID,
            name = "Local Inbox",
            updatedAt = 42L,
        )
        val remote = local.copy(
            name = "Server Inbox",
            pbId = "remote-id",
            isSynced = true,
        )
        database.categoryDao().insert(local)

        val result = database.categoryDao().mergeRemoteIfNewer(remote)

        assertEquals(RemoteMergeResult.KeptLocal, result)
        assertEquals(local, database.categoryDao().findCategoryByIdAnyState(local.id))
    }
}
