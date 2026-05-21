package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationActionReceiver")

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val taskRepository: TaskRepository by inject()
    private val notificationScheduler: NotificationScheduler by inject()
    private val toggleTaskCompleteAction: ToggleTaskCompleteAction by inject()
    private val allDayNotificationDismissalStore: AllDayNotificationDismissalStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
        log.d { "Notification action received: ${intent.action} for task $taskId" }

        when (intent.action) {
            NotificationScheduler.ACTION_GOT_IT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        allDayNotificationDismissalStore.dismissToday(taskId)
                        notificationScheduler.stopOngoing(taskId)
                    } catch (e: Exception) {
                        log.e(e) { "Failed to dismiss all-day notification for task $taskId" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            NotificationScheduler.ACTION_MARK_DONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val task = taskRepository.getTaskById(taskId)
                        if (task == null) {
                            log.d { "Task $taskId not found for Mark Done action" }
                            return@launch
                        }
                        val occurrenceDeadlineLocalMillis = intent.occurrenceDeadlineUtcMillis()?.let { utcToLocal(it) }
                        if (task.status == TaskStatus.DONE) {
                            NotificationScheduler.cancelDisplayedReminders(context, taskId)
                            notificationScheduler.stopOngoing(taskId)
                            return@launch
                        }
                        if (
                            occurrenceDeadlineLocalMillis != null &&
                            task.deadline != null &&
                            task.deadline > occurrenceDeadlineLocalMillis
                        ) {
                            log.d { "Ignoring stale Mark Done action for task $taskId" }
                            NotificationScheduler.cancelDisplayedReminders(context, taskId)
                            notificationScheduler.stopOngoing(taskId)
                            return@launch
                        }
                        // Use ToggleTaskCompleteAction so recurring tasks advance correctly.
                        toggleTaskCompleteAction(
                            task = task,
                            occurrenceDeadlineLocalMillis = occurrenceDeadlineLocalMillis,
                        )
                        NotificationScheduler.cancelDisplayedReminders(context, taskId)
                        notificationScheduler.stopOngoing(taskId)
                    } catch (e: Exception) {
                        log.e(e) { "Failed to handle Mark Done action for task $taskId" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun Intent.occurrenceDeadlineUtcMillis(): Long? =
        if (hasExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC)) {
            getLongExtra(NotificationScheduler.EXTRA_OCCURRENCE_DEADLINE_UTC, 0L)
        } else {
            null
        }
}
