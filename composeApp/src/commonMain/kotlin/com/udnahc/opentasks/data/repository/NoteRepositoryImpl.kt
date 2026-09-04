package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("NoteRepository")

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mutationGate: AccountMutationGate,
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes()
            .map { notes -> notes.map { it.withLocalTimestamps() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getNoteById(id: String): Note? =
        withContext(ioDispatcher) { noteDao.getNoteById(id)?.withLocalTimestamps() }

    override fun observeNoteById(id: String): Flow<Note?> =
        noteDao.observeNoteById(id)
            .map { it?.withLocalTimestamps() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun insert(note: Note) {
        insertCommitted(note)
    }

    override suspend fun insertCommitted(note: Note): CommittedMutation<Note> =
        mutationGate.withExclusive {
            log.v { "Inserting note" }
            val committed = note.withDefaultTimestamps()
            withContext(ioDispatcher) {
                noteDao.insert(committed.withUtcTimestamps())
            }
            committed.withSyncWarning()
        }

    override suspend fun update(note: Note) {
        updateCommitted(note)
    }

    override suspend fun updateCommitted(note: Note): CommittedMutation<Note> =
        mutationGate.withExclusive {
            log.v { "Updating note" }
            val committed = note.copy(
                isSynced = false,
                updatedAt = maxOf(localNow(), note.updatedAt),
            )
            withContext(ioDispatcher) {
                noteDao.update(committed.withUtcTimestamps())
            }
            committed.withSyncWarning()
        }

    override suspend fun delete(note: Note) {
        deleteCommitted(note)
    }

    override suspend fun deleteCommitted(note: Note): CommittedMutation<Note> =
        mutationGate.withExclusive {
            log.v { "Soft-deleting note" }
            val committed = note.copy(
                isDeleted = true,
                isSynced = false,
                updatedAt = maxOf(localNow(), note.updatedAt),
            )
            withContext(ioDispatcher) {
                noteDao.update(committed.withUtcTimestamps())
            }
            committed.withSyncWarning()
        }

    private suspend fun Note.withSyncWarning(): CommittedMutation<Note> =
        CommittedMutation(this).withPostCommitWarning(
            warning = triggerSyncAfterCommit(),
            phase = PostCommitWarningPhase.SYNC,
        )

    private suspend fun triggerSyncAfterCommit(): Throwable? = try {
        syncTrigger.triggerSync()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w { "Note write committed, but sync scheduling failed" }
        error
    }

    private fun Note.withDefaultTimestamps(): Note {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Note.withLocalTimestamps() = copy(
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )

    /** Converts local-shifted timestamps to UTC for database storage. */
    private fun Note.withUtcTimestamps() = copy(
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
