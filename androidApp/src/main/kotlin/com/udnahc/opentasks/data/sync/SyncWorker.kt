package com.udnahc.opentasks.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("SyncWorker")

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncService: SyncService by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val widgetAccountGate: WidgetAccountGate by inject()

    override suspend fun doWork(): Result {
        log.d { "SyncWorker starting" }
        return try {
            val expectedAccountId = inputData.getString(KEY_ACCOUNT_ID)
            val expectedEpoch = inputData.getLong(KEY_BOUNDARY_EPOCH, 0L)
            val maintained = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                if (expectedAccountId.isNullOrBlank() ||
                    expectedEpoch <= 0L ||
                    expectedAccountId != boundary.accountId ||
                    expectedEpoch != boundary.boundaryEpoch
                ) {
                    log.d { "SyncWorker skipped stale account boundary" }
                    return@withAuthenticatedBoundary false
                }
                runScheduledSyncMaintenance(
                    syncNetwork = syncService::syncActiveClientWithinMutation,
                    rebuildReminders = { rebuildReminderQueueAction() },
                    refreshWidgets = {
                        TaskWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary)
                        CalendarWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary)
                        WeekWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary)
                    },
                )
                true
            }
            if (maintained != true) {
                log.d { "SyncWorker skipped: no authenticated account boundary" }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "SyncWorker failed, retrying" }
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_BOUNDARY_EPOCH = "boundary_epoch"
    }
}

/** A missing stable client skips only the network pass; local maintenance always runs. */
internal suspend fun runScheduledSyncMaintenance(
    syncNetwork: suspend () -> Boolean,
    rebuildReminders: suspend () -> Unit,
    refreshWidgets: suspend () -> Unit,
) {
    var syncFailure: Exception? = null
    try {
        if (!syncNetwork()) log.d { "SyncWorker skipped network sync: no stable active PocketBase client" }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        syncFailure = e
    }

    val maintenanceFailures = mutableListOf<Exception>()
    try {
        rebuildReminders()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        maintenanceFailures += e
    }
    try {
        refreshWidgets()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        maintenanceFailures += e
    }

    val originalSyncFailure = syncFailure
    if (originalSyncFailure != null) {
        maintenanceFailures.forEach(originalSyncFailure::addSuppressed)
        throw originalSyncFailure
    }
    if (maintenanceFailures.isNotEmpty()) {
        val maintenanceFailure = maintenanceFailures.first()
        maintenanceFailures.drop(1).forEach(maintenanceFailure::addSuppressed)
        throw maintenanceFailure
    }
}
