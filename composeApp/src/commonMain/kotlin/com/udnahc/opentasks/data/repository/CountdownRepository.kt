package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Countdown
import kotlinx.coroutines.flow.Flow

interface CountdownRepository {
    fun getAllCountdowns(): Flow<List<Countdown>>
    fun observeCountdownById(id: String): Flow<Countdown?>
    suspend fun getCountdownById(id: String): Countdown?
    suspend fun getCountdownByIdUtc(id: String): Countdown?
    /** Includes tombstones so reminder reconciliation can cancel removed countdown occurrences. */
    suspend fun getAllCountdownsForReminderReconciliationUtc(): List<Countdown>
    suspend fun insert(countdown: Countdown): CommittedMutation<Countdown>
    suspend fun update(countdown: Countdown): CommittedMutation<Countdown>
    suspend fun delete(countdown: Countdown): CommittedMutation<Countdown>
}
