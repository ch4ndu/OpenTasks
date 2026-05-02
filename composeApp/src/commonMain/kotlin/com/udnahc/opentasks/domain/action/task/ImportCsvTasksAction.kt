package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.CsvTask
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import org.lighthousegames.logging.logging

private val log = logging("ImportCsvTasksAction")

class ImportCsvTasksAction(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
) {
    suspend operator fun invoke(tasks: List<CsvTask>): Int {
        log.d { "Importing ${tasks.size} CSV tasks" }
        if (tasks.isEmpty()) return 0

        val categoryCache = mutableMapOf<String, String>()
        val now = localNow()
        var importedCount = 0

        for (csvTask in tasks) {
            val externalId = "csv_${csvTask.title.hashCode()}_${csvTask.createdAt}"
            if (taskRepository.getTaskByExternalId(externalId) != null) continue

            val categoryId = resolveCategory(csvTask.listName, categoryCache)

            // Convert external UTC timestamps to local millis for repository
            val task = Task(
                title = csvTask.title,
                content = csvTask.content,
                priority = csvTask.priority,
                deadline = csvTask.startDate?.let { utcToLocal(it) },
                endDeadline = csvTask.dueDate?.let { utcToLocal(it) },
                isAllDay = csvTask.isAllDay,
                status = if (csvTask.isCompleted) TaskStatus.DONE else TaskStatus.TODO,
                recurrenceType = csvTask.recurrenceType,
                categoryId = categoryId,
                durationReminders = csvTask.durationReminders,
                sourceExternalId = externalId,
                createdAt = if (csvTask.createdAt > 0) utcToLocal(csvTask.createdAt) else now,
                updatedAt = now,
            )
            taskRepository.insert(task)
            scheduleTaskRemindersAction(task.id)
            importedCount++
        }

        log.d { "Imported $importedCount CSV tasks" }
        return importedCount
    }

    private suspend fun resolveCategory(
        name: String,
        cache: MutableMap<String, String>,
    ): String {
        cache[name]?.let { return it }

        if (name.equals("Inbox", ignoreCase = true)) {
            cache[name] = DEFAULT_INBOX_ID
            return DEFAULT_INBOX_ID
        }

        val existing = categoryRepository.getCategoryByName(name)
        if (existing != null) {
            cache[name] = existing.id
            return existing.id
        }

        val now = localNow()
        val newCategory = Category(name = name, icon = "inbox", createdAt = now)
        categoryRepository.insert(newCategory)
        cache[name] = newCategory.id
        return newCategory.id
    }

    companion object {
        private const val DEFAULT_INBOX_ID = AppConstants.DEFAULT_INBOX_ID
    }
}
