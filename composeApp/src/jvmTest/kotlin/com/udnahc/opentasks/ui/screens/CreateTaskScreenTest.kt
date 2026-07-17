package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateTaskScreenTest {
    @Test
    fun reopeningAnEditorSeedsEveryPersistedFormField() {
        val deadline = startOfDayLocalMillis(2026, 8, 12) + (9 * 60 * 60 * 1_000L)
        val endDeadline = deadline + (90 * 60 * 1_000L)
        val task = testTask(
            id = "restore-form",
            title = "Duration task",
            content = "Description",
            deadline = deadline,
            endDeadline = endDeadline,
            recurrenceType = RecurrenceType.MONTHLY,
            categoryId = "work",
            section = "This week",
            status = TaskStatus.DONE,
            priority = TaskPriority.HIGH,
        ).copy(
            subtasks = "subtasks",
            isAllDay = true,
            notifyBeforeValue = 3,
            location = "Room 4",
            url = "https://example.com",
            organizer = "owner@example.com",
            eventStatus = "CONFIRMED",
            attendees = "a@example.com",
            durationReminders = "0,-1",
            dateReminders = "60,0",
        )

        val restored = task.toTaskFormData()

        assertEquals(task.title, restored.title)
        assertEquals(task.content, restored.content)
        assertEquals(task.subtasks, restored.subtasks)
        assertEquals(TaskPriority.HIGH, restored.priority)
        assertEquals(deadline, restored.deadline)
        assertEquals(endDeadline, restored.endDeadline)
        assertTrue(restored.isAllDay)
        assertEquals(3, restored.reminderDays)
        assertEquals(RecurrenceType.MONTHLY, restored.recurrence)
        assertEquals("work", restored.categoryId)
        assertEquals("This week", restored.section)
        assertEquals(TaskStatus.DONE, restored.status)
        assertEquals("Room 4", restored.location)
        assertEquals("https://example.com", restored.url)
        assertEquals("owner@example.com", restored.organizer)
        assertEquals("CONFIRMED", restored.eventStatus)
        assertEquals("a@example.com", restored.attendees)
        assertEquals("0,-1", restored.durationReminders)
        assertEquals("60,0", restored.dateReminders)
        assertFalse(restored.pendingImages.isNotEmpty())
    }
}
