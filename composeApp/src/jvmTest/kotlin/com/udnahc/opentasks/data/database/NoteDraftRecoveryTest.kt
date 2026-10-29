package com.udnahc.opentasks.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
import com.udnahc.opentasks.data.repository.NoteRepositoryImpl
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.testutil.testNote
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteDraftRecoveryTest {
    private lateinit var databaseFile: File
    private lateinit var database: AppDatabase
    private val gate = MutexAccountMutationGate()
    private var syncRequests = 0
    private val repository by lazy {
        NoteRepositoryImpl(
            noteDao = database.noteDao(),
            syncTrigger = object : SyncTrigger {
                override suspend fun triggerSync() { syncRequests += 1 }
            },
            mutationGate = gate,
        )
    }

    @BeforeTest
    fun createDatabase() {
        databaseFile = File.createTempFile("note-draft-recovery", ".db")
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
    fun updateQueuedBehindRemoteDeletionCannotResurrectNoteAndDraftCanBeSavedAsNew() = runTest {
        val opened = testNote(id = "deleted-note", title = "Original", content = "Original body")
        database.noteDao().insert(opened)
        val draft = opened.copy(title = "Unsaved title", content = "<p>Unsaved body</p>")
        val tombstone = opened.copy(isDeleted = true, isSynced = true, updatedAt = 500L)
        val deletionHasGate = CompletableDeferred<Unit>()
        val allowDeletion = CompletableDeferred<Unit>()
        val deleteJob = launch {
            gate.withExclusive {
                deletionHasGate.complete(Unit)
                allowDeletion.await()
                database.noteDao().upsert(tombstone)
            }
        }
        deletionHasGate.await()
        val queuedUpdate = async {
            assertFailsWith<IllegalStateException> { repository.updateCommitted(draft) }
        }
        allowDeletion.complete(Unit)
        deleteJob.join()
        queuedUpdate.await()

        assertEquals(tombstone, database.noteDao().findNoteByIdAnyState(opened.id))
        assertNull(repository.getNoteById(opened.id))
        assertEquals(0, syncRequests)

        val restored = AddNoteAction(repository)(draft.title, draft.content).value
        assertNotEquals(opened.id, restored.id)
        assertEquals(draft.title, restored.title)
        assertEquals(draft.content, restored.content)
        assertNotNull(repository.getNoteById(restored.id))
        assertEquals(tombstone, database.noteDao().findNoteByIdAnyState(opened.id))
        assertEquals(1, syncRequests)
    }

    @Test
    fun staleDeleteLeavesRemoteTombstoneUntouchedWhileLiveUpdateAndDeleteRemainSupported() = runTest {
        val opened = testNote(id = "note", title = "Original")
        repository.insert(opened)
        val edited = repository.updateCommitted(opened.copy(title = "Edited")).value
        assertEquals("Edited", repository.getNoteById(opened.id)?.title)
        repository.deleteCommitted(edited)
        val tombstone = assertNotNull(database.noteDao().findNoteByIdAnyState(opened.id))
        assertTrue(tombstone.isDeleted)
        val committedRequests = syncRequests

        assertFailsWith<IllegalStateException> { repository.deleteCommitted(opened) }
        assertEquals(tombstone, database.noteDao().findNoteByIdAnyState(opened.id))
        assertEquals(committedRequests, syncRequests)
    }
}
