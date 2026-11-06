package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.model.NoteOrganization
import com.udnahc.opentasks.data.model.organization
import com.udnahc.opentasks.data.repository.NoteRepositoryImpl
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.testutil.testNote
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NoteOrganizationPersistenceTest {
    @Test
    fun migrationPreservesExistingNotesAndUsesSafeDefaults() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY, title TEXT, content TEXT, isDeleted INTEGER)")
            connection.execSQL("INSERT INTO notes VALUES ('existing', 'Title', '<p>Body</p>', 0), ('deleted', 'Deleted', '', 1)")
            MIGRATION_16_17.migrate(connection)
            connection.prepare("SELECT title, content, isPinned, isArchived, tagIds, linkedNoteIds FROM notes WHERE id = 'existing'").use {
                assertTrue(it.step())
                assertEquals("Title", it.getText(0))
                assertEquals("<p>Body</p>", it.getText(1))
                assertEquals(0L, it.getLong(2))
                assertEquals(0L, it.getLong(3))
                assertEquals("[]", it.getText(4))
                assertEquals("[]", it.getText(5))
            }
            connection.prepare("SELECT isDeleted FROM notes WHERE id = 'deleted'").use {
                assertTrue(it.step()); assertEquals(1L, it.getLong(0))
            }
        } finally { connection.close() }
    }

    @Test
    fun saveReopenArchiveRestoreAndDeletePreserveOrganizationAndRemoteIdentity() = runTest {
        val file = File.createTempFile("note-organization", ".db")
        val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).build()
        try {
            val repository = NoteRepositoryImpl(database.noteDao(), object : SyncTrigger {
                override suspend fun triggerSync() = Unit
            }, mutationGate = MutexAccountMutationGate())
            val organization = NoteOrganization(true, true, listOf("work"), listOf("other"))
            val created = AddNoteAction(repository)("Title", "<p>Body</p>", organization).value
            assertEquals(organization, repository.getNoteById(created.id)?.organization())
            val stored = assertNotNull(database.noteDao().getNoteById(created.id))
            database.noteDao().update(stored.copy(pbId = "remote-note", isSynced = true, updatedAt = stored.updatedAt + 10000))
            val restored = repository.updateCommitted(created.copy(isArchived = false)).value
            assertFalse(restored.isArchived)
            assertEquals("remote-note", restored.pbId)
            assertEquals(organization.tagIds, restored.tagIds)
            val after = assertNotNull(database.noteDao().getNoteById(created.id))
            assertEquals(stored.createdAt, after.createdAt)
            assertTrue(after.updatedAt > stored.updatedAt + 10000)
            repository.deleteCommitted(created)
            val deleted = assertNotNull(database.noteDao().findNoteByIdAnyState(created.id))
            assertTrue(deleted.isDeleted)
            assertFalse(deleted.isArchived)
            assertEquals("remote-note", deleted.pbId)
            assertEquals(listOf("other"), deleted.linkedNoteIds)
            assertNull(repository.getNoteById(created.id))
        } finally { database.close(); file.delete() }
    }
}
