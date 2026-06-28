package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testAttachment
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TaskActionsTest {
    @Test
    fun addTaskPersistsAllBusinessFieldsAndSchedulesById() = runTest {
        val repository = FakeTaskRepository()
        val action = AddTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        val task = action(
            title = "Plan release",
            content = "Checklist",
            subtasks = """[{"id":"1","text":"Cut build","isChecked":false}]""",
            priority = TaskPriority.HIGH,
            deadline = 2_000L,
            endDeadline = 3_000L,
            isAllDay = true,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 2,
            isUrgent = true,
            isImportant = true,
            categoryId = "work",
            section = "Next",
            location = "HQ",
            url = "https://example.com",
            organizer = "ops@example.com",
            eventStatus = "confirmed",
            attendees = "a@example.com",
            durationReminders = "0,-1",
            dateReminders = "60,0",
        )

        val inserted = repository.inserted.single()
        assertEquals(task.id, inserted.id)
        assertEquals("Plan release", inserted.title)
        assertEquals(TaskPriority.HIGH, inserted.priority)
        assertEquals(2_000L, inserted.deadline)
        assertEquals(3_000L, inserted.endDeadline)
        assertTrue(inserted.isAllDay)
        assertEquals(RecurrenceType.WEEKLY, inserted.recurrenceType)
        assertEquals(2, inserted.recurrenceInterval)
        assertTrue(inserted.isUrgent)
        assertTrue(inserted.isImportant)
        assertEquals("work", inserted.categoryId)
        assertEquals("Next", inserted.section)
        assertEquals("0,-1", inserted.durationReminders)
        assertEquals("60,0", inserted.dateReminders)
    }

    @Test
    fun updateAndDeleteTaskRefreshTimestampsAndPreserveSoftDeleteContract() = runTest {
        val original = testTask(id = "task-1", title = "Old", updatedAt = 1L)
        val repository = FakeTaskRepository(listOf(original))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)

        UpdateTaskAction(repository, scheduler)(original.copy(title = "New"))
        val updated = repository.updated.last()
        assertEquals("New", updated.title)
        assertNotEquals(1L, updated.updatedAt)
        assertFalse(updated.isDeleted)

        DeleteTaskAction(repository, FakeAttachmentRepository(), FakeAttachmentFileStorage(), scheduler)(updated)
        val deleted = repository.updated.last()
        assertTrue(deleted.isDeleted)
        assertTrue(deleted.updatedAt >= updated.updatedAt)
    }

    @Test
    fun deleteTaskHardDeletesNeverUploadedAttachmentsAndTombstonesUploadedAttachments() = runTest {
        val task = testTask(id = "task-attachments")
        val taskRepository = FakeTaskRepository(listOf(task))
        val localOnly = testAttachment(
            id = "local-only",
            ownerId = task.id,
            localPath = "/tmp/local-only.jpg",
            thumbnailPath = "/tmp/local-only-thumb.jpg",
            pbId = null,
        )
        val uploaded = testAttachment(
            id = "uploaded",
            ownerId = task.id,
            localPath = "/tmp/uploaded.jpg",
            thumbnailPath = "/tmp/uploaded-thumb.jpg",
            pbId = "pb-uploaded",
        )
        val attachmentRepository = FakeAttachmentRepository(listOf(localOnly, uploaded))
        val storage = FakeAttachmentFileStorage().apply {
            addFile(localOnly.localPath)
            addFile(localOnly.thumbnailPath)
            addFile(uploaded.localPath)
            addFile(uploaded.thumbnailPath)
        }
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)

        DeleteTaskAction(taskRepository, attachmentRepository, storage, scheduler)(task)

        assertEquals(listOf(localOnly), attachmentRepository.hardDeleted)
        assertFalse(storage.exists(localOnly.localPath))
        assertFalse(storage.exists(localOnly.thumbnailPath))
        assertFalse(storage.exists(uploaded.localPath))
        assertFalse(storage.exists(uploaded.thumbnailPath))
        assertTrue(attachmentRepository.updated.single { it.id == uploaded.id }.isDeleted)
    }

    @Test
    fun toggleCompleteAdvancesOccurrenceOrCompletesSeriesForRecurringTasks() = runTest {
        val deadline = startOfDayLocalMillis(2026, 5, 4)
        val task = testTask(
            id = "recurring",
            deadline = deadline,
            endDeadline = deadline + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            status = TaskStatus.TODO,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        action(task)
        val nextOccurrence = repository.updated.last()
        assertEquals(TaskStatus.TODO, nextOccurrence.status)
        assertEquals(deadline + (2 * MILLIS_PER_DAY), nextOccurrence.deadline)
        assertEquals(task.endDeadline?.plus(2 * MILLIS_PER_DAY), nextOccurrence.endDeadline)
        assertEquals(RecurrenceType.DAILY, nextOccurrence.recurrenceType)

        action(task, completeSeries = true)
        val completedSeries = repository.updated.last()
        assertEquals(TaskStatus.DONE, completedSeries.status)
        assertEquals(RecurrenceType.NONE, completedSeries.recurrenceType)
        assertEquals(0, completedSeries.recurrenceInterval)
    }

    @Test
    fun notificationCompletionAdvancesRecurringTaskFromNotificationOccurrence() = runTest {
        val storedDeadline = startOfDayLocalMillis(2026, 5, 4)
        val notificationOccurrence = startOfDayLocalMillis(2026, 5, 10)
        val task = testTask(
            id = "recurring",
            deadline = storedDeadline,
            endDeadline = storedDeadline + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            status = TaskStatus.TODO,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        action(task, occurrenceDeadlineLocalMillis = notificationOccurrence)

        val updated = repository.updated.single()
        assertEquals(notificationOccurrence + (2 * MILLIS_PER_DAY), updated.deadline)
        assertEquals(notificationOccurrence + (3 * MILLIS_PER_DAY), updated.endDeadline)
        assertEquals(TaskStatus.TODO, updated.status)
    }

    @Test
    fun staleNotificationCompletionDoesNotRewindRecurringTask() = runTest {
        val currentDeadline = startOfDayLocalMillis(2026, 5, 10)
        val staleOccurrence = startOfDayLocalMillis(2026, 5, 8)
        val task = testTask(
            id = "recurring",
            deadline = currentDeadline,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            status = TaskStatus.TODO,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        action(task, occurrenceDeadlineLocalMillis = staleOccurrence)

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun toggleCompleteRestoresDoneTaskToTodo() = runTest {
        val task = testTask(id = "done", status = TaskStatus.DONE)
        val repository = FakeTaskRepository(listOf(task))

        ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))(task)

        assertEquals(TaskStatus.TODO, repository.updated.single().status)
    }
}
