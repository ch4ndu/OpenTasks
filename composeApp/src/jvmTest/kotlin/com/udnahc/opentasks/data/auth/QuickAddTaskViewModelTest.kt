package com.udnahc.opentasks.data.auth

import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.data.repository.PostCommitWarningPhase
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.usecase.task.ParseQuickTaskInputUseCase
import com.udnahc.opentasks.domain.usecase.task.QuickTaskCreationContext
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.viewmodel.QuickAddTaskSaveEvent
import com.udnahc.opentasks.viewmodel.QuickAddTaskViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddTaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savesThroughAddTaskActionWithContextAndParsedFieldPrecedence() = runTest(dispatcher) {
        val fixture = fixture(
            context = QuickTaskCreationContext(
                categoryId = "work",
                priority = TaskPriority.HIGH,
                fallbackDate = LocalDate(2030, 6, 10),
            ),
        )
        fixture.viewModel.onInputChanged("Submit report tomorrow at 3pm weekly")

        fixture.viewModel.save()
        advanceUntilIdle()

        val task = fixture.taskRepository.inserted.single()
        assertEquals("Submit report", task.title)
        assertEquals("", task.content)
        assertEquals("", task.subtasks)
        assertEquals("work", task.categoryId)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(LocalDateTime(2028, 2, 28, 15, 0), localMillisToLocalDateTime(task.deadline ?: 0L))
        assertFalse(task.isAllDay)
        assertEquals("WEEKLY", task.recurrenceType.name)
        assertEquals(0, task.recurrenceInterval)
        assertEquals(0, task.notifyBeforeValue)
        assertEquals(NotifyBeforeUnit.NONE, task.notifyBeforeUnit)
        assertEquals("", task.dateReminders)
        assertEquals("", task.durationReminders)
        assertFalse(task.isUrgent)
        assertFalse(task.isImportant)
        val event = assertIs<QuickAddTaskSaveEvent.Saved>(fixture.viewModel.saveEvent.value)
        assertTrue(fixture.viewModel.consumeSaveEvent(event))
        assertFalse(fixture.viewModel.consumeSaveEvent(event))
    }

    @Test
    fun chipDismissalKeepsPhraseLiteralAndEditingMakesChangedPhraseEligibleAgain() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.onInputChanged("Plan tomorrow")
        val token = fixture.viewModel.uiState.value.activeTokens.single()

        fixture.viewModel.dismissToken(token.signature)

        assertEquals("Plan tomorrow", fixture.viewModel.uiState.value.parseResult.cleanedTitle)
        assertTrue(fixture.viewModel.uiState.value.activeTokens.isEmpty())

        fixture.viewModel.onInputChanged("Plan Monday")
        assertEquals("Plan", fixture.viewModel.uiState.value.parseResult.cleanedTitle)
        assertEquals(1, fixture.viewModel.uiState.value.activeTokens.size)
    }

    @Test
    fun rapidButtonAndImeSubmissionsCreateOnlyOneTask() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.onInputChanged("One task")

        fixture.viewModel.save()
        fixture.viewModel.save()
        advanceUntilIdle()
        fixture.viewModel.save()
        advanceUntilIdle()

        assertEquals(1, fixture.taskRepository.inserted.size)
        assertTrue(fixture.viewModel.uiState.value.isSaved)
        assertFalse(fixture.viewModel.uiState.value.canSave)
    }

    @Test
    fun committedAddWarningIsCarriedByTheOneShotSavedEvent() = runTest(dispatcher) {
        val fixture = fixture()
        val warning = IllegalStateException("sync warning")
        fixture.taskRepository.insertPostCommitWarning = warning
        fixture.viewModel.onInputChanged("Saved task")

        fixture.viewModel.save()
        advanceUntilIdle()

        val event = assertIs<QuickAddTaskSaveEvent.Saved>(fixture.viewModel.saveEvent.value)
        assertEquals(warning, event.postCommitWarning?.cause)
        assertEquals(PostCommitWarningPhase.SYNC, event.postCommitWarning?.phase)
    }

    @Test
    fun failureRetainsDraftAndCanRetryWithoutPublishingSuccess() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.taskRepository.insertError = IllegalStateException("write failed")
        fixture.viewModel.onInputChanged("Keep this tomorrow")

        fixture.viewModel.save()
        advanceUntilIdle()

        assertEquals("Keep this tomorrow", fixture.viewModel.uiState.value.input)
        assertTrue(fixture.viewModel.uiState.value.saveFailed)
        assertNull(fixture.viewModel.saveEvent.value)
        assertTrue(fixture.taskRepository.inserted.isEmpty())

        fixture.viewModel.clearError()
        fixture.taskRepository.insertError = null
        fixture.viewModel.save()
        advanceUntilIdle()
        assertEquals(1, fixture.taskRepository.inserted.size)
    }

    @Test
    fun cancellationDoesNotBecomeAnErrorOrSuccess() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.onInputChanged("Canceled task")
        fixture.taskRepository.insertError = CancellationException("canceled")

        fixture.viewModel.save()
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.saveFailed)
        assertFalse(fixture.viewModel.uiState.value.isSaving)
        assertNull(fixture.viewModel.saveEvent.value)
        assertTrue(fixture.taskRepository.inserted.isEmpty())
    }

    @Test
    fun staleOpeningBoundaryRejectsBeforePersistence() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.onInputChanged("Do not save")
        val replacement = fixture.binding.copy(boundaryEpoch = fixture.binding.boundaryEpoch + 1)
        fixture.accountRepository.publishState(AccountSessionState.LocalOnly(replacement))
        fixture.stateStore.setBinding(replacement)

        fixture.viewModel.save()
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.saveFailed)
        assertNull(fixture.viewModel.saveEvent.value)
        assertTrue(fixture.taskRepository.inserted.isEmpty())
    }

    private fun fixture(
        context: QuickTaskCreationContext = QuickTaskCreationContext(),
    ): Fixture {
        val binding = CacheBinding(
            canonicalEndpoint = "",
            serverInstanceId = "",
            accountId = LOCAL_CACHE_OWNER_ID,
            capabilityVersion = 0,
            boundaryEpoch = 7L,
            mode = CacheMode.LOCAL_ONLY,
        )
        val gate = MutexAccountMutationGate()
        val stateStore = WidgetFakeAccountStateStore(binding)
        val accountRepository = FakeAccountRepository(AccountSessionState.LocalOnly(binding), gate)
        val executor = AccountBoundaryExecutor(
            accountRepository = accountRepository,
            accountBoundaryGuard = AccountBoundaryGuard(stateStore),
            mutationGate = gate,
        )
        val taskRepository = FakeTaskRepository()
        val addTaskAction = AddTaskAction(
            repository = taskRepository,
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                scheduler = NotificationScheduler(),
                taskRepository = taskRepository,
            ),
            accountBoundaryExecutor = executor,
        )
        val viewModel = QuickAddTaskViewModel(
            context = context,
            parser = ParseQuickTaskInputUseCase(),
            addTaskAction = addTaskAction,
            accountBoundaryExecutor = executor,
            ioDispatcher = dispatcher,
            referenceTimeProvider = { LocalDateTime(2028, 2, 27, 14, 30) },
        )
        return Fixture(
            binding = binding,
            accountRepository = accountRepository,
            stateStore = stateStore,
            taskRepository = taskRepository,
            viewModel = viewModel,
        )
    }

    private data class Fixture(
        val binding: CacheBinding,
        val accountRepository: FakeAccountRepository,
        val stateStore: WidgetFakeAccountStateStore,
        val taskRepository: FakeTaskRepository,
        val viewModel: QuickAddTaskViewModel,
    )
}
