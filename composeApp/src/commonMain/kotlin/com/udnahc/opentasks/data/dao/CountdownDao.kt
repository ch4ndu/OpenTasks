package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Countdown
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    @Insert
    suspend fun insert(countdown: Countdown): Long

    @Update
    suspend fun update(countdown: Countdown)

    @Delete
    suspend fun delete(countdown: Countdown)

    @Upsert
    suspend fun upsert(countdown: Countdown)

    @Query("SELECT * FROM countdowns WHERE isDeleted = 0 ORDER BY targetDate ASC")
    fun getAllCountdowns(): Flow<List<Countdown>>

    @Query("SELECT * FROM countdowns WHERE id = :id AND isDeleted = 0")
    suspend fun getCountdownById(id: String): Countdown?

    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun getCountdownByIdUtc(id: String): Countdown?

    @Query("SELECT * FROM countdowns")
    suspend fun getCountdownsWithTargetsUtc(): List<Countdown>

    @Query("SELECT * FROM countdowns WHERE id = :id AND isDeleted = 0")
    fun observeCountdownById(id: String): Flow<Countdown?>

    /** Unfiltered lookup including soft-deleted rows. For sync use only. */
    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun findCountdownByIdAnyState(id: String): Countdown?

    @Query("SELECT * FROM countdowns WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Countdown>

    @Query("UPDATE countdowns SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE countdowns SET isSynced = 1 WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(
        id: String,
        updatedAt: Long,
        isDeleted: Boolean
    ): Int

    @Query("UPDATE countdowns SET isSynced = 0 WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE countdowns SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(
        id: String,
        pbId: String
    )

    @Query("SELECT * FROM countdowns")
    suspend fun getAllCountdownsOnce(): List<Countdown>

    @Query("DELETE FROM countdowns")
    suspend fun deleteAll()
}
