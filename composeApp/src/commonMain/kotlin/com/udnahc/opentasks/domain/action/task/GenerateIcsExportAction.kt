package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.IcsGenerator
import com.udnahc.opentasks.data.repository.TaskRepository

class GenerateIcsExportAction(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(): Pair<String, Int> {
        val tasks = taskRepository.getAllTasksOnceUtc()
        val ics = IcsGenerator.generate(tasks)
        return ics to tasks.size
    }
}
