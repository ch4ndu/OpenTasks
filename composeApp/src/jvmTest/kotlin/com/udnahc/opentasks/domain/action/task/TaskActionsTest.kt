package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.repository.TaskAttachmentFilePaths
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.notification.ReminderCommandRejectedException
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderKind
import com.udnahc.opentasks.data.auth.MutexAccountMutationGate
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
import kotlin.test.assertFailsWith
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
        assertEquals(task.value.id, inserted.id)
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

        DeleteTaskAction(repository, FakeAttachmentFileStorage(), scheduler, mutationGate = MutexAccountMutationGate())(updated.id)
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

        DeleteTaskAction(taskRepository, storage, scheduler, mutationGate = MutexAccountMutationGate())(task.id)

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
            semanticKey = ReminderIdentity(
                task.id,
                localToUtc(notificationOccurrence),
                ReminderKind.DATE,
                0,
            ).semanticKey,
            accountId = "account",
            boundaryEpoch = 1L,
        )

        val updated = repository.updated.single()
        assertEquals(notificationOccurrence + (2 * MILLIS_PER_DAY), updated.deadline)
        assertEquals(notificationOccurrence + (3 * MILLIS_PER_DAY), updated.endDeadline)
        assertEquals(TaskStatus.TODO, updated.status)
    }

    @Test
    fun notificationMarkDoneCompletesTheCurrentOneOffOccurrence() = runTest {
        val deadline = startOfDayLocalMillis(2026, 5, 10)
        val task = testTask(id = "one-off", deadline = deadline)
        val repository = FakeTaskRepository(listOf(task))
        val action = MarkTaskNotificationDoneAction(
            UpdateTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository)),
        )

        val result = action(
            taskId = task.id,
            occurrenceDeadlineUtcMillis = localToUtc(deadline),
            semanticKey = ReminderIdentity(
                task.id,
                localToUtc(deadline),
                ReminderKind.DATE,
                0,
            ).semanticKey,
            accountId = "account",
            boundaryEpoch = 1L,
        )

        assertTrue(result.value is TaskWriteResult.Updated)
        assertEquals(TaskStatus.DONE, repository.tasks.single().status)
    }

    @Test
    fun notificationMarkDoneRejectsARescheduledOneOffWithoutWriting() = runTest {
        val oldDeadline = startOfDayLocalMillis(2026, 5, 10)
        val currentDeadline = startOfDayLocalMillis(2026, 5, 11)
        val task = testTask(id = "rescheduled-one-off", deadline = currentDeadline)
        val repository = FakeTaskRepository(listOf(task))
        val action = MarkTaskNotificationDoneAction(
            UpdateTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository)),
        )

        val result = action(
            taskId = task.id,
            occurrenceDeadlineUtcMillis = localToUtc(oldDeadline),
            semanticKey = ReminderIdentity(
                task.id,
                localToUtc(oldDeadline),
                ReminderKind.DATE,
                0,
            ).semanticKey,
            accountId = "account",
            boundaryEpoch = 1L,
        )

        assertEquals(TaskWriteResult.StaleOccurrence, result.value)
        assertTrue(repository.updated.isEmpty())
        assertEquals(TaskStatus.TODO, repository.tasks.single().status)
    }

    @Test
    fun notificationMarkDoneAdvancesFromAProjectedRecurringOccurrence() = runTest {
        val anchor = startOfDayLocalMillis(2026, 5, 10)
        val projectedOccurrence = startOfDayLocalMillis(2026, 5, 14)
        val task = testTask(
            id = "projected-recurring",
            deadline = anchor,
            endDeadline = anchor + MILLIS_PER_DAY,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = MarkTaskNotificationDoneAction(
            UpdateTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository)),
        )

        val result = action(
            taskId = task.id,
            occurrenceDeadlineUtcMillis = localToUtc(projectedOccurrence),
            semanticKey = ReminderIdentity(
                task.id,
                localToUtc(projectedOccurrence),
                ReminderKind.DATE,
                0,
            ).semanticKey,
            accountId = "account",
            boundaryEpoch = 1L,
        )

        val updated = (result.value as TaskWriteResult.Updated).task
        assertEquals(startOfDayLocalMillis(2026, 5, 16), updated.deadline)
        assertEquals(startOfDayLocalMillis(2026, 5, 17), updated.endDeadline)
        assertEquals(TaskStatus.TODO, updated.status)
    }

    @Test
    fun projectedRecurringMembershipIsAuthorityOnlyForNotificationMarkDone() = runTest {
        val anchor = startOfDayLocalMillis(2026, 5, 10)
        val projectedOccurrence = startOfDayLocalMillis(2026, 5, 14)
        val task = testTask(
            id = "projected-intent-boundary",
            deadline = anchor,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
        )
        val repository = FakeTaskRepository(listOf(task))
        val coordinator = TaskWriteCoordinator(repository)
        val form = TaskFormData(
            title = task.title,
            content = task.content,
            deadline = anchor,
            recurrence = RecurrenceType.DAILY,
        )

        assertEquals(
            TaskWriteResult.StaleOccurrence,
            coordinator.write(
                task.id,
                TaskWriteIntent.CompleteOccurrence(projectedOccurrence),
            ).value,
        )
        assertEquals(
            TaskWriteResult.StaleOccurrence,
            coordinator.write(
                task.id,
                TaskWriteIntent.CompleteSeries(projectedOccurrence),
            ).value,
        )
        assertEquals(
            TaskWriteResult.StaleOccurrence,
            coordinator.write(
                task.id,
                TaskWriteIntent.ApplyFormAndComplete(
                    formData = form,
                    expectedOccurrence = projectedOccurrence,
                    scope = FormCompletionScope.OCCURRENCE,
                ),
            ).value,
        )
        assertTrue(repository.updated.isEmpty())

        val notification = coordinator.write(
            task.id,
            TaskWriteIntent.NotificationMarkDone(projectedOccurrence),
        )
        assertEquals(
            startOfDayLocalMillis(2026, 5, 16),
            (notification.value as TaskWriteResult.Updated).task.deadline,
        )

        val currentOrdinaryOccurrence = coordinator.write(
            task.id,
            TaskWriteIntent.CompleteOccurrence(startOfDayLocalMillis(2026, 5, 16)),
        )
        assertTrue(currentOrdinaryOccurrence.value is TaskWriteResult.Updated)
    }

    @Test
    fun notificationMarkDoneRejectsAProjectedNonMemberWithoutRewinding() = runTest {
        val anchor = startOfDayLocalMillis(2026, 5, 10)
        val nonMemberOccurrence = startOfDayLocalMillis(2026, 5, 11)
        val task = testTask(
            id = "non-member-recurring",
            deadline = anchor,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
        )
        val repository = FakeTaskRepository(listOf(task))
        val action = MarkTaskNotificationDoneAction(
            UpdateTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository)),
        )

        val result = action(
            taskId = task.id,
            occurrenceDeadlineUtcMillis = localToUtc(nonMemberOccurrence),
            semanticKey = ReminderIdentity(
                task.id,
                localToUtc(nonMemberOccurrence),
                ReminderKind.DATE,
                0,
            ).semanticKey,
            accountId = "account",
            boundaryEpoch = 1L,
        )

        assertEquals(TaskWriteResult.StaleOccurrence, result.value)
        assertTrue(repository.updated.isEmpty())
        assertEquals(anchor, repository.tasks.single().deadline)
    }

    @Test
    fun committedTaskTruthCarriesSyncWarningAfterRepositoryCommit() = runTest {
        val task = testTask(id = "sync-warning")
        val warning = IllegalStateException("sync trigger failed")
        val repository = FakeTaskRepository(listOf(task)).apply {
            mutationPostCommitWarning = warning
        }
        val action = UpdateTaskAction(repository, ScheduleTaskRemindersAction(NotificationScheduler(), repository))

        val result = action(
            task.id,
            TaskWriteIntent.FormUpdate(TaskFormData(title = "Committed", content = task.content)),
        )

        assertTrue(result.value is TaskWriteResult.Updated)
        assertEquals("Committed", repository.tasks.single().title)
        assertEquals(warning, result.postCommitWarning?.cause)
        assertEquals(PostCommitWarningPhase.SYNC, result.postCommitWarning?.phase)
    }

    @Test
    fun committedTaskTruthCarriesReminderMaintenanceWarningAfterRepositoryCommit() = runTest {
        val task = testTask(id = "reminder-warning")
        val repository = FakeTaskRepository(listOf(task))
        val action = UpdateTaskAction(
            repository,
            ScheduleTaskRemindersAction(ThrowingReminderScheduler(), repository),
        )

        val result = action(
            task.id,
            TaskWriteIntent.FormUpdate(TaskFormData(title = "Committed", content = task.content)),
        )

        assertTrue(result.value is TaskWriteResult.Updated)
        assertEquals("Committed", repository.tasks.single().title)
        val warning = result.postCommitWarning ?: error("Expected a reminder maintenance warning")
        assertEquals(PostCommitWarningPhase.REMINDER_MAINTENANCE, warning.phase)
    }

    @Test
    fun notificationGotItDismissesOnlyAllDayOngoingNotificationState() = runTest {
        val allDayDeadline = startOfDayLocalMillis(2026, 5, 4)
        val timedDeadline = startOfDayLocalMillis(2026, 5, 5)
        val allDayTask = testTask(id = "all-day", isAllDay = true, deadline = allDayDeadline)
        val timedTask = testTask(id = "timed", isAllDay = false, deadline = timedDeadline)
        val repository = FakeTaskRepository(listOf(allDayTask, timedTask))
        val settingsRepository = FakeAppSettingsRepository()
        val dismissalStore = AllDayNotificationDismissalStore(settingsRepository)
        val scheduler = RecordingReminderScheduler()
        val action = DismissTaskNotificationAction(
            taskRepository = repository,
            allDayNotificationDismissalStore = dismissalStore,
            reminderScheduler = scheduler,
        )

        action(
            taskId = allDayTask.id,
            semanticKey = ReminderIdentity(
                allDayTask.id,
                localToUtc(allDayDeadline),
                ReminderKind.ONGOING,
                0,
            ).semanticKey,
            occurrenceDeadlineUtcMillis = localToUtc(allDayDeadline),
            accountId = "account",
            boundaryEpoch = 1L,
        )
        action(
            taskId = timedTask.id,
            semanticKey = ReminderIdentity(
                timedTask.id,
                localToUtc(timedDeadline),
                ReminderKind.ONGOING,
                0,
            ).semanticKey,
            occurrenceDeadlineUtcMillis = localToUtc(timedDeadline),
            accountId = "account",
            boundaryEpoch = 1L,
        )

        assertEquals(
            listOf(
                ReminderIdentity(allDayTask.id, localToUtc(allDayDeadline), ReminderKind.ONGOING, 0).semanticKey,
                ReminderIdentity(timedTask.id, localToUtc(timedDeadline), ReminderKind.ONGOING, 0).semanticKey,
            ),
            scheduler.cancelled,
        )
        assertTrue(dismissalStore.isDismissedToday("all-day"))
        assertFalse(dismissalStore.isDismissedToday("timed"))
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun notificationGotItAcceptsAnOrdinaryTaskReminderFromTheSheet() = runTest {
        val deadline = startOfDayLocalMillis(2026, 5, 6) + MILLIS_PER_DAY
        val task = testTask(id = "ordinary-sheet", deadline = deadline)
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = RecordingReminderScheduler()
        val action = DismissTaskNotificationAction(
            taskRepository = repository,
            allDayNotificationDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()),
            reminderScheduler = scheduler,
        )
        val semanticKey = ReminderIdentity(
            eventId = task.id,
            occurrenceUtcMillis = localToUtc(deadline),
            kind = ReminderKind.DATE,
            ordinal = 0,
        ).semanticKey

        action(
            taskId = task.id,
            semanticKey = semanticKey,
            occurrenceDeadlineUtcMillis = localToUtc(deadline),
            accountId = "account",
            boundaryEpoch = 1L,
        )

        assertEquals(listOf(semanticKey), scheduler.cancelled)
    }

    @Test
    fun notificationGotItRejectsAConflictingCountdownIdentity() = runTest {
        val scheduler = RecordingReminderScheduler()
        val action = DismissTaskNotificationAction(
            taskRepository = FakeTaskRepository(),
            allDayNotificationDismissalStore = AllDayNotificationDismissalStore(FakeAppSettingsRepository()),
            reminderScheduler = scheduler,
        )
        val identity = ReminderIdentity(
            eventId = "countdown-1",
            occurrenceUtcMillis = 2_000L,
            kind = ReminderKind.COUNTDOWN,
            ordinal = 0,
        )

        assertFailsWith<ReminderCommandRejectedException> {
            action(
                taskId = identity.eventId,
                semanticKey = identity.semanticKey,
                occurrenceDeadlineUtcMillis = identity.occurrenceUtcMillis,
                accountId = "account",
                boundaryEpoch = 1L,
            )
        }
        assertTrue(scheduler.cancelled.isEmpty())
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
        assertEquals(TaskWriteResult.CompletionChoiceRequired(deadline), choice.value)
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

        assertEquals(TaskWriteResult.StaleOccurrence, stale.value)
        assertEquals(advanced, repository.tasks.single())
        assertEquals(writesBeforeStaleAttempt, repository.updated.size)
        assertEquals(
            TaskWriteResult.Missing,
            action("missing", TaskWriteIntent.FormUpdate(TaskFormData(title = "Missing", content = ""))).value,
        )
        assertEquals(listOf(task.id), repository.tasks.map { it.id })
    }

    @Test
    fun deleteSerializesWithLateExistingTaskMutationsAndCannotBeRecreated() = runTest {
        val task = testTask(id = "delete-race", deadline = startOfDayLocalMillis(2026, 5, 1))
        val repository = FakeTaskRepository(listOf(task))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), repository)
        val delete = DeleteTaskAction(
            repository,
            FakeAttachmentFileStorage(),
            scheduler,
            mutationGate = MutexAccountMutationGate(),
        )
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
    val cancelled = mutableListOf<String>()

    override suspend fun schedule(request: com.udnahc.opentasks.data.notification.ReminderRequest) = Unit

    override suspend fun cancel(semanticKey: String) {
        cancelled += semanticKey
    }
    override suspend fun cancelPendingReminders(eventId: String) = Unit
    override suspend fun cancelReminders(eventId: String) = Unit
    override suspend fun cancelAll(eventId: String) = Unit
    override suspend fun cancelAllAccountReminders() = Unit
    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) = Unit

    override suspend fun stopOngoing(eventId: String) {
        stoppedOngoing.add(eventId)
    }
}

private class ThrowingReminderScheduler : ReminderScheduler {
    private fun failure(): Nothing = error("reminder maintenance failed")

    override suspend fun schedule(request: com.udnahc.opentasks.data.notification.ReminderRequest) = failure()
    override suspend fun cancel(semanticKey: String) = failure()
    override suspend fun cancelPendingReminders(eventId: String) = failure()
    override suspend fun cancelReminders(eventId: String) = failure()
    override suspend fun cancelAll(eventId: String) = failure()
    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) = failure()
    override suspend fun stopOngoing(eventId: String) = failure()
    override suspend fun cancelAllAccountReminders() = failure()
}
