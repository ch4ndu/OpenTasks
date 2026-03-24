package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.extensions.utcMillisToLocalMillis
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.action.tag.AddTagAction

class ImportCalendarEventsAction(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val addTagAction: AddTagAction,
) {
    suspend operator fun invoke(events: List<CalendarEvent>): Int {
        if (events.isEmpty()) return 0

        // Find or create "Calendar Imports" category
        val category = categoryRepository.getCategoryByName(CATEGORY_NAME)
            ?: run {
                val newCategory = Category(name = CATEGORY_NAME, icon = "calendar", createdAt = utcNow())
                categoryRepository.insert(newCategory)
                newCategory
            }

        // Find or create "Imported" tag
        val tag = tagRepository.getTagByName(TAG_NAME)
            ?: run {
                val tagId = addTagAction(TAG_NAME)
                tagRepository.getTagById(tagId) ?: return 0
            }

        val now = utcNow()
        var importedCount = 0

        for (event in events) {
            // Skip duplicates
            if (taskRepository.getTaskByExternalId(event.externalId) != null) continue

            // Build content with time range
            val content = buildEventContent(event)

            val task = Task(
                title = event.title,
                content = content,
                deadline = event.startTimeUtcMillis,
                isAllDay = event.isAllDay,
                sourceExternalId = event.externalId,
                categoryId = category.id,
                location = event.location,
                url = event.url,
                organizer = event.organizer,
                eventStatus = event.status,
                attendees = event.attendees.joinToString(", "),
                createdAt = now,
                updatedAt = now,
            )
            taskRepository.insert(task)

            // Tag the task
            tagRepository.insertTaskTag(TaskTag(taskId = task.id, tagId = tag.id))
            importedCount++
        }

        return importedCount
    }

    private fun buildEventContent(event: CalendarEvent): String {
        val parts = mutableListOf<String>()

        if (event.isAllDay) {
            parts.add("All day event")
        } else {
            val startLocal = utcMillisToLocalMillis(event.startTimeUtcMillis)
            val startDate = formatDateShort(startLocal)
            val startTime = formatTimeFromLocalMillis(startLocal)
            if (event.endTimeUtcMillis != null) {
                val endLocal = utcMillisToLocalMillis(event.endTimeUtcMillis)
                val endTime = formatTimeFromLocalMillis(endLocal)
                parts.add("$startDate $startTime – $endTime")
            } else {
                parts.add("$startDate $startTime")
            }
        }

        if (event.calendarName.isNotBlank()) {
            parts.add("Calendar: ${event.calendarName}")
        }

        if (event.description.isNotBlank()) {
            parts.add(event.description)
        }

        return parts.joinToString("\n")
    }

    companion object {
        const val CATEGORY_NAME = "Calendar Imports"
        const val TAG_NAME = "Imported"
    }
}
