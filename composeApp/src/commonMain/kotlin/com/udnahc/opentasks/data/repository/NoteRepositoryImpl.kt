package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.sync.SyncTrigger
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

    override suspend fun insert(note: Note) = mutationGate.withExclusive {
        log.v { "Inserting note: ${note.id}" }
        withContext(ioDispatcher) {
            noteDao.insert(note.withDefaultTimestamps().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
    }

    override suspend fun update(note: Note) = mutationGate.withExclusive {
        log.v { "Updating note: ${note.id}" }
        withContext(ioDispatcher) {
            noteDao.update(note.withUtcTimestamps().copy(isSynced = false))
        }
        syncTrigger.triggerSync()
    }

    override suspend fun delete(note: Note) = mutationGate.withExclusive {
        log.v { "Soft-deleting note: ${note.id}" }
        withContext(ioDispatcher) {
            noteDao.update(
                note.withUtcTimestamps().copy(
                    isDeleted = true,
                    isSynced = false,
                    updatedAt = localToUtc(localNow()),
                )
            )
        }
        syncTrigger.triggerSync()
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
