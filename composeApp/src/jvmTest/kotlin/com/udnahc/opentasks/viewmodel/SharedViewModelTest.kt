package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import app.cash.turbine.ReceiveTurbine
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateSectionAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTodayTasksUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
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
        val categoryRepository = FakeCategoryRepository(listOf(testCategory(id = "inbox", name = "Inbox")))
        val scheduler = ScheduleTaskRemindersAction(NotificationScheduler(), taskRepository)
        val viewModel = TaskListViewModel(
            observeTasksForCategory = ObserveTasksForCategoryUseCase(taskRepository),
            observeAllTasks = ObserveAllTasksUseCase(taskRepository),
            observeAllCategories = ObserveAllCategoriesUseCase(categoryRepository),
            toggleTaskCompleteAction = ToggleTaskCompleteAction(taskRepository, scheduler),
            toggleTaskStarredAction = ToggleTaskStarredAction(taskRepository),
            addCategoryAction = AddCategoryAction(categoryRepository),
            observeTaskSortOption = ObserveTaskSortOptionUseCase(settingsRepository),
            saveTaskSortOptionAction = SaveTaskSortOptionAction(settingsRepository),
            observeTodayTasks = ObserveTodayTasksUseCase(taskRepository),
            updateSectionAction = UpdateSectionAction(taskRepository),
            observeTaskListViewMode = ObserveTaskListViewModeUseCase(settingsRepository),
            saveTaskListViewModeAction = SaveTaskListViewModeAction(settingsRepository),
            updateTaskStatusAction = UpdateTaskStatusAction(taskRepository, scheduler),
        )

        viewModel.selectCategory("inbox")
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
    fun noteViewModelTracksListSelectionAndRepositoryWrites() = runTest(dispatcher) {
        val noteRepository = FakeNoteRepository(listOf(testNote(id = "one", title = "One")))
        val viewModel = NoteViewModel(
            observeAllNotes = ObserveAllNotesUseCase(noteRepository),
            observeNoteById = ObserveNoteByIdUseCase(noteRepository),
            addNoteAction = AddNoteAction(noteRepository),
            updateNoteAction = UpdateNoteAction(noteRepository),
            deleteNoteAction = DeleteNoteAction(noteRepository),
        )

        viewModel.notes.test {
            assertEquals(listOf("one"), awaitMatching { it.isNotEmpty() }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectNote("one")
        viewModel.selectedNote.test {
            assertEquals("one", awaitMatching { it?.id == "one" }?.id)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.addNote("Two", "Body")
        viewModel.notes.test {
            assertEquals(listOf("One", "Two"), awaitMatching { it.size == 2 }.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun countdownViewModelFiltersByCountdownType() = runTest(dispatcher) {
        val countdownRepository = FakeCountdownRepository(
            listOf(
                testCountdown(id = "birthday", countdownType = CountdownType.BIRTHDAY),
                testCountdown(id = "holiday", countdownType = CountdownType.HOLIDAY),
            )
        )
        val viewModel = CountdownViewModel(
            observeAllCountdowns = ObserveAllCountdownsUseCase(countdownRepository),
            deleteCountdownAction = DeleteCountdownAction(
                countdownRepository,
                ScheduleCountdownRemindersAction(NotificationScheduler(), countdownRepository),
            ),
        )

        viewModel.filteredCountdowns.test {
            assertEquals(listOf("birthday", "holiday"), awaitMatching { it.size == 2 }.map { it.id })
            viewModel.selectFilter(CountdownType.BIRTHDAY)
            assertEquals(listOf("birthday"), awaitMatching { it.map { countdown -> countdown.id } == listOf("birthday") }.map { it.id })
            viewModel.selectFilter(null)
            assertEquals(listOf("birthday", "holiday"), awaitMatching { it.size == 2 }.map { it.id })
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
