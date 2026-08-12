package com.udnahc.opentasks.domain.usecase.task

import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTransformsTest {
    private val dayStart = startOfDayLocalMillis(2026, 5, 4)

    @Test
    fun sortCalendarTasksForDayOrdersAllDayBeforeTimedAndCountdownsLastWithinSameTime() {
        val timedLater = testTask(id = "timed-later", deadline = dayStart + 15 * MILLIS_PER_HOUR)
        val countdown = testTask(id = "${COUNTDOWN_ID_PREFIX}birthday", title = "Birthday", deadline = dayStart, isAllDay = true)
        val allDayTask = testTask(id = "all-day", title = "All Day", deadline = dayStart, isAllDay = true)
        val timedEarlier = testTask(id = "timed-earlier", deadline = dayStart + 9 * MILLIS_PER_HOUR)

        val sorted = sortCalendarTasksForDay(listOf(timedLater, countdown, allDayTask, timedEarlier))

        assertEquals(listOf("all-day", "${COUNTDOWN_ID_PREFIX}birthday", "timed-earlier", "timed-later"), sorted.map { it.id })
    }

    @Test
    fun calendarProjectionKeepsAllDayAndTimedRowsSorted() {
        val timed = testTask(id = "timed", deadline = dayStart + 10 * MILLIS_PER_HOUR)
        val midnight = testTask(id = "midnight", deadline = dayStart)
        val allDay = testTask(id = "all-day", deadline = dayStart + MILLIS_PER_HOUR, isAllDay = true)

        val result = projectCalendarDay(
            listOf(timed, allDay, midnight),
            targetDayKey = dayKey(dayStart),
            todayDayKey = dayKey(dayStart),
        )

        assertEquals(listOf("midnight", "all-day"), result.allDayRows.map { it.task.id })
        assertEquals(listOf("timed"), result.timedRows.map { it.task.id })
        assertTrue(result.allDayRows.all { it.isAllDay })
        assertEquals(600, result.timedRows.single().startMinutes)
        assertEquals("10:00 AM", result.timedRows.single().timelineTimeText)
        assertEquals("May 4", result.timedRows.single().cardDateText)
        assertTrue(result.isToday)
    }

    @Test
    fun calendarProjectionUsesFixedPreviewsAndBoundedDynamicPrefix() {
        val tasks = (0..6).map { index ->
            testTask(
                id = "task-$index",
                deadline = dayStart + (index + 1) * MILLIS_PER_HOUR,
            )
        }
        val projection = projectCalendarDay(tasks, dayKey(dayStart), dayKey(dayStart))

        assertEquals(5, projection.monthPreview.rows.size)
        assertEquals(2, projection.monthPreview.overflowCount)
        assertEquals(2, calendarTaskPrefix(projection.rows, maxVisible = 2).rows.size)
        assertEquals(5, calendarTaskPrefix(projection.rows, maxVisible = 2).overflowCount)
    }

    @Test
    fun observeTasksByDayGroupsOnlyDatedTasksAndSortsEachDay() = kotlinx.coroutines.test.runTest {
        val repository = com.udnahc.opentasks.testutil.FakeTaskRepository(
            listOf(
                testTask(id = "none", deadline = null),
                testTask(id = "later", deadline = dayStart + 11 * MILLIS_PER_HOUR),
                testTask(id = "all-day", priority = TaskPriority.HIGH, deadline = dayStart, isAllDay = true),
                testTask(id = "earlier", deadline = dayStart + 8 * MILLIS_PER_HOUR),
            )
        )

        ObserveTasksByDayUseCase(repository)().test {
            val byDay = awaitItem()
            assertEquals(listOf("all-day", "earlier", "later"), byDay.getValue(dayKey(dayStart)).map { it.id })
            assertEquals(1, byDay.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
