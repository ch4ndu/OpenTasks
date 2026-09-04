package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: String): Note?
    fun observeNoteById(id: String): Flow<Note?>
    suspend fun insert(note: Note)
    suspend fun insertCommitted(note: Note): CommittedMutation<Note> {
        insert(note)
        return CommittedMutation(note)
    }
    suspend fun update(note: Note)
    suspend fun updateCommitted(note: Note): CommittedMutation<Note> {
        update(note)
        return CommittedMutation(note)
    }
    suspend fun delete(note: Note)
    suspend fun deleteCommitted(note: Note): CommittedMutation<Note> {
        delete(note)
        return CommittedMutation(note)
    }
}
