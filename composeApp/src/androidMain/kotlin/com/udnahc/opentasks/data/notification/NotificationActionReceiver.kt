package com.udnahc.opentasks.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.repository.TaskRepository
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

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
        log.d { "Notification action received: ${intent.action} for task $taskId" }

        when (intent.action) {
            NotificationScheduler.ACTION_GOT_IT -> {
                notificationScheduler.stopOngoing(taskId)
            }
            NotificationScheduler.ACTION_MARK_DONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val task = taskRepository.getTaskById(taskId)
                        if (task != null) {
                            taskRepository.update(
                                task.copy(isCompleted = true, updatedAt = utcNow())
                            )
                        }
                        notificationScheduler.cancelAll(taskId)
                    } catch (e: Exception) {
                        log.e { "Failed to handle Mark Done action for task $taskId: ${e.message}" }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
