package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("RescheduleAllRemindersAction")

class RescheduleAllRemindersAction(
    private val taskRepository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke() {
        val tasks = taskRepository.getTasksWithDeadlines()
        log.d { "Rescheduling reminders for ${tasks.size} tasks" }
        tasks.forEach { scheduleTaskRemindersAction(it) }
    }
}
