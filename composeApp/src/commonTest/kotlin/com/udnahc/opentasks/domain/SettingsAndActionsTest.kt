package com.udnahc.opentasks.domain

import app.cash.turbine.test
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.settings.SaveTextSizePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.tag.TagTaskAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import com.udnahc.opentasks.testutil.FakeAppSettingsRepository
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeNoteRepository
import com.udnahc.opentasks.testutil.FakeTagRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsAndActionsTest {
    @Test
    fun settingsActionsPersistAndUseCasesExposeTypedDefaultsAndUpdates() = runTest {
        val repository = FakeAppSettingsRepository()

        ObserveThemePreferenceUseCase(repository)().test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            SaveThemePreferenceAction(repository)(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        ObserveTextSizePreferenceUseCase(repository)().test {
            assertEquals(TextSizePreference.SMALL, awaitItem())
            SaveTextSizePreferenceAction(repository)(TextSizePreference.LARGE)
            assertEquals(TextSizePreference.LARGE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        ObserveTaskSortOptionUseCase(repository)().test {
            assertEquals(TaskSortOption.RECENTLY_UPDATED, awaitItem())
            SaveTaskSortOptionAction(repository)(TaskSortOption.BY_TITLE)
            assertEquals(TaskSortOption.BY_TITLE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        ObserveTaskListViewModeUseCase(repository)().test {
            assertEquals(TaskListViewMode.LIST, awaitItem())
            SaveTaskListViewModeAction(repository)(TaskListViewMode.BOARD)
            assertEquals(TaskListViewMode.BOARD, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        ObserveCalendarViewPreferenceUseCase(repository)().test {
            assertEquals(CalendarViewPreference.MONTH, awaitItem())
            SaveCalendarViewPreferenceAction(repository)(CalendarViewPreference.THREE_DAY)
            assertEquals(CalendarViewPreference.THREE_DAY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        ObserveCalendarListDisplayModePreferenceUseCase(repository)().test {
            assertEquals(CalendarListDisplayModePreference.TIMELINE, awaitItem())
            SaveCalendarListDisplayModePreferenceAction(repository)(CalendarListDisplayModePreference.CARD)
            assertEquals(CalendarListDisplayModePreference.CARD, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun simpleActionsWriteExpectedEntitiesToRepositories() = runTest {
        val categoryRepository = FakeCategoryRepository()
        AddCategoryAction(categoryRepository)("Projects")
        assertEquals("Projects", categoryRepository.inserted.single().name)

        val noteRepository = FakeNoteRepository()
        AddNoteAction(noteRepository)("Title", "Body")
        assertEquals("Title", noteRepository.inserted.single().title)

        val note = testNote(id = "note-1", title = "Old")
        UpdateNoteAction(noteRepository)(note.copy(title = "New"))
        assertEquals("New", noteRepository.updated.last().title)

        DeleteNoteAction(noteRepository)(note)
        assertTrue(noteRepository.deleted.last().isDeleted)

        val tagRepository = FakeTagRepository()
        val tagId = AddTagAction(tagRepository)("Focus", "#00ff00")
        TagTaskAction(tagRepository)("task-1", tagId)
        assertEquals("Focus", tagRepository.insertedTags.single().name)
        assertEquals("task-1", tagRepository.insertedTaskTags.single().taskId)
    }

    @Test
    fun taskActionsToggleStar() = runTest {
        val task = testTask(id = "task-1", isStarred = false, section = "Old")
        val repository = FakeTaskRepository(listOf(task))

        ToggleTaskStarredAction(repository)(task.id)
        assertEquals(true, repository.updated.last().isStarred)

    }
}
