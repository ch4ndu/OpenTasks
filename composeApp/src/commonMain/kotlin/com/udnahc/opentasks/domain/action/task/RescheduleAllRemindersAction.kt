package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.repository.TaskRepository

class RescheduleAllRemindersAction(
    private val taskRepository: TaskRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke() {
        val tasks = taskRepository.getTasksWithDeadlines()
        tasks.forEach { scheduleTaskRemindersAction(it) }
    }
}
