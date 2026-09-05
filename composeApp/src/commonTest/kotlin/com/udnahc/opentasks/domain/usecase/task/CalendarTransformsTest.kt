package com.udnahc.opentasks.domain.usecase.task

import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.testutil.testTask
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun observeTasksByDayGroupsOnlyDatedTasksInRepositoryOrder() = kotlinx.coroutines.test.runTest {
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
            assertEquals(listOf("later", "all-day", "earlier"), byDay.getValue(dayKey(dayStart)).map { it.id })
            assertEquals(1, byDay.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun projectionCacheDoesNoFormattingForAnUnchangedDayWhenAnotherDayChanges() {
        val firstKey = dayKey(dayStart)
        val secondStart = dayStart + 24 * MILLIS_PER_HOUR
        val secondKey = dayKey(secondStart)
        val firstLater = testTask(id = "first-later", deadline = dayStart + 4 * MILLIS_PER_HOUR)
        val firstEarlier = testTask(id = "first-earlier", deadline = dayStart + MILLIS_PER_HOUR)
        val secondLater = testTask(id = "second-later", deadline = secondStart + 5 * MILLIS_PER_HOUR)
        val secondEarlier = testTask(id = "second-earlier", deadline = secondStart + 2 * MILLIS_PER_HOUR)
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)

        val initial = cache.renderMonth(
            today = LocalDate(2026, 5, 4),
            dayInputs = mapOf(
                firstKey to listOf(firstLater, firstEarlier),
                secondKey to listOf(secondLater, secondEarlier),
            ),
        )
        val firstProjection = initial.calendarDaysByDay.getValue(firstKey)
        val firstRows = firstProjection.rows
        assertEquals(listOf("first-earlier", "first-later"), firstRows.map { it.task.id })
        formatter.clearRecordedWork()

        val updated = cache.renderMonth(
            today = LocalDate(2026, 5, 4),
            dayInputs = mapOf(
                firstKey to listOf(firstLater, firstEarlier),
                secondKey to listOf(secondLater.copy(title = "Updated"), secondEarlier),
            ),
        )

        val updatedFirstProjection = updated.calendarDaysByDay.getValue(firstKey)
        assertTrue(firstProjection === updatedFirstProjection)
        assertTrue(firstRows === updatedFirstProjection.rows)
        assertTrue(firstRows.zip(updatedFirstProjection.rows).all { (before, after) -> before === after })
        assertEquals(
            listOf("second-earlier", "second-later"),
            updated.calendarDaysByDay.getValue(secondKey).rows.map { it.task.id },
        )
        assertTrue(formatter.formattedMillis.none { dayKey(it) == firstKey })
        assertTrue(formatter.formattedMillis.any { dayKey(it) == secondKey })
    }

    @Test
    fun projectionCacheReusesRowsForTodayOnlyChangesAndDropsRemovedDays() {
        val firstKey = dayKey(dayStart)
        val secondStart = dayStart + 24 * MILLIS_PER_HOUR
        val secondKey = dayKey(secondStart)
        val first = testTask(id = "first", deadline = dayStart + MILLIS_PER_HOUR)
        val second = testTask(id = "second", deadline = secondStart + MILLIS_PER_HOUR)
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)
        val inputs = mapOf(firstKey to listOf(first), secondKey to listOf(second))
        val initial = cache.renderMonth(LocalDate(2026, 5, 4), inputs)
        formatter.clearRecordedWork()

        val rolled = cache.renderMonth(LocalDate(2026, 5, 5), inputs)

        assertFalse(rolled.calendarDaysByDay.getValue(firstKey).isToday)
        assertTrue(rolled.calendarDaysByDay.getValue(secondKey).isToday)
        assertTrue(
            initial.calendarDaysByDay.getValue(firstKey).rows ===
                rolled.calendarDaysByDay.getValue(firstKey).rows,
        )
        assertTrue(
            initial.calendarDaysByDay.getValue(secondKey).rows ===
                rolled.calendarDaysByDay.getValue(secondKey).rows,
        )
        assertEquals(0, formatter.nonHourFormatCalls)

        val retainedProjection = rolled.calendarDaysByDay.getValue(firstKey)
        cache.renderMonth(LocalDate(2026, 5, 5), mapOf(secondKey to listOf(second)))
        val restored = cache.renderMonth(LocalDate(2026, 5, 5), inputs)
        assertFalse(retainedProjection === restored.calendarDaysByDay.getValue(firstKey))
    }

    @Test
    fun projectionCacheBuildsOneHourLabelSetPerContextIncludingEmptyDayMode() {
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)

        val first = cache.render(
            today = LocalDate(2026, 5, 4),
            dayInputs = emptyMap(),
            formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.DAY,
            listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null,
            monthSelectedDayKey = null,
        )
        val second = cache.render(
            today = LocalDate(2026, 5, 4),
            dayInputs = emptyMap(),
            formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.THREE_DAY,
            listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null,
            monthSelectedDayKey = null,
        )

        assertEquals(24, first.timelineHourLabels.size)
        assertTrue(first.timelineHourLabels === second.timelineHourLabels)
        assertEquals(24, formatter.hourFormatCalls)

        val dayKey = dayKey(dayStart)
        val task = testTask(id = "context", deadline = dayStart + MILLIS_PER_HOUR)
        val firstProjection = cache.render(
            today = LocalDate(2026, 5, 4),
            dayInputs = mapOf(dayKey to listOf(task)),
            formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.DAY,
            listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null,
            monthSelectedDayKey = null,
        ).calendarDaysByDay.getValue(dayKey)
        formatter.clearRecordedWork()
        formatter.contextKey = "second-context"
        val changedContext = cache.render(
            today = LocalDate(2026, 5, 4),
            dayInputs = mapOf(dayKey to listOf(task)),
            formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.DAY,
            listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null,
            monthSelectedDayKey = null,
        )

        assertFalse(first.timelineHourLabels === changedContext.timelineHourLabels)
        assertEquals(48, formatter.hourFormatCalls)
        assertFalse(firstProjection === changedContext.calendarDaysByDay.getValue(dayKey))
        assertTrue(formatter.nonHourFormatCalls > 0)
    }

    @Test
    fun yearModeUsesOnlyDayKeysUntilTheFirstCoherentModeSwitch() {
        val key = dayKey(dayStart)
        val later = testTask(id = "year-later", deadline = dayStart + 8 * MILLIS_PER_HOUR)
        val earlier = testTask(id = "year-earlier", deadline = dayStart + MILLIS_PER_HOUR)
        val unsortedInput = listOf(later, earlier)
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)

        val year = cache.render(
            today = LocalDate(2026, 5, 4),
            dayInputs = mapOf(key to unsortedInput),
            formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.YEAR,
            listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null,
            monthSelectedDayKey = null,
        )

        assertEquals(setOf(key), year.taskDayKeys)
        assertTrue(year.calendarDaysByDay.isEmpty())
        assertTrue(year.timelineHourLabels.isEmpty())
        assertEquals(0, formatter.nonHourFormatCalls)
        assertEquals(0, formatter.hourFormatCalls)

        val month = cache.renderMonth(LocalDate(2026, 5, 4), mapOf(key to unsortedInput))

        assertEquals(CalendarViewPreference.MONTH, month.viewPreference)
        assertEquals(
            listOf("year-earlier", "year-later"),
            month.calendarDaysByDay.getValue(key).rows.map { it.task.id },
        )
        assertTrue(formatter.nonHourFormatCalls > 0)
        assertEquals(0, formatter.hourFormatCalls)
    }

    private fun CalendarProjectionCache.renderMonth(
        today: LocalDate,
        dayInputs: Map<Long, List<com.udnahc.opentasks.data.model.Task>>,
    ) = render(
        today = today,
        dayInputs = dayInputs,
        formattingContextKey = "test-context",
        viewPreference = CalendarViewPreference.MONTH,
        listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
        listSelectedDayKey = null,
        monthSelectedDayKey = null,
    )
}

internal class CountingCalendarFormatter : DateTimeTextFormatter by EnglishDateTimeFormatter {
    var contextKey: String = "test-context"
    var nonHourFormatCalls: Int = 0
        private set
    var hourFormatCalls: Int = 0
        private set
    val formattedMillis = mutableListOf<Long>()

    override val formattingContextKey: String get() = contextKey

    override fun formatShortDate(localMillis: Long): String = record(localMillis) {
        EnglishDateTimeFormatter.formatShortDate(localMillis)
    }

    override fun formatDateLabel(localMillis: Long): String = record(localMillis) {
        EnglishDateTimeFormatter.formatDateLabel(localMillis)
    }

    override fun formatTime(localMillis: Long): String = record(localMillis) {
        EnglishDateTimeFormatter.formatTime(localMillis)
    }

    override fun formatHour(hour: Int): String {
        hourFormatCalls += 1
        return EnglishDateTimeFormatter.formatHour(hour)
    }

    fun clearRecordedWork() {
        nonHourFormatCalls = 0
        formattedMillis.clear()
    }

    private fun record(localMillis: Long, format: () -> String): String {
        nonHourFormatCalls += 1
        formattedMillis += localMillis
        return format()
    }
}
