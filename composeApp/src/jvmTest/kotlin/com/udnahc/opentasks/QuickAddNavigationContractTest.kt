package com.udnahc.opentasks

import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.navigation.Screen
import com.udnahc.opentasks.navigation.asQuickAddTask
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class QuickAddNavigationContractTest {
    @Test
    fun matrixTaskListAndCalendarFabContextsMatchExistingFullEditorDefaults() {
        assertEquals(
            Screen.CreateTask(),
            taskFabCreationScreen(0, "ignored", 9, 8, 2030),
        )
        assertEquals(
            Screen.CreateTask(categoryId = "projects"),
            taskFabCreationScreen(1, "projects", 9, 8, 2030),
        )
        assertEquals(
            Screen.CreateTask(day = 9, month = 8, year = 2030),
            taskFabCreationScreen(2, "ignored", 9, 8, 2030),
        )
    }

    @Test
    fun quickAndFullDestinationsCarryTheSameCreationContext() {
        val full = Screen.CreateTask(
            priorityOrdinal = TaskPriority.MEDIUM.ordinal,
            categoryId = "work",
            day = 29,
            month = 2,
            year = 2028,
        )

        val quick = full.asQuickAddTask()

        assertEquals(full.priorityOrdinal, quick.priorityOrdinal)
        assertEquals(full.categoryId, quick.categoryId)
        assertEquals(full.day, quick.day)
        assertEquals(full.month, quick.month)
        assertEquals(full.year, quick.year)
        assertEquals(TaskPriority.MEDIUM, quick.creationContext().priority)
        assertEquals("work", quick.creationContext().categoryId)
        assertEquals(LocalDate(2028, 2, 29), quick.creationContext().fallbackDate)
    }

    @Test
    fun quadrantPriorityAndInvalidDateRemainSafeAndEntryScoped() {
        val first = Screen.QuickAddTask(
            priorityOrdinal = TaskPriority.HIGH.ordinal,
            categoryId = AppConstants.DEFAULT_INBOX_ID,
            day = 31,
            month = 2,
            year = 2028,
        )
        val second = first.copy(priorityOrdinal = TaskPriority.LOW.ordinal)

        assertEquals(TaskPriority.HIGH, first.creationContext().priority)
        assertNull(first.creationContext().fallbackDate)
        assertNotEquals(quickAddTaskViewModelKey(first), quickAddTaskViewModelKey(second))
    }
}
