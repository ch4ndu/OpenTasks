package com.udnahc.opentasks.domain.usecase.task

import app.cash.turbine.test
import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
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

    @Test
    fun visibleRangeBoundsContinuationAndExcludesMidnightEndWithoutChangingClassic() {
        val may5 = startOfDayLocalMillis(2026, 5, 5)
        val may6 = startOfDayLocalMillis(2026, 5, 6)
        val firstKey = dayKey(may5)
        val secondKey = dayKey(may6)
        val imported = testTask(id = "imported", deadline = dayStart, endDeadline = may6, isAllDay = true)
        val overnight = testTask(id = "overnight", deadline = dayStart + 22 * MILLIS_PER_HOUR, endDeadline = may5 + 10 * MILLIS_PER_HOUR)
        val midnightEnd = testTask(id = "midnight-end", deadline = dayStart + 23 * MILLIS_PER_HOUR, endDeadline = may5)
        val ancient = startOfDayLocalMillis(1990, 1, 1)
        val longSpan = testTask(id = "long-span", deadline = ancient, endDeadline = startOfDayLocalMillis(2090, 1, 1), isAllDay = true)
        val inputs = mapOf(dayKey(dayStart) to listOf(imported, overnight, midnightEnd), dayKey(ancient) to listOf(longSpan))
        val window = CalendarVisibleRange(LocalDate(2026, 5, 5), LocalDate(2026, 5, 7))
        val expanded = expandCalendarVisibleInputs(inputs, window)
        assertEquals(setOf(firstKey, secondKey), expanded.keys)
        assertEquals(setOf("imported", "overnight", "long-span"), expanded.getValue(firstKey).map { it.id }.toSet())
        assertEquals(listOf("long-span"), expanded.getValue(secondKey).map { it.id })
        assertTrue(expanded.getValue(firstKey).single { it.id == "overnight" } === overnight)
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)
        val week = cache.render(
            today = LocalDate(2026, 5, 5), dayInputs = inputs, formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.WEEK, listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null, monthSelectedDayKey = null, visibleRange = window,
        )
        val continuation = week.calendarDaysByDay.getValue(firstKey).timedRows.single()
        assertTrue(continuation.task === overnight)
        assertEquals(0, continuation.layoutStartMinutes)
        assertEquals(600, continuation.endMinutes)
        assertEquals("12:00 AM", continuation.timelineTimeText)
        assertEquals("May 5", continuation.cardDateText)
        assertTrue(week.timelineHourLabels.isEmpty())
        assertEquals(0, formatter.hourFormatCalls)
        val day = cache.render(
            today = LocalDate(2026, 5, 5), dayInputs = inputs, formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.DAY, listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null, monthSelectedDayKey = null, visibleRange = window,
        )
        assertTrue(week.calendarDaysByDay.getValue(firstKey) === day.calendarDaysByDay.getValue(firstKey))
        assertEquals(24, day.timelineHourLabels.size)
        assertEquals(24, formatter.hourFormatCalls)
        formatter.clearRecordedWork()
        val year = cache.render(
            today = LocalDate(2026, 5, 5), dayInputs = inputs, formattingContextKey = formatter.formattingContextKey,
            viewPreference = CalendarViewPreference.YEAR, listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
            listSelectedDayKey = null, monthSelectedDayKey = null, visibleRange = window,
        )
        assertEquals(setOf(firstKey, secondKey), year.taskDayKeys)
        assertTrue(year.calendarDaysByDay.isEmpty())
        assertTrue(year.timelineHourLabels.isEmpty())
        assertEquals(0, formatter.nonHourFormatCalls)
        val classic = cache.renderMonth(LocalDate(2026, 5, 5), inputs)
        assertEquals(inputs.keys, classic.taskDayKeys)
        assertTrue(classic.calendarDaysByDay.getValue(firstKey).rows.isEmpty())
        assertEquals("10:00 PM", classic.calendarDaysByDay.getValue(dayKey(dayStart)).timedRows.first().timelineTimeText)
    }

    @Test
    fun overlapLanesReuseFirstFreeLaneWithinConnectedGroupsWithStableTies() {
        val first = testTask(id = "first", deadline = dayStart + 9 * MILLIS_PER_HOUR, endDeadline = dayStart + 11 * MILLIS_PER_HOUR)
        val second = testTask(id = "second", deadline = dayStart + 10 * MILLIS_PER_HOUR, endDeadline = dayStart + 12 * MILLIS_PER_HOUR)
        val touching = testTask(id = "touching", deadline = dayStart + 11 * MILLIS_PER_HOUR, endDeadline = dayStart + 13 * MILLIS_PER_HOUR)
        val separate = testTask(id = "separate", deadline = dayStart + 13 * MILLIS_PER_HOUR, endDeadline = dayStart + 14 * MILLIS_PER_HOUR)
        val tieA = testTask(id = "tie-a", deadline = dayStart + 16 * MILLIS_PER_HOUR, endDeadline = dayStart + 17 * MILLIS_PER_HOUR)
        val tieB = tieA.copy(id = "tie-b")
        val inputs = listOf(tieB, touching, second, separate, first, tieA)
        val projected = projectCalendarDay(inputs, dayKey(dayStart), dayKey(dayStart)).timedRows
        val byId = projected.associateBy { it.task.id }

        assertEquals(0, byId.getValue("first").laneIndex)
        assertEquals(1, byId.getValue("second").laneIndex)
        assertEquals(0, byId.getValue("touching").laneIndex)
        listOf("first", "second", "touching").forEach { id -> assertEquals(2, byId.getValue(id).laneCount) }
        assertEquals(0, byId.getValue("separate").laneIndex)
        assertEquals(1, byId.getValue("separate").laneCount)
        assertEquals(0, byId.getValue("tie-a").laneIndex)
        assertEquals(1, byId.getValue("tie-b").laneIndex)
        assertEquals(2, byId.getValue("tie-a").laneCount)
        assertEquals(2, byId.getValue("tie-b").laneCount)
        assertTrue(projected.all { row -> inputs.any { it === row.task } })
        val reversed = projectCalendarDay(inputs.reversed(), dayKey(dayStart), dayKey(dayStart)).timedRows
        assertEquals(projected.map { Triple(it.task.id, it.laneIndex, it.laneCount) },
            reversed.map { Triple(it.task.id, it.laneIndex, it.laneCount) })
    }

    @Test
    fun shortEventsUseSeparateDisplayLanesWithoutChangingTheirRealEndpoints() {
        val first = testTask(id = "first", deadline = dayStart + 600 * MILLIS_PER_MINUTE, endDeadline = dayStart + 601 * MILLIS_PER_MINUTE)
        val second = testTask(id = "second", deadline = dayStart + 602 * MILLIS_PER_MINUTE, endDeadline = dayStart + 603 * MILLIS_PER_MINUTE)
        val touching = testTask(id = "touching", deadline = dayStart + 617 * MILLIS_PER_MINUTE, endDeadline = dayStart + 632 * MILLIS_PER_MINUTE)

        val rows = projectCalendarDay(listOf(first, second, touching), dayKey(dayStart), dayKey(dayStart)).timedRows

        assertEquals(listOf(601, 603, 632), rows.map { it.endMinutes })
        assertEquals(listOf(615, 617, 632), rows.map { it.displayEndMinutes })
        assertEquals(listOf(0, 1, 0), rows.map { it.laneIndex })
        assertEquals(listOf(2, 2, 1), rows.map { it.laneCount })
        assertTrue(rows[0].task === first)
        assertTrue(rows[1].task === second)
        assertEquals("10:00 AM", rows[0].timelineTimeText)
        assertEquals("10:02 AM", rows[1].timelineTimeText)
    }

    @Test
    fun syncAcknowledgementReusesFormattingAndLanesButRefreshesCallbackTasks() {
        val task = testTask(id = "sync", deadline = dayStart + 10 * MILLIS_PER_HOUR)
        val unchanged = testTask(id = "unchanged", deadline = dayStart + 11 * MILLIS_PER_HOUR)
        val key = dayKey(dayStart)
        val formatter = CountingCalendarFormatter()
        val cache = CalendarProjectionCache(formatter)
        val initial = cache.renderMonth(LocalDate(2026, 5, 4), mapOf(key to listOf(task, unchanged)))
        val originalRow = initial.calendarDaysByDay.getValue(key).rows.first()
        formatter.clearRecordedWork()
        val acknowledged = task.copy(pbId = "remote-id", isSynced = true)

        val refreshed = cache.renderMonth(LocalDate(2026, 5, 4), mapOf(key to listOf(acknowledged, unchanged)))
        val day = refreshed.calendarDaysByDay.getValue(key)

        assertEquals(0, formatter.nonHourFormatCalls)
        assertEquals(0, formatter.hourFormatCalls)
        assertTrue(day.rows.first().task === acknowledged)
        assertEquals(originalRow.copy(task = acknowledged), day.rows.first())
        assertTrue(day.timedRows.first() === day.rows.first())
        assertTrue(day.monthPreview.rows.first() === day.rows.first())
        assertTrue(day.rows.last() === initial.calendarDaysByDay.getValue(key).rows.last())

        cache.renderMonth(LocalDate(2026, 5, 4), mapOf(key to listOf(acknowledged.copy(deadline = dayStart + 12 * MILLIS_PER_HOUR), unchanged)))
        assertTrue(formatter.nonHourFormatCalls > 0)
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
