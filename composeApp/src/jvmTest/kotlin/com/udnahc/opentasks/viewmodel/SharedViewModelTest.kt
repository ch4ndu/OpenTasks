package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import app.cash.turbine.ReceiveTurbine
import com.udnahc.opentasks.NotificationDeepLinkEvent
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.localToUtc
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.CountingMode
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.SmartListVisibility
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskCategory
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.DismissTaskNotificationAction
import com.udnahc.opentasks.domain.action.task.MarkTaskNotificationDoneAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateSectionAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTodayTasksUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeAttachmentRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeCountdownRepository
import com.udnahc.opentasks.testutil.FakeNoteRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
open class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) {
    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModelTest : MainDispatcherRule() {
    private val dayStart = startOfDayLocalMillis(2026, 5, 4)

    @Test
    fun calendarViewModelMergesCountdownsAndPersistsCalendarPreferences() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(
            listOf(
                testTask(id = "timed", deadline = dayStart + 9 * MILLIS_PER_HOUR),
                testTask(id = "all-day", deadline = dayStart, isAllDay = true),
            )
        )
        val countdownRepository = FakeCountdownRepository(
            listOf(testCountdown(id = "birthday", title = "Birthday", targetDate = dayStart, countdownType = CountdownType.BIRTHDAY))
        )
        val settingsRepository = FakeAppSettingsRepository()
        val viewModel = CalendarViewModel(
            observeTasksByDay = ObserveTasksByDayUseCase(taskRepository),
            observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository(listOf(testCategory(id = "cat", name = "Personal")))),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(taskRepository, ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)),
            observeCalendarViewPreference = ObserveCalendarViewPreferenceUseCase(settingsRepository),
            saveCalendarViewPreference = SaveCalendarViewPreferenceAction(settingsRepository),
            observeCalendarListDisplayModePreference = ObserveCalendarListDisplayModePreferenceUseCase(settingsRepository),
            saveCalendarListDisplayModePreference = SaveCalendarListDisplayModePreferenceAction(settingsRepository),
            ioDispatcher = dispatcher,
        )

        viewModel.tasksByDay.test {
            val byDay = awaitMatching { it.containsKey(dayKey(dayStart)) }
            assertEquals(listOf("all-day", "countdown_birthday", "timed"), byDay.getValue(dayKey(dayStart)).map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectListDay(dayStart)
        viewModel.selectedListDayTasks.test {
            assertEquals(
                listOf("all-day", "countdown_birthday", "timed"),
                awaitMatching { it.isNotEmpty() }.map { it.id },
            )
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.saveCalendarViewPreference(CalendarViewPreference.DAY)
        viewModel.saveCalendarListDisplayModePreference(CalendarListDisplayModePreference.CARD)
        advanceUntilIdle()

        assertEquals(CalendarViewPreference.DAY.name, settingsRepository.getValue("calendar_view_preference"))
        assertEquals(CalendarListDisplayModePreference.CARD.name, settingsRepository.getValue("calendar_list_display_mode_preference"))
    }

    @Test
    fun taskListViewModelProjectsSelectedFilterActiveCompletedAndBoardStatus() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(
            listOf(
                testTask(id = "todo", title = "B", categoryId = "inbox", status = TaskStatus.TODO, updatedAt = 30),
                testTask(id = "done", title = "A", categoryId = "inbox", status = TaskStatus.DONE, updatedAt = 40),
                testTask(id = "other", categoryId = "other", status = TaskStatus.TODO, updatedAt = 50),
                testTask(id = "starred", categoryId = "inbox", status = TaskStatus.IN_PROGRESS, isStarred = true, priority = TaskPriority.HIGH, updatedAt = 60),
            )
        )
        val settingsRepository = FakeAppSettingsRepository(mapOf("task_list_sort_option" to TaskSortOption.BY_TITLE.name))
        val categoryRepository = FakeCategoryRepository(
            listOf(
                testCategory(id = "inbox", name = "Inbox"),
                testCategory(id = "work", name = "Work"),
                testCategory(id = "personal", name = "Personal"),
            )
        )
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val attachmentRepository = FakeAttachmentRepository()
        val localDaySignal = LocalDaySignal()
        val viewModel = TaskListViewModel(
            observeTasksForCategory = ObserveTasksForCategoryUseCase(taskRepository),
            observeAllTasks = ObserveAllTasksUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(taskRepository, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(taskRepository),
            addCategoryAction = AddCategoryAction(categoryRepository),
            observeTaskSortOption = ObserveTaskSortOptionUseCase(settingsRepository),
            saveTaskSortOptionAction = SaveTaskSortOptionAction(settingsRepository),
            observeTodayTasks = ObserveTodayTasksUseCase(taskRepository, localDaySignal),
            updateSectionAction = UpdateSectionAction(taskRepository),
            observeTaskListViewMode = ObserveTaskListViewModeUseCase(settingsRepository),
            saveTaskListViewModeAction = SaveTaskListViewModeAction(settingsRepository),
            updateTaskStatusAction = UpdateTaskStatusAction(taskRepository, scheduler),
            observeTaskImageSummaries = ObserveTaskImageSummariesUseCase(attachmentRepository),
            localDaySignal = localDaySignal,
        )

        viewModel.selectCategory("inbox")
        viewModel.setCategorySearchQuery("wo")
        viewModel.filteredCategories.test {
            assertEquals(listOf("work"), awaitMatching { it.map { category -> category.id } == listOf("work") }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.activeTasksForSelectedCategory.test {
            assertEquals(listOf("todo", "starred"), awaitMatching { it.size == 2 }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.completedTasksForSelectedCategory.test {
            assertEquals(listOf("done"), awaitMatching { it.isNotEmpty() }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectFilter(TaskListFilter.Starred)
        viewModel.tasksForSelectedCategory.test {
            assertEquals(listOf("starred"), awaitMatching { it.map { task -> task.id } == listOf("starred") }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectCategory("inbox")
        viewModel.tasksByStatus.test {
            val byStatus = awaitMatching { byStatus ->
                byStatus[TaskStatus.TODO]?.map { it.id } == listOf("todo") &&
                    byStatus[TaskStatus.IN_PROGRESS]?.map { it.id } == listOf("starred") &&
                    byStatus[TaskStatus.DONE]?.map { it.id } == listOf("done")
            }
            assertEquals(listOf("todo"), byStatus.getValue(TaskStatus.TODO).map { it.id })
            assertEquals(listOf("starred"), byStatus.getValue(TaskStatus.IN_PROGRESS).map { it.id })
            assertEquals(listOf("done"), byStatus.getValue(TaskStatus.DONE).map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskListDateFilterUpdatesWhenLocalDayChangesWithoutTaskEmission() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 4)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val dueOnMayFifth = testTask(
            id = "due-may-fifth",
            categoryId = "inbox",
            deadline = startOfDayLocalMillis(2026, 5, 5),
        )
        val taskRepository = FakeTaskRepository(listOf(dueOnMayFifth))
        val settingsRepository = FakeAppSettingsRepository()
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = TaskListViewModel(
            observeTasksForCategory = ObserveTasksForCategoryUseCase(taskRepository),
            observeAllTasks = ObserveAllTasksUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository()),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(taskRepository, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(taskRepository),
            addCategoryAction = AddCategoryAction(FakeCategoryRepository()),
            observeTaskSortOption = ObserveTaskSortOptionUseCase(settingsRepository),
            saveTaskSortOptionAction = SaveTaskSortOptionAction(settingsRepository),
            observeTodayTasks = ObserveTodayTasksUseCase(taskRepository, localDaySignal),
            updateSectionAction = UpdateSectionAction(taskRepository),
            observeTaskListViewMode = ObserveTaskListViewModeUseCase(settingsRepository),
            saveTaskListViewModeAction = SaveTaskListViewModeAction(settingsRepository),
            updateTaskStatusAction = UpdateTaskStatusAction(taskRepository, scheduler),
            observeTaskImageSummaries = ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            localDaySignal = localDaySignal,
        )

        viewModel.selectFilter(TaskListFilter.Today)
        viewModel.tasksForSelectedCategory.test {
            assertEquals(emptyList(), awaitItem())

            currentDay = LocalDate(2026, 5, 5)
            localDaySignal.refresh()

            assertEquals(listOf("due-may-fifth"), awaitMatching { it.isNotEmpty() }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun matrixCategoriesUpdateWhenLocalDayChangesWithoutTaskEmission() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 4)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val taskRepository = FakeTaskRepository(
            listOf(
                testTask(
                    id = "moves-to-overdue",
                    priority = TaskPriority.HIGH,
                    deadline = startOfDayLocalMillis(2026, 5, 4),
                )
            )
        )
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = MatrixViewModel(
            observeTasksByPriority = ObserveTasksByPriorityUseCase(taskRepository),
            observeTasksForPriority = ObserveTasksForPriorityUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository()),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(taskRepository, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(taskRepository),
            updateTaskStatusAction = UpdateTaskStatusAction(taskRepository, scheduler),
            observeTaskImageSummaries = ObserveTaskImageSummariesUseCase(FakeAttachmentRepository()),
            localDaySignal = localDaySignal,
        )

        viewModel.categorizedTasks.test {
            val initial = awaitMatching { groups ->
                groups.firstOrNull { it.category == TaskCategory.TODAY }
                    ?.tasks?.map { it.id } == listOf("moves-to-overdue")
            }
            assertEquals(
                listOf("moves-to-overdue"),
                initial.first { it.category == TaskCategory.TODAY }.tasks.map { it.id },
            )

            currentDay = LocalDate(2026, 5, 5)
            localDaySignal.refresh()

            val updated = awaitMatching { groups ->
                groups.firstOrNull { it.category == TaskCategory.OVERDUE }
                    ?.tasks?.map { it.id } == listOf("moves-to-overdue")
            }
            assertEquals(
                listOf("moves-to-overdue"),
                updated.first { it.category == TaskCategory.OVERDUE }.tasks.map { it.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noteViewModelTracksListSelectionAndRepositoryWrites() = runTest(dispatcher) {
        val noteRepository = FakeNoteRepository(listOf(testNote(id = "one", title = "One")))
        val viewModel = NoteViewModel(
            observeAllNotes = ObserveAllNotesUseCase(noteRepository),
            observeNoteById = ObserveNoteByIdUseCase(noteRepository),
            addNoteAction = AddNoteAction(noteRepository),
            updateNoteAction = UpdateNoteAction(noteRepository),
            deleteNoteAction = DeleteNoteAction(noteRepository),
        )

        viewModel.noteListItems.test {
            assertEquals(listOf("one"), awaitMatching { it.isNotEmpty() }.map { it.note.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectNote("one")
        viewModel.selectedNote.test {
            assertEquals("one", awaitMatching { it?.id == "one" }?.id)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addNote("Two", "Body")
        viewModel.noteListItems.test {
            assertEquals(listOf("One", "Two"), awaitMatching { it.size == 2 }.map { it.note.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun taskNotificationViewModelProjectsNotificationAndMarksDone() = runTest(dispatcher) {
        val deadline = startOfDayLocalMillis(2026, 5, 4) + 9 * MILLIS_PER_HOUR
        val taskRepository = FakeTaskRepository(
            listOf(testTask(id = "notify", title = "Notify me", deadline = deadline))
        )
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = TaskNotificationViewModel(
            observeTaskById = ObserveTaskByIdUseCase(taskRepository),
            markTaskNotificationDoneAction = MarkTaskNotificationDoneAction(
                taskRepository,
                ToggleTaskCompleteAction(taskRepository, scheduler),
            ),
            dismissTaskNotificationAction = DismissTaskNotificationAction(
                taskRepository,
                AllDayNotificationDismissalStore(FakeAppSettingsRepository()),
                NotificationScheduler(),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.setNotificationEvent(
            NotificationDeepLinkEvent(
                eventId = "notify",
                notificationAtUtcMillis = localToUtc(deadline),
            )
        )

        viewModel.uiState.test {
            val ready = awaitMatching { it.task?.id == "notify" }
            assertEquals("Notify me", ready.taskTitle)
            assertEquals(true, ready.notificationTimeText.isNotBlank())
            assertEquals(true, ready.dueText.isNotBlank())

            var completed = false
            viewModel.markDone { completed = true }
            advanceUntilIdle()

            assertEquals(true, completed)
            assertEquals(TaskStatus.DONE, taskRepository.updated.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun countdownListVisibilityAndOccurrenceUpdateWhenLocalDayChanges() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 6)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val countdown = testCountdown(
            id = "annual",
            targetDate = startOfDayLocalMillis(2025, 5, 10),
        ).copy(
            recurrenceType = RecurrenceType.YEARLY,
            smartListVisibility = SmartListVisibility.THREE_DAYS_EARLY,
        )
        val repository = FakeCountdownRepository(listOf(countdown))
        val scheduler = ScheduleCountdownRemindersAction(NotificationScheduler(), repository)
        val viewModel = CountdownViewModel(
            observeAllCountdowns = ObserveAllCountdownsUseCase(repository),
            deleteCountdownAction = DeleteCountdownAction(repository, scheduler),
            localDaySignal = localDaySignal,
        )

        viewModel.visibleCountdownItems.test {
            assertEquals(emptyList(), awaitItem())

            currentDay = LocalDate(2026, 5, 7)
            localDaySignal.refresh()

            val visible = awaitMatching { it.singleOrNull()?.countdown?.id == "annual" }
            assertEquals(LocalDate(2026, 5, 10), visible.single().effectiveDate)
            assertEquals(3, visible.single().daysUntil)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun countdownDetailOccurrenceUpdatesWhenLocalDayChanges() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 15)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val countdown = testCountdown(
            id = "monthly",
            targetDate = startOfDayLocalMillis(2026, 5, 1),
        ).copy(
            recurrenceType = RecurrenceType.MONTHLY,
            countingMode = CountingMode.COUNT_UP,
        )
        val repository = FakeCountdownRepository(listOf(countdown))
        val scheduler = ScheduleCountdownRemindersAction(NotificationScheduler(), repository)
        val viewModel = CountdownFormViewModel(
            addCountdownAction = AddCountdownAction(repository, scheduler),
            updateCountdownAction = UpdateCountdownAction(repository, scheduler),
            deleteCountdownAction = DeleteCountdownAction(repository, scheduler),
            observeCountdownByIdUseCase = ObserveCountdownByIdUseCase(repository),
            localDaySignal = localDaySignal,
            ioDispatcher = dispatcher,
        )
        viewModel.setCountdownId("monthly")

        viewModel.detailCountdown.test {
            val initial = awaitMatching { it?.effectiveDate == LocalDate(2026, 5, 1) }
            assertEquals(-14, initial?.daysUntil)

            currentDay = LocalDate(2026, 6, 2)
            localDaySignal.refresh()

            val updated = awaitMatching { it?.effectiveDate == LocalDate(2026, 6, 1) }
            assertEquals(-1, updated?.daysUntil)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun calendarMovesItsSingleCountdownOccurrenceWhenLocalDayChanges() = runTest(dispatcher) {
        var currentDay = LocalDate(2026, 5, 4)
        val localDaySignal = LocalDaySignal(currentDate = { currentDay })
        val countdown = testCountdown(
            id = "daily",
            targetDate = startOfDayLocalMillis(2026, 5, 4),
        ).copy(recurrenceType = RecurrenceType.DAILY)
        val countdownRepository = FakeCountdownRepository(listOf(countdown))
        val taskRepository = FakeTaskRepository()
        val settingsRepository = FakeAppSettingsRepository()
        val viewModel = CalendarViewModel(
            observeTasksByDay = ObserveTasksByDayUseCase(taskRepository),
            observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(FakeCategoryRepository()),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(
                taskRepository,
                ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository),
            ),
            observeCalendarViewPreference = ObserveCalendarViewPreferenceUseCase(settingsRepository),
            saveCalendarViewPreference = SaveCalendarViewPreferenceAction(settingsRepository),
            observeCalendarListDisplayModePreference =
                ObserveCalendarListDisplayModePreferenceUseCase(settingsRepository),
            saveCalendarListDisplayModePreference =
                SaveCalendarListDisplayModePreferenceAction(settingsRepository),
            localDaySignal = localDaySignal,
            ioDispatcher = dispatcher,
        )
        val mayFourth = dayKey(startOfDayLocalMillis(2026, 5, 4))
        val mayFifth = dayKey(startOfDayLocalMillis(2026, 5, 5))

        viewModel.tasksByDay.test {
            val initial = awaitMatching { it[mayFourth]?.singleOrNull()?.id == "countdown_daily" }
            assertEquals(1, initial.values.flatten().count { it.id == "countdown_daily" })

            currentDay = LocalDate(2026, 5, 5)
            localDaySignal.refresh()

            val updated = awaitMatching { it[mayFifth]?.singleOrNull()?.id == "countdown_daily" }
            assertEquals(null, updated[mayFourth]?.firstOrNull { it.id == "countdown_daily" })
            assertEquals(1, updated.values.flatten().count { it.id == "countdown_daily" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun countdownViewModelFiltersByCountdownType() = runTest(dispatcher) {
        val countdownRepository = FakeCountdownRepository(
            listOf(
                testCountdown(id = "birthday", countdownType = CountdownType.BIRTHDAY)
                    .copy(smartListVisibility = SmartListVisibility.ALWAYS),
                testCountdown(id = "holiday", countdownType = CountdownType.HOLIDAY)
                    .copy(smartListVisibility = SmartListVisibility.ALWAYS),
            )
        )
        val viewModel = CountdownViewModel(
            observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
            deleteCountdownAction = DeleteCountdownAction(
                countdownRepository,
                ScheduleCountdownRemindersAction(NotificationScheduler(), countdownRepository),
            ),
        )

        viewModel.hasStoredCountdowns.test {
            assertEquals(true, awaitMatching { it })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.visibleCountdownItems.test {
            assertEquals(
                listOf("birthday", "holiday"),
                awaitMatching { it.size == 2 }.map { it.countdown.id },
            )
            viewModel.selectFilter(CountdownType.BIRTHDAY)
            assertEquals(
                listOf("birthday"),
                awaitMatching { items ->
                    items.map { it.countdown.id } == listOf("birthday")
                }.map { it.countdown.id },
            )
            viewModel.selectFilter(null)
            assertEquals(
                listOf("birthday", "holiday"),
                awaitMatching { it.size == 2 }.map { it.countdown.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching item emitted")
    }
}
