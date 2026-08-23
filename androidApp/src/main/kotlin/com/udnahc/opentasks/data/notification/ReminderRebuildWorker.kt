package com.udnahc.opentasks.data.notification

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import kotlin.coroutines.cancellation.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("ReminderRebuildWorker")

internal const val REMINDER_REBUILD_WORK_NAME = "reminder_queue_rebuild"

class ReminderRebuildWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()
    private val rebuildReminderQueueAction: RebuildReminderQueueAction by inject()
    private val notificationScheduler: NotificationScheduler by inject()

    override suspend fun doWork(): Result {
        val outcome = runReminderRebuildWorker {
            val rebuilt = accountBoundaryExecutor.withActiveCacheBoundary {
                runReminderRebuildWithinBoundary(
                    rebuild = { rebuildReminderQueueAction() },
                    cancelAll = { notificationScheduler.cancelAllAccountReminders() },
                )
            }
            if (rebuilt == null) {
                log.d { "Skipping reminder rebuild without an active cache session" }
            }
        }
        return when (outcome) {
            ReminderRebuildWorkerOutcome.Success -> Result.success()
            is ReminderRebuildWorkerOutcome.Retry -> {
                log.e(outcome.error) { "Reminder rebuild failed, retrying" }
                Result.retry()
            }
        }
    }
}

internal sealed interface ReminderRebuildWorkerOutcome {
    data object Success : ReminderRebuildWorkerOutcome
    data class Retry(val error: Exception) : ReminderRebuildWorkerOutcome
}

/**
 * Keeps the original queue rebuild error as the primary failure while a
 * cancellation cleanup failure is recorded for diagnosis and never masks it.
 * The caller already holds the active account boundary for both operations.
 */
internal suspend fun runReminderRebuildWithinBoundary(
    rebuild: suspend () -> Unit,
    cancelAll: suspend () -> Unit,
) {
    try {
        rebuild()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        try {
            cancelAll()
        } catch (cleanupError: CancellationException) {
            throw cleanupError
        } catch (cleanupError: Exception) {
            error.addSuppressed(cleanupError)
        }
        throw error
    }
}

/** Maps ordinary worker failures to retry while preserving cancellation. */
internal suspend fun runReminderRebuildWorker(
    run: suspend () -> Unit,
): ReminderRebuildWorkerOutcome = try {
    run()
    ReminderRebuildWorkerOutcome.Success
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    ReminderRebuildWorkerOutcome.Retry(error)
}

internal fun reminderRebuildExistingWorkPolicy(): ExistingWorkPolicy = ExistingWorkPolicy.REPLACE

internal fun reminderRebuildWorkRequest(
    sdkInt: Int = Build.VERSION.SDK_INT,
): OneTimeWorkRequest {
    val builder = OneTimeWorkRequestBuilder<ReminderRebuildWorker>()
    if (sdkInt >= Build.VERSION_CODES.S) {
        builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }
    return builder.build()
}
