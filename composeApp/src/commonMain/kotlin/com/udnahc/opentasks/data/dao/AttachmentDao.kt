package com.udnahc.opentasks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.udnahc.opentasks.data.model.Attachment
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Update
    suspend fun update(attachment: Attachment)

    @Delete
    suspend fun delete(attachment: Attachment)

    @Upsert
    suspend fun upsert(attachment: Attachment)

    @Query(
        """
        SELECT * FROM attachments
        WHERE ownerType = :ownerType AND ownerId = :ownerId AND kind = :kind AND isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC
        """
    )
    fun observeForOwner(ownerType: String, ownerId: String, kind: String): Flow<List<Attachment>>

    @Query(
        """
        SELECT * FROM attachments
        WHERE kind = 'image' AND isDeleted = 0
        ORDER BY ownerType ASC, ownerId ASC, sortOrder ASC, createdAt ASC
        """
    )
    fun observeActiveImagesOrdered(): Flow<List<Attachment>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId AND kind = :kind")
    suspend fun maxSortOrder(ownerType: String, ownerId: String, kind: String): Int

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun findByIdAnyState(id: String): Attachment?

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId AND isDeleted = 0")
    suspend fun getActiveForOwnerAnyState(ownerType: String, ownerId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Attachment>

    @Query("SELECT * FROM attachments")
    suspend fun getAllOnce(): List<Attachment>

    @Query("UPDATE attachments SET isSynced = 1, syncState = 'SYNCED', lastSyncError = NULL WHERE id = :id AND updatedAt = :updatedAt AND isDeleted = :isDeleted")
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long, isDeleted: Boolean): Int

    @Query("UPDATE attachments SET syncState = :syncState, lastSyncError = :error, isSynced = 0 WHERE id = :id")
    suspend fun markSyncFailed(id: String, syncState: String, error: String?)

    @Query("UPDATE attachments SET pbId = :pbId WHERE id = :id")
    suspend fun updatePbId(id: String, pbId: String)

    @Query("UPDATE attachments SET remoteFileName = :remoteFileName WHERE id = :id")
    suspend fun updateRemoteFileName(id: String, remoteFileName: String?)

    @Query("UPDATE attachments SET isSynced = 0, syncState = 'LOCAL_ONLY', lastSyncError = NULL WHERE id = :id")
    suspend fun markUnsynced(id: String)

    @Query("UPDATE attachments SET isDeleted = 1, isSynced = 0, syncState = 'LOCAL_ONLY', updatedAt = :updatedAt WHERE ownerType = :ownerType AND ownerId = :ownerId AND isDeleted = 0")
    suspend fun tombstoneActiveForOwner(ownerType: String, ownerId: String, updatedAt: Long)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
