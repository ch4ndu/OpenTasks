package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_import_all_day_event
import opentasks.composeapp.generated.resources.calendar_import_calendar_name
import org.jetbrains.compose.resources.getString
import org.lighthousegames.logging.logging

private val log = logging("ImportCalendarEventsAction")

class ImportCalendarEventsAction(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val addTagAction: AddTagAction,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
) {
    private val taskWriteCoordinator = TaskWriteCoordinator(taskRepository)

    suspend operator fun invoke(events: List<CalendarEvent>): Int =
        accountBoundaryExecutor.withForegroundActionBoundary {
            log.d { "Importing ${events.size} calendar events" }
            if (events.isEmpty()) return@withForegroundActionBoundary 0

            // Find or create "Calendar Imports" category
            val category = categoryRepository.getCategoryByName(CATEGORY_NAME)
                ?: run {
                    val now = localNow()
                    val newCategory = Category(name = CATEGORY_NAME, icon = "calendar", createdAt = now)
                    categoryRepository.insert(newCategory)
                    newCategory
                }

            // Find or create "Imported" tag
            val tag = tagRepository.getTagByName(TAG_NAME)
                ?: run {
                    val tagId = addTagAction(TAG_NAME)
                    tagRepository.getTagById(tagId)
                        ?: return@withForegroundActionBoundary 0
                }

            val now = localNow()
            var importedCount = 0
            val importedTaskIds = mutableListOf<String>()
            val identityBatch = ImportedIdentityBatch()
            val consumedLegacyAliases = mutableSetOf<String>()

            for (event in events) {
                val identity = identityBatch.nextCalendar(event)
                if (taskRepository.getTaskByExternalId(identity.canonicalId) != null) {
                    log.v { "Skipping duplicate calendar event" }
                    continue
                }
                val legacyMatch = identity.legacyAlias != identity.canonicalId &&
                        identity.legacyAlias !in consumedLegacyAliases &&
                        taskRepository.getTaskByExternalId(identity.legacyAlias) != null
                if (legacyMatch) {
                    consumedLegacyAliases.add(identity.legacyAlias)
                    log.v { "Skipping legacy calendar event duplicate" }
                    continue
                }

                // Build content with time range (format from local millis already available)
                val content = buildEventContent(event)

                // Convert external UTC timestamps to local millis for repository
                val task = Task(
                    title = event.title,
                    content = content,
                    deadline = utcToLocal(event.startTimeUtcMillis),
                    endDeadline = event.endTimeUtcMillis?.let { utcToLocal(it) },
                    isAllDay = event.isAllDay,
                    sourceExternalId = identity.canonicalId,
                    categoryId = category.id,
                    location = event.location,
                    url = event.url,
                    organizer = event.organizer,
                    eventStatus = event.status,
                    attendees = event.attendees.joinToString(", "),
                    createdAt = now,
                    updatedAt = now,
                )
                taskWriteCoordinator.create(task)
                importedTaskIds += task.id

                // Tag the task
                tagRepository.insertTaskTag(TaskTag(taskId = task.id, tagId = tag.id))
                importedCount++
            }

            rebuildReminderQueueAction?.afterRecordChange {
                importedTaskIds.forEach { scheduleTaskRemindersAction(it) }
            } ?: importedTaskIds.forEach { scheduleTaskRemindersAction(it) }
            log.d { "Imported $importedCount calendar events" }
            importedCount
        }

    private suspend fun buildEventContent(event: CalendarEvent): String {
        val parts = mutableListOf<String>()

        if (event.isAllDay) {
            parts.add(getString(Res.string.calendar_import_all_day_event))
        } else {
            // Convert external UTC to local millis for display formatting
            val startLocal = utcToLocal(event.startTimeUtcMillis)
            val startDate = dateTimeFormatter.formatShortDate(startLocal)
            val startTime = dateTimeFormatter.formatTime(startLocal)
            if (event.endTimeUtcMillis != null) {
                val endLocal = utcToLocal(event.endTimeUtcMillis)
                val endDate = dateTimeFormatter.formatShortDate(endLocal)
                val endTime = dateTimeFormatter.formatTime(endLocal)
                if (endDate == startDate) {
                    parts.add("$startDate $startTime – $endTime")
                } else {
                    parts.add("$startDate $startTime – $endDate $endTime")
                }
            } else {
                parts.add("$startDate $startTime")
            }
        }

        if (event.calendarName.isNotBlank()) {
            parts.add(getString(Res.string.calendar_import_calendar_name, event.calendarName))
        }

        if (event.description.isNotBlank()) {
            parts.add(event.description)
        }

        return parts.joinToString("\n")
    }

    companion object {
        // Stable persisted names used for lookup/sync; intentionally not localized.
        const val CATEGORY_NAME = "Calendar Imports"
        const val TAG_NAME = "Imported"
    }
}
