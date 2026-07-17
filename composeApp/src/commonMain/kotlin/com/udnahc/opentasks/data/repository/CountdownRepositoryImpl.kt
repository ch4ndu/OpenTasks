package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Countdown
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

private val log = logging("CountdownRepository")

class CountdownRepositoryImpl(
    private val countdownDao: CountdownDao,
    private val syncTrigger: SyncTrigger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CountdownRepository {

    override fun getAllCountdowns(): Flow<List<Countdown>> =
        countdownDao.getAllCountdowns()
            .map { countdowns -> countdowns.map { it.withLocalTimestamps() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeCountdownById(id: String): Flow<Countdown?> =
        countdownDao.observeCountdownById(id)
            .map { it?.withLocalTimestamps() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun getCountdownById(id: String): Countdown? =
        withContext(ioDispatcher) { countdownDao.getCountdownById(id)?.withLocalTimestamps() }

    override suspend fun getCountdownByIdUtc(id: String): Countdown? =
        withContext(ioDispatcher) { countdownDao.getCountdownByIdUtc(id) }

    override suspend fun getAllCountdownsForReminderReconciliationUtc(): List<Countdown> =
        withContext(ioDispatcher) { countdownDao.getAllCountdownsForReminderReconciliationUtc() }

    override suspend fun insert(countdown: Countdown) {
        log.v { "Inserting countdown: ${countdown.id}" }
        withContext(ioDispatcher) {
            countdownDao.insert(countdown.withDefaultTimestamps().withUtcTimestamps())
        }
        syncTrigger.triggerSync()
    }

    override suspend fun update(countdown: Countdown) {
        log.v { "Updating countdown: ${countdown.id}" }
        withContext(ioDispatcher) {
            countdownDao.update(countdown.withUtcTimestamps().copy(isSynced = false))
        }
        syncTrigger.triggerSync()
    }

    override suspend fun delete(countdown: Countdown) {
        log.v { "Soft-deleting countdown: ${countdown.id}" }
        withContext(ioDispatcher) {
            countdownDao.update(
                countdown.withUtcTimestamps().copy(
                    isDeleted = true,
                    isSynced = false,
                    updatedAt = localToUtc(localNow()),
                )
            )
        }
        syncTrigger.triggerSync()
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
