package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.NoteDao
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.lighthousegames.logging.logging

private val log = logging("NoteRepository")

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val triggerSyncAction: TriggerSyncAction,
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes()
            .map { notes -> notes.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getNoteById(id: String): Note? =
        noteDao.getNoteById(id)?.withLocalTimestamps()

    override suspend fun insert(note: Note) {
        log.v { "Inserting note: ${note.id}" }
        noteDao.insert(note.withUtcTimestamps())
        triggerSyncAction()
    }

    override suspend fun update(note: Note) {
        log.v { "Updating note: ${note.id}, content has newlines=${'\n' in note.content}" }
        noteDao.update(note.withUtcTimestamps().copy(isSynced = false))
        triggerSyncAction()
    }

    override suspend fun delete(note: Note) {
        log.v { "Deleting note: ${note.id}" }
        noteDao.delete(note)
        triggerSyncAction()
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
