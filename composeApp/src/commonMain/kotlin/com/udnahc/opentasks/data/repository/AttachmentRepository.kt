package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Attachment
import com.udnahc.opentasks.data.model.AttachmentSummary
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun observeForOwner(ownerType: String, ownerId: String, kind: String): Flow<List<Attachment>>
    fun observeTaskImageSummaries(): Flow<List<AttachmentSummary>>
    suspend fun getByIdAnyState(id: String): Attachment?
    suspend fun nextSortOrder(ownerType: String, ownerId: String, kind: String): Int
    suspend fun insert(attachment: Attachment): Long
    suspend fun update(attachment: Attachment)
    suspend fun delete(attachment: Attachment)
    suspend fun hardDelete(attachment: Attachment)
    suspend fun tombstoneActiveForOwner(ownerType: String, ownerId: String)
}
