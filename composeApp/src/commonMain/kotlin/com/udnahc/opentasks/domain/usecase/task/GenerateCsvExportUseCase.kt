package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.calendar.CsvGenerator
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.first

class GenerateCsvExportUseCase(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(): Pair<String, Int> {
        val tasks = taskRepository.getAllTasksOnceUtc()
        val categories = categoryRepository.getAllCategories().first()
        val csv = CsvGenerator.generate(tasks, categories)
        return csv to tasks.size
    }
}
