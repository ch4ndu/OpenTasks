package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.CsvTask
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("ImportCsvTasksAction")

class ImportCsvTasksAction(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val taskWriteCoordinator = TaskWriteCoordinator(taskRepository)

    suspend operator fun invoke(tasks: List<CsvTask>): Int =
        accountBoundaryExecutor.withForegroundActionBoundary {
            log.d { "Importing ${tasks.size} CSV tasks" }
            if (tasks.isEmpty()) return@withForegroundActionBoundary 0

            val categoryCache = mutableMapOf<String, String>()
            val now = localNow()
            var importedCount = 0
            val importedTaskIds = mutableListOf<String>()

            for (csvTask in tasks) {
                val externalId = "csv_${csvTask.title.hashCode()}_${csvTask.createdAt}"
                if (taskRepository.getTaskByExternalId(externalId) != null) continue

                val categoryId = resolveCategory(csvTask.listName, categoryCache)

                // Convert external UTC timestamps to local millis for repository
                val task = Task(
                    title = csvTask.title,
                    content = csvTask.content,
                    priority = csvTask.priority,
                    deadline = (csvTask.startDate ?: csvTask.dueDate)?.let { utcToLocal(it) },
                    endDeadline = if (csvTask.startDate != null && csvTask.dueDate != null) {
                        utcToLocal(csvTask.dueDate)
                    } else {
                        null
                    },
                    isAllDay = csvTask.isAllDay,
                    status = if (csvTask.isCompleted) TaskStatus.DONE else TaskStatus.TODO,
                    completedAt = if (csvTask.isCompleted) {
                        csvTask.completedAt?.let { utcToLocal(it) } ?: now
                    } else {
                        null
                    },
                    recurrenceType = csvTask.recurrenceType,
                    categoryId = categoryId,
                    durationReminders = csvTask.durationReminders,
                    sourceExternalId = externalId,
                    createdAt = if (csvTask.createdAt > 0) utcToLocal(csvTask.createdAt) else now,
                    updatedAt = now,
                )
                taskWriteCoordinator.create(task)
                importedTaskIds += task.id
                importedCount++
            }

            rebuildReminderQueueAction?.afterRecordChange {
                importedTaskIds.forEach { scheduleTaskRemindersAction(it) }
            } ?: importedTaskIds.forEach { scheduleTaskRemindersAction(it) }
            log.d { "Imported $importedCount CSV tasks" }
            importedCount
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
