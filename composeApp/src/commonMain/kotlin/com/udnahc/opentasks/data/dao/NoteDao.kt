package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id AND isDeleted = 0")
    suspend fun getNoteById(id: String): Note?

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findNoteByIdAnyState(id: String): Note?

    @Query("SELECT * FROM notes WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Note>

    @Query("UPDATE notes SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE notes SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(id: String, pbId: String)

    @Upsert
    suspend fun upsert(note: Note)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesOnce(): List<Note>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
