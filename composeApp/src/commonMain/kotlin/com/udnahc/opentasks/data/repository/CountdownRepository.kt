package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.model.Countdown
import kotlinx.coroutines.flow.Flow

interface CountdownRepository {
    fun getAllCountdowns(): Flow<List<Countdown>>
    fun observeCountdownById(id: String): Flow<Countdown?>
    suspend fun getCountdownById(id: String): Countdown?
    suspend fun insert(countdown: Countdown)
    suspend fun update(countdown: Countdown)
    suspend fun delete(countdown: Countdown)
}
