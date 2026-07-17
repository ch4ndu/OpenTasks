package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.repository.TaskAttachmentFilePaths
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeAttachmentFileStorage
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testAttachment
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

        UpdateTaskAction(repository, scheduler)(
            original.id,
            TaskWriteIntent.FormUpdate(TaskFormData(title = "New", content = original.content)),
        )
        val updated = repository.updated.last()
        assertEquals("New", updated.title)
        assertNotEquals(1L, updated.updatedAt)
        assertFalse(updated.isDeleted)

        DeleteTaskAction(repository, FakeAttachmentFileStorage(), scheduler)(updated.id)
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
        taskRepository.graphDeletionFiles = listOf(
            TaskAttachmentFilePaths(localOnly.localPath, localOnly.thumbnailPath),
        )
        val storage = FakeAttachmentFileStorage().apply {
            addFile(localOnly.localPath)
            addFile(localOnly.thumbnailPath)
            addFile(uploaded.localPath)
            addFile(uploaded.thumbnailPath)
        }
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)

        DeleteTaskAction(taskRepository, storage, scheduler)(task.id)

        assertFalse(storage.exists(localOnly.localPath))
        assertFalse(storage.exists(localOnly.thumbnailPath))
        assertTrue(storage.exists(uploaded.localPath))
        assertTrue(storage.exists(uploaded.thumbnailPath))
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

        action(task.id, occurrenceDeadlineLocalMillis = deadline)
        val nextOccurrence = repository.updated.last()
        assertEquals(TaskStatus.TODO, nextOccurrence.status)
        assertEquals(deadline + (2 * MILLIS_PER_DAY), nextOccurrence.deadline)
        assertEquals(task.endDeadline?.plus(2 * MILLIS_PER_DAY), nextOccurrence.endDeadline)
        assertEquals(RecurrenceType.DAILY, nextOccurrence.recurrenceType)

        action(task.id, completeSeries = true, occurrenceDeadlineLocalMillis = deadline + (2 * MILLIS_PER_DAY))
        val completedSeries = repository.updated.last()
        assertEquals(TaskStatus.DONE, completedSeries.status)
        assertEquals(RecurrenceType.NONE, completedSeries.recurrenceType)
        assertEquals(0, completedSeries.recurrenceInterval)
    }

    @Test
    fun notificationCompletionAdvancesTheLiveRecurringOccurrence() = runTest {
        val notificationOccurrence = startOfDayLocalMillis(2026, 5, 10)
        val task = testTask(
            id = "recurring",
            deadline = notificationOccurrence,
            endDeadline = notificationOccurrence + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            status = TaskStatus.TODO,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        action(task.id, occurrenceDeadlineLocalMillis = notificationOccurrence)

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

        action(task.id, occurrenceDeadlineLocalMillis = staleOccurrence)

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun notificationMarkDoneUsesOccurrenceDeadlineFromNotificationPayload() = runTest {
        val notificationOccurrence = startOfDayLocalMillis(2026, 5, 10)
        val task = testTask(
            id = "recurring-notification",
            deadline = notificationOccurrence,
            endDeadline = notificationOccurrence + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            status = TaskStatus.TODO,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = MarkTaskNotificationDoneAction(
            updateTaskAction = UpdateTaskAction(
                repository,
                ScheduleTaskRemindersAction(NotificationScheduler(), repository),
            ),
        )

        action(
            taskId = task.id,
            occurrenceDeadlineUtcMillis = localToUtc(notificationOccurrence),
        )

        val updated = repository.updated.single()
        assertEquals(notificationOccurrence + (2 * MILLIS_PER_DAY), updated.deadline)
        assertEquals(notificationOccurrence + (3 * MILLIS_PER_DAY), updated.endDeadline)
        assertEquals(TaskStatus.TODO, updated.status)
    }

    @Test
    fun notificationGotItDismissesOnlyAllDayOngoingNotificationState() = runTest {
        val allDayTask = testTask(id = "all-day", isAllDay = true)
        val timedTask = testTask(id = "timed", isAllDay = false)
        val repository = FakeTaskRepository(listOf(allDayTask, timedTask))
        val settingsRepository = FakeAppSettingsRepository()
        val dismissalStore = AllDayNotificationDismissalStore(settingsRepository)
        val scheduler = RecordingReminderScheduler()
        val action = DismissTaskNotificationAction(
            taskRepository = repository,
            allDayNotificationDismissalStore = dismissalStore,
            reminderScheduler = scheduler,
        )

        action(allDayTask.id)
        action(timedTask.id)

        assertEquals(listOf("all-day", "timed"), scheduler.stoppedOngoing)
        assertTrue(dismissalStore.isDismissedToday("all-day"))
        assertFalse(dismissalStore.isDismissedToday("timed"))
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun concurrentAllDayDismissalsPreserveEveryDismissedTaskId() = runTest {
        val store = AllDayNotificationDismissalStore(
            FakeAppSettingsRepository(),
            nowUtcMillisProvider = { 1_800_000_000_000L },
        )

        awaitAll(
            async { store.dismissToday("first") },
            async { store.dismissToday("second") },
        )

        assertTrue(store.isDismissedToday("first"))
        assertTrue(store.isDismissedToday("second"))
    }

    @Test
    fun toggleCompleteRestoresDoneTaskToTodo() = runTest {
        val task = testTask(id = "done", status = TaskStatus.DONE)
        val repository = FakeTaskRepository(listOf(task))

        ToggleTaskCompleteAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))(task.id)

        assertEquals(TaskStatus.TODO, repository.updated.single().status)
    }

    @Test
    fun persistedTruthWritesPreserveConcurrentStatusStarAndFormChanges() = runTest {
        val deadline = startOfDayLocalMillis(2026, 1, 31)
        val task = testTask(
            id = "persisted-truth",
            title = "Old",
            deadline = deadline,
            recurrenceType = RecurrenceType.MONTHLY,
        ).copy(recurrenceAnchorDay = 31)
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val status = UpdateTaskStatusAction(repository, scheduler)
        val star = ToggleTaskStarredAction(repository)
        val form = UpdateTaskAction(repository, scheduler)

        awaitAll(
            async { status(task.id, TaskStatus.IN_PROGRESS) },
            async { star(task.id) },
            async {
                form(
                    task.id,
                    TaskWriteIntent.FormUpdate(
                        TaskFormData(
                            title = "Edited",
                            content = "Body",
                            deadline = deadline,
                            recurrence = RecurrenceType.MONTHLY,
                            status = TaskStatus.TODO,
                        ),
                    ),
                )
            },
        )

        val stored = repository.tasks.single()
        assertEquals(TaskStatus.TODO, stored.status)
        assertTrue(stored.isStarred)
        assertEquals("Edited", stored.title)
        assertEquals(deadline, stored.deadline)
        assertEquals(31, stored.recurrenceAnchorDay)
    }

    @Test
    fun statusTransitionsSetAndClearCompletionTimeWithoutAdvancingEditOnlyRecurrence() = runTest {
        val deadline = startOfDayLocalMillis(2026, 1, 31)
        val task = testTask(
            id = "completion-time",
            deadline = deadline,
            recurrenceType = RecurrenceType.MONTHLY,
        ).copy(recurrenceAnchorDay = 31)
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val update = UpdateTaskAction(repository, scheduler)
        val status = UpdateTaskStatusAction(repository, scheduler)

        update(
            task.id,
            TaskWriteIntent.FormUpdate(
                TaskFormData(
                    title = "Edited only",
                    content = "",
                    deadline = deadline,
                    recurrence = RecurrenceType.MONTHLY,
                    status = TaskStatus.TODO,
                ),
            ),
        )
        assertEquals(deadline, repository.tasks.single().deadline)
        assertEquals(TaskStatus.TODO, repository.tasks.single().status)
        assertEquals(null, repository.tasks.single().completedAt)

        val migratedDone = testTask(
            id = "migrated-done",
            deadline = deadline,
            recurrenceType = RecurrenceType.MONTHLY,
            status = TaskStatus.DONE,
        ).copy(recurrenceAnchorDay = null, completedAt = null)
        repository.replaceTasks(listOf(migratedDone))
        ToggleTaskStarredAction(repository)(migratedDone.id)
        assertEquals(TaskStatus.DONE, repository.tasks.single().status)
        assertEquals(null, repository.tasks.single().completedAt)
        assertEquals(null, repository.tasks.single().recurrenceAnchorDay)

        val completionTask = testTask(id = "completion-time-done")
        repository.replaceTasks(listOf(completionTask))
        status(completionTask.id, TaskStatus.DONE)
        val done = repository.tasks.single()
        assertEquals(TaskStatus.DONE, done.status)
        assertTrue(done.completedAt != null)
        val completedAt = done.completedAt

        ToggleTaskStarredAction(repository)(completionTask.id)
        assertEquals(completedAt, repository.tasks.single().completedAt)
        ToggleTaskCompleteAction(repository, scheduler)(completionTask.id)
        assertEquals(TaskStatus.TODO, repository.tasks.single().status)
        assertEquals(null, repository.tasks.single().completedAt)
    }

    @Test
    fun recurringFormCompletionAppliesTheFullFormBeforeOccurrenceOrSeriesCompletion() = runTest {
        val deadline = startOfDayLocalMillis(2026, 1, 31)
        val task = testTask(
            id = "form-completion",
            title = "Old",
            deadline = deadline,
            endDeadline = deadline + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1,
        ).copy(recurrenceAnchorDay = 31)
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val action = UpdateTaskAction(repository, scheduler)
        val form = TaskFormData(
            title = "Edited",
            content = "Body",
            subtasks = "subtasks",
            priority = TaskPriority.HIGH,
            deadline = deadline,
            endDeadline = deadline + (2 * MILLIS_PER_DAY),
            recurrence = RecurrenceType.MONTHLY,
            categoryId = "work",
            section = "Next",
            status = TaskStatus.DONE,
            location = "HQ",
            url = "https://example.com",
            organizer = "ops@example.com",
            eventStatus = "CONFIRMED",
            attendees = "a@example.com",
            durationReminders = "30,0",
            dateReminders = "60,0",
        )

        val choice = action(task.id, TaskWriteIntent.FormUpdate(form))
        assertEquals(TaskWriteResult.CompletionChoiceRequired(deadline), choice)
        assertEquals(task, repository.tasks.single())

        action(task.id, TaskWriteIntent.ApplyFormAndComplete(form, deadline, FormCompletionScope.OCCURRENCE))
        val occurrence = repository.tasks.single()
        val nextDeadline = startOfDayLocalMillis(2026, 2, 28)
        assertEquals(TaskStatus.TODO, occurrence.status)
        assertEquals(nextDeadline, occurrence.deadline)
        assertEquals(nextDeadline + (2 * MILLIS_PER_DAY), occurrence.endDeadline)
        assertEquals(31, occurrence.recurrenceAnchorDay)
        assertEquals(null, occurrence.completedAt)
        assertEquals("Edited", occurrence.title)
        assertEquals("Body", occurrence.content)
        assertEquals("subtasks", occurrence.subtasks)
        assertEquals(TaskPriority.HIGH, occurrence.priority)
        assertEquals("work", occurrence.categoryId)
        assertEquals("Next", occurrence.section)
        assertEquals("HQ", occurrence.location)
        assertEquals("https://example.com", occurrence.url)
        assertEquals("30,0", occurrence.durationReminders)

        val seriesForm = form.copy(title = "Series complete", deadline = occurrence.deadline, endDeadline = occurrence.endDeadline)
        action(
            task.id,
            TaskWriteIntent.ApplyFormAndComplete(
                seriesForm,
                occurrence.deadline ?: error("Missing occurrence deadline"),
                FormCompletionScope.SERIES,
            ),
        )
        val series = repository.tasks.single()
        assertEquals(TaskStatus.DONE, series.status)
        assertTrue(series.completedAt != null)
        assertEquals(RecurrenceType.NONE, series.recurrenceType)
        assertEquals(0, series.recurrenceInterval)
        assertEquals(null, series.recurrenceAnchorDay)
        assertEquals("Series complete", series.title)
    }

    @Test
    fun staleOccurrenceAndMissingIdNeverPartiallyWriteOrRecreateTasks() = runTest {
        val firstDeadline = startOfDayLocalMillis(2026, 5, 1)
        val task = testTask(
            id = "stale-form",
            deadline = firstDeadline,
            recurrenceType = RecurrenceType.DAILY,
        )
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val action = UpdateTaskAction(repository, scheduler)
        val form = TaskFormData(title = "Should not apply", content = "", deadline = firstDeadline, recurrence = RecurrenceType.DAILY, status = TaskStatus.DONE)

        ToggleTaskCompleteAction(repository, scheduler)(task.id, occurrenceDeadlineLocalMillis = firstDeadline)
        val advanced = repository.tasks.single()
        val writesBeforeStaleAttempt = repository.updated.size
        val stale = action(task.id, TaskWriteIntent.ApplyFormAndComplete(form, firstDeadline, FormCompletionScope.OCCURRENCE))

        assertEquals(TaskWriteResult.StaleOccurrence, stale)
        assertEquals(advanced, repository.tasks.single())
        assertEquals(writesBeforeStaleAttempt, repository.updated.size)
        assertEquals(
            TaskWriteResult.Missing,
            action("missing", TaskWriteIntent.FormUpdate(TaskFormData(title = "Missing", content = ""))),
        )
        assertEquals(listOf(task.id), repository.tasks.map { it.id })
    }

    @Test
    fun deleteSerializesWithLateExistingTaskMutationsAndCannotBeRecreated() = runTest {
        val task = testTask(id = "delete-race", deadline = startOfDayLocalMillis(2026, 5, 1))
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val delete = DeleteTaskAction(repository, FakeAttachmentFileStorage(), scheduler)
        val star = ToggleTaskStarredAction(repository)

        awaitAll(
            async { delete(task.id) },
            async { star(task.id) },
        )

        assertTrue(repository.tasks.single().isDeleted)
        assertEquals(TaskWriteResult.Missing, star(task.id))
        assertTrue(repository.tasks.single().isDeleted)
    }
}

private class RecordingReminderScheduler : ReminderScheduler {
    val stoppedOngoing = mutableListOf<String>()

    override suspend fun schedule(request: com.udnahc.opentasks.data.notification.ReminderRequest) = Unit

    override suspend fun cancel(semanticKey: String) = Unit
    override suspend fun cancelPendingReminders(eventId: String) = Unit
    override suspend fun cancelReminders(eventId: String) = Unit
    override suspend fun cancelAll(eventId: String) = Unit
    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) = Unit

    override suspend fun stopOngoing(eventId: String) {
        stoppedOngoing.add(eventId)
    }
}
