package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.lighthousegames.logging.logging

private val log = logging("CountdownRepository")

class CountdownRepositoryImpl(
    private val countdownDao: CountdownDao,
    private val triggerSyncAction: TriggerSyncAction,
) : CountdownRepository {

    override fun getAllCountdowns(): Flow<List<Countdown>> =
        countdownDao.getAllCountdowns()
            .map { countdowns -> countdowns.map { it.withLocalTimestamps() } }
            .flowOn(Dispatchers.Default)

    override fun observeCountdownById(id: String): Flow<Countdown?> =
        countdownDao.observeCountdownById(id)
            .map { it?.withLocalTimestamps() }
            .flowOn(Dispatchers.Default)

    override suspend fun getCountdownById(id: String): Countdown? =
        countdownDao.getCountdownById(id)?.withLocalTimestamps()

    override suspend fun insert(countdown: Countdown) {
        log.v { "Inserting countdown: ${countdown.id}" }
        countdownDao.insert(countdown.withDefaultTimestamps().withUtcTimestamps())
        triggerSyncAction()
    }

    override suspend fun update(countdown: Countdown) {
        log.v { "Updating countdown: ${countdown.id}" }
        countdownDao.update(countdown.withUtcTimestamps().copy(isSynced = false))
        triggerSyncAction()
    }

    override suspend fun delete(countdown: Countdown) {
        log.v { "Soft-deleting countdown: ${countdown.id}" }
        countdownDao.update(countdown.withUtcTimestamps().copy(isDeleted = true, isSynced = false))
        triggerSyncAction()
    }

    private fun Countdown.withDefaultTimestamps(): Countdown {
        val now = localNow()
        return copy(
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = if (updatedAt == 0L) now else updatedAt,
        )
    }

    /** Converts UTC timestamps from the database to local time for presentation. */
    private fun Countdown.withLocalTimestamps() = copy(
        targetDate = utcToLocal(targetDate),
        createdAt = utcToLocal(createdAt),
        updatedAt = utcToLocal(updatedAt)
    )

    /** Converts local-shifted timestamps to UTC for database storage. */
    private fun Countdown.withUtcTimestamps() = copy(
        targetDate = localToUtc(targetDate),
        createdAt = localToUtc(createdAt),
        updatedAt = localToUtc(updatedAt),
    )
}
