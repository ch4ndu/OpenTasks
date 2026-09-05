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
            if (expectedAccountId.isNullOrBlank() || expectedEpoch <= 0L) {
                log.d { "SyncWorker skipped malformed account boundary" }
                return Result.success()
            }
            val capturedBoundary = widgetAccountGate.withActiveCacheBoundary(
                expectedAccountId = expectedAccountId,
                expectedBoundaryEpoch = expectedEpoch,
            ) { boundary ->
                boundary
            }
            if (capturedBoundary == null) {
                log.d { "SyncWorker skipped stale or unavailable account boundary" }
                return Result.success()
            }
            runScheduledSyncMaintenance(
                capturedBoundary = capturedBoundary,
                syncNetwork = { syncService.syncActiveClientWithinMutation() },
                withRevalidatedBoundary = { expected, block ->
                    widgetAccountGate.withForegroundBoundary(expected, block)
                },
                maintenanceSteps = listOf(
                    { rebuildReminderQueueAction() },
                    { boundary -> TaskWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary) },
                    { boundary -> CalendarWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary) },
                    { boundary -> WeekWidget.refreshAllWidgetsWithinBoundary(applicationContext, boundary) },
                ),
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            log.e { "SyncWorker failed, retrying" }
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_BOUNDARY_EPOCH = "boundary_epoch"
    }
}
