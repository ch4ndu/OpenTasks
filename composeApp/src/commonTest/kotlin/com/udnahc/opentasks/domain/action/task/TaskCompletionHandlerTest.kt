package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.ReminderRequest
import com.udnahc.opentasks.data.notification.ReminderScheduler
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskCompletionHandlerTest {
    @Test
    fun rejectedSeriesCompletionKeepsChoiceForRetry() = runTest {
        val launcher: TaskMutationLauncher = { _, onFailure, _ ->
            onFailure(IllegalStateException("boundary changed"))
        }
        val handler = TaskCompletionHandler(
            toggleTaskCompleteAction = ToggleTaskCompleteAction(
                repository = FakeTaskRepository(),
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                    scheduler = NoOpReminderScheduler,
                    taskRepository = FakeTaskRepository(),
                ),
            ),
            scope = this,
            launchMutationDelegate = launcher,
        )

        handler.toggleComplete(
            taskId = "task",
            status = TaskStatus.TODO,
            recurrenceType = RecurrenceType.DAILY,
            occurrenceDeadlineLocalMillis = 1_000L,
        )
        handler.completeOccurrence()

        assertEquals(TaskCompletionChoice("task", 1_000L), handler.taskPendingSeriesChoice.value)
    }

    @Test
    fun duplicateSeriesCompletionIsIgnoredWhileFirstSubmissionIsInFlight() = runTest {
        var launchCount = 0
        var pendingAction: (suspend () -> Unit)? = null
        val launcher: TaskMutationLauncher = { _, _, action ->
            launchCount += 1
            pendingAction = action
        }
        val handler = handler(
            repository = FakeTaskRepository(listOf(testTask(id = "task", deadline = 1_000L))),
            launcher = launcher,
            scope = this,
        )

        handler.toggleComplete(
            taskId = "task",
            status = TaskStatus.TODO,
            recurrenceType = RecurrenceType.DAILY,
            occurrenceDeadlineLocalMillis = 1_000L,
        )
        handler.completeOccurrence()
        handler.completeOccurrence()

        assertEquals(1, launchCount)
        checkNotNull(pendingAction).invoke()
        assertEquals(null, handler.taskPendingSeriesChoice.value)
    }

    @Test
    fun cancellationKeepsChoiceAndUnlocksRetry() = runTest {
        val repository = FakeTaskRepository(
            listOf(testTask(id = "task", deadline = 1_000L)),
        )
        var launchCount = 0
        var pendingAction: (suspend () -> Unit)? = null
        val launcher: TaskMutationLauncher = { _, _, action ->
            launchCount += 1
            pendingAction = action
        }
        val handler = handler(repository, launcher, this)
        handler.toggleComplete(
            taskId = "task",
            status = TaskStatus.TODO,
            recurrenceType = RecurrenceType.DAILY,
            occurrenceDeadlineLocalMillis = 1_000L,
        )

        repository.mutationError = CancellationException("cancelled")
        handler.completeOccurrence()
        val cancelledAction = checkNotNull(pendingAction)
        assertFailsWith<CancellationException> { cancelledAction() }
        assertEquals(TaskCompletionChoice("task", 1_000L), handler.taskPendingSeriesChoice.value)

        repository.mutationError = null
        handler.completeOccurrence()
        assertEquals(2, launchCount)
        checkNotNull(pendingAction).invoke()
        assertEquals(null, handler.taskPendingSeriesChoice.value)
    }

    private fun handler(
        repository: FakeTaskRepository,
        launcher: TaskMutationLauncher,
        scope: CoroutineScope,
    ): TaskCompletionHandler {
        return TaskCompletionHandler(
            toggleTaskCompleteAction = ToggleTaskCompleteAction(
                repository = repository,
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                    scheduler = NoOpReminderScheduler,
                    taskRepository = repository,
                ),
            ),
            scope = scope,
            launchMutationDelegate = launcher,
        )
    }
}

private object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(request: ReminderRequest) = Unit
    override suspend fun cancel(semanticKey: String) = Unit
    override suspend fun cancelPendingReminders(eventId: String) = Unit
    override suspend fun cancelReminders(eventId: String) = Unit
    override suspend fun cancelAll(eventId: String) = Unit
    override suspend fun startOngoing(
        identity: com.udnahc.opentasks.data.notification.ReminderIdentity,
        title: String,
    ) = Unit
    override suspend fun stopOngoing(eventId: String) = Unit
    override suspend fun cancelAllAccountReminders() = Unit
}
