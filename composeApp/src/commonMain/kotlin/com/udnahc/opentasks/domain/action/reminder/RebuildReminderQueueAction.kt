package com.udnahc.opentasks.domain.action.reminder

import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.pendingReminderQueueLimit
import com.udnahc.opentasks.data.notification.selectFairReminderQueue
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import kotlin.coroutines.cancellation.CancellationException
import org.lighthousegames.logging.logging

private val log = logging("RebuildReminderQueueAction")
private const val MAX_GENERATED_OCCURRENCES_PER_EVENT = 60

class RebuildReminderQueueAction(
    private val taskRepository: TaskRepository,
    private val countdownRepository: CountdownRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
    private val scheduler: ReminderScheduler,
    private val pendingQueueLimit: () -> Int? = ::pendingReminderQueueLimit,
) {
    /**
     * Keep the platform's existing queue strategy after a committed record
     * change. Android has no pending queue limit and schedules directly;
     * bounded platforms rebuild their selected queue for fairness and caps.
     */
    suspend fun afterRecordChange(scheduleDirectly: suspend () -> Unit) {
        if (pendingQueueLimit() == null) scheduleDirectly() else invoke()
    }

    suspend fun afterRecordChangeResult(
        scheduleDirectly: suspend () -> Unit,
    ): Throwable? = try {
        afterRecordChange(scheduleDirectly)
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w(error) { "Reminder maintenance failed after a committed write" }
        error
    }

    suspend operator fun invoke() {
        val tasks = taskRepository.getAllTasksForReminderReconciliationUtc()
        val countdowns = countdownRepository.getAllCountdownsForReminderReconciliationUtc()
        val limit = pendingQueueLimit()
        if (limit == null) {
            log.d { "Rebuilding reminders for ${tasks.size} tasks and ${countdowns.size} countdowns" }
            tasks.forEach { scheduleTaskRemindersAction.invokeWithUtcTask(it) }
            countdowns.forEach { scheduleCountdownRemindersAction.invokeWithUtcCountdown(it) }
            return
        }

        val candidates = tasks.flatMap {
            scheduleTaskRemindersAction.buildFutureRequests(
                it,
                MAX_GENERATED_OCCURRENCES_PER_EVENT,
            )
        } + countdowns.flatMap {
            scheduleCountdownRemindersAction.buildFutureRequests(
                it,
                MAX_GENERATED_OCCURRENCES_PER_EVENT,
            )
        }
        val selected = selectFairReminderQueue(candidates, limit)
        log.d { "Replacing bounded reminder queue with ${selected.size} of ${candidates.size} candidates" }
        scheduler.replacePendingReminders(selected)
    }
}
