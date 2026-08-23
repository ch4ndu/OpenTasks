package com.udnahc.opentasks.data.repository

import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.sync.SyncTrigger
import kotlinx.coroutines.CancellationException
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
    private val mutationGate: AccountMutationGate,
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

    override suspend fun insert(countdown: Countdown): CommittedMutation<Countdown> = mutationGate.withExclusive {
        log.v { "Inserting countdown: ${countdown.id}" }
        val committed = countdown.withDefaultTimestamps()
        withContext(ioDispatcher) {
            countdownDao.insert(committed.withUtcTimestamps())
        }
        CommittedMutation(committed).withPostCommitWarning(
            triggerSyncAfterCommit(),
            PostCommitWarningPhase.SYNC,
        )
    }

    override suspend fun update(countdown: Countdown): CommittedMutation<Countdown> = mutationGate.withExclusive {
        log.v { "Updating countdown: ${countdown.id}" }
        val committed = countdown.copy(
            isSynced = false,
            updatedAt = maxOf(localNow(), countdown.updatedAt),
        )
        withContext(ioDispatcher) {
            countdownDao.update(committed.withUtcTimestamps())
        }
        CommittedMutation(committed).withPostCommitWarning(
            triggerSyncAfterCommit(),
            PostCommitWarningPhase.SYNC,
        )
    }

    override suspend fun delete(countdown: Countdown): CommittedMutation<Countdown> = mutationGate.withExclusive {
        log.v { "Soft-deleting countdown: ${countdown.id}" }
        val committed = countdown.copy(
            isDeleted = true,
            isSynced = false,
            updatedAt = maxOf(localNow(), countdown.updatedAt),
        )
        withContext(ioDispatcher) {
            countdownDao.update(committed.withUtcTimestamps())
        }
        CommittedMutation(committed).withPostCommitWarning(
            triggerSyncAfterCommit(),
            PostCommitWarningPhase.SYNC,
        )
    }

    private suspend fun triggerSyncAfterCommit(): Throwable? = try {
        syncTrigger.triggerSync()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w(error) { "Countdown write committed, but sync scheduling failed" }
        error
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
