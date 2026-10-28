package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.extensions.dayKeyToMillis
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import kotlinx.datetime.LocalDate

/** A UI-selected civil window; task spans never determine the size of expansion. */
data class CalendarVisibleRange(val first: LocalDate, val endExclusive: LocalDate)

private fun CalendarVisibleRange.firstKey(): Long = dayKeyFromDate(first.year, first.monthNumber, first.dayOfMonth)
private fun CalendarVisibleRange.endKey(): Long = dayKeyFromDate(endExclusive.year, endExclusive.monthNumber, endExclusive.dayOfMonth)

/** Clips a task's half-open interval to the bounded window before enumerating any civil days. */
private fun calendarTaskDayRange(task: Task, window: CalendarVisibleRange): LongRange? {
    val start = task.deadline ?: return null
    val firstDate = localMillisToLocalDate(start)
    val end = task.endDeadline?.takeIf { it > start }
    val lastDate = if (end == null) firstDate else {
        val endDate = localMillisToLocalDate(end)
        val midnight = startOfDayLocalMillis(endDate.year, endDate.monthNumber, endDate.dayOfMonth)
        if (end == midnight) endDate.minus(1, DateTimeUnit.DAY) else endDate
    }
    val firstKey = maxOf(dayKey(start), window.firstKey())
    val lastKey = minOf(dayKeyFromDate(lastDate.year, lastDate.monthNumber, lastDate.dayOfMonth), window.endKey() - 1)
    return (firstKey..lastKey).takeIf { firstKey <= lastKey }
}

internal fun expandCalendarVisibleInputs(
    dayInputs: Map<Long, List<Task>>,
    window: CalendarVisibleRange,
): Map<Long, List<Task>> {
    val expanded = linkedMapOf<Long, MutableList<Task>>()
    dayInputs.values.forEach { tasks -> tasks.forEach { task ->
        calendarTaskDayRange(task, window)?.forEach { day -> expanded.getOrPut(day) { mutableListOf() }.add(task) }
    } }
    return expanded
}

internal fun calendarVisibleTaskDayKeys(dayInputs: Map<Long, List<Task>>, window: CalendarVisibleRange): Set<Long> {
    val keys = mutableSetOf<Long>()
    val windowSize = (window.endKey() - window.firstKey()).coerceAtLeast(0)
    for (tasks in dayInputs.values) for (task in tasks) {
        if (keys.size.toLong() == windowSize) return keys
        calendarTaskDayRange(task, window)?.forEach { day -> keys.add(day) }
    }
    return keys
}

data class CalendarDayTasks(
    val allDayTasks: List<Task> = emptyList(),
    val timedTasks: List<Task> = emptyList(),
    val timedTaskStartMinutes: Map<String, Int> = emptyMap(),
    val timedTaskTimeText: Map<String, String> = emptyMap(),
)

data class CalendarTaskRowProjection(
    val task: Task,
    val isAllDay: Boolean,
    val startMinutes: Int,
    val timelineTimeText: String?,
    val cardDateText: String,
    val cardTimeText: String,
    val layoutStartMinutes: Int = startMinutes,
    val endMinutes: Int = startMinutes + 30,
    val laneIndex: Int = 0,
    val laneCount: Int = 1,
)

data class CalendarTaskPrefix(
    val rows: List<CalendarTaskRowProjection>,
    val overflowCount: Int,
)

data class CalendarDayProjection(
    val dayKey: Long = 0L,
    val formattedDate: String = "",
    val monthDateText: String = "",
    val isToday: Boolean = false,
    val rows: List<CalendarTaskRowProjection> = emptyList(),
    val allDayRows: List<CalendarTaskRowProjection> = emptyList(),
    val timedRows: List<CalendarTaskRowProjection> = emptyList(),
    val allDayPreview: CalendarTaskPrefix = CalendarTaskPrefix(emptyList(), 0),
    val monthPreview: CalendarTaskPrefix = CalendarTaskPrefix(emptyList(), 0),
)

internal val EMPTY_CALENDAR_DAY_PROJECTION = CalendarDayProjection()

data class CalendarRenderState(
    val viewPreference: CalendarViewPreference,
    val listDisplayModePreference: CalendarListDisplayModePreference,
    val today: LocalDate,
    val taskDayKeys: Set<Long> = emptySet(),
    val calendarDaysByDay: Map<Long, CalendarDayProjection> = emptyMap(),
    val selectedDayProjection: CalendarDayProjection = EMPTY_CALENDAR_DAY_PROJECTION,
    val timelineHourLabels: List<String> = emptyList(),
)

internal class CalendarProjectionCache(
    private val dateTimeFormatter: DateTimeTextFormatter,
) {
    private var contextKey: Pair<String, Boolean>? = null
    private val sourceInputsByDay = mutableMapOf<Long, List<Task>>()
    private val projectionsByDay = mutableMapOf<Long, CalendarDayProjection>()
    private var timelineHourLabels: List<String>? = null
    private var expandedSource: Map<Long, List<Task>>? = null
    private var expandedWindow: CalendarVisibleRange? = null
    private var expandedInputs: Map<Long, List<Task>> = emptyMap()

    fun render(
        today: LocalDate,
        dayInputs: Map<Long, List<Task>>,
        formattingContextKey: String,
        viewPreference: CalendarViewPreference,
        listDisplayModePreference: CalendarListDisplayModePreference,
        listSelectedDayKey: Long?,
        monthSelectedDayKey: Long?,
        agendaDayKeys: List<Long> = emptyList(),
        visibleRange: CalendarVisibleRange? = null,
    ): CalendarRenderState {
        val projectionContext = formattingContextKey to (visibleRange != null)
        if (contextKey != projectionContext) {
            val formattingChanged = contextKey?.first != formattingContextKey
            contextKey = projectionContext
            sourceInputsByDay.clear()
            projectionsByDay.clear()
            if (formattingChanged) timelineHourLabels = null
        }

        val todayDayKey = dayKeyFromDate(today.year, today.monthNumber, today.dayOfMonth)
        val selectedDayKey = when (viewPreference) {
            CalendarViewPreference.LIST -> listSelectedDayKey ?: todayDayKey
            CalendarViewPreference.MONTH -> monthSelectedDayKey ?: todayDayKey
            else -> null
        }
        val taskDayKeys = if (visibleRange != null && viewPreference == CalendarViewPreference.YEAR) {
            calendarVisibleTaskDayKeys(dayInputs, visibleRange)
        } else dayInputs.keys.toSet()

        if (viewPreference == CalendarViewPreference.YEAR) {
            if (visibleRange == null) removeChangedAndMissingEntries(dayInputs) else removeChangedAndMissingTasks(dayInputs)
            return CalendarRenderState(
                viewPreference = viewPreference,
                listDisplayModePreference = listDisplayModePreference,
                today = today,
                taskDayKeys = taskDayKeys,
            )
        }

        val visibleInputs = if (visibleRange == null) dayInputs else {
            if (expandedSource !== dayInputs || expandedWindow != visibleRange) {
                expandedInputs = expandCalendarVisibleInputs(dayInputs, visibleRange)
                expandedSource = dayInputs
                expandedWindow = visibleRange
            }
            expandedInputs
        }
        val requiredInputs = LinkedHashMap(visibleInputs)
        agendaDayKeys.forEach { day -> if (day !in requiredInputs) requiredInputs[day] = emptyList() }
        if (selectedDayKey != null && selectedDayKey !in requiredInputs) {
            requiredInputs[selectedDayKey] = emptyList()
        }
        val nextProjections = requiredInputs.mapValues { (day, tasks) ->
            val previous = projectionsByDay[day]
            if (previous != null && sourceInputsByDay[day] == tasks) {
                val isToday = day == todayDayKey
                if (previous.isToday == isToday) previous else previous.copy(isToday = isToday)
            } else {
                projectCalendarDay(
                    tasks = tasks,
                    targetDayKey = day,
                    todayDayKey = todayDayKey,
                    dateTimeFormatter = dateTimeFormatter,
                    clipToDay = visibleRange != null,
                )
            }
        }
        sourceInputsByDay.keys.retainAll(requiredInputs.keys)
        projectionsByDay.keys.retainAll(requiredInputs.keys)
        sourceInputsByDay.putAll(requiredInputs)
        projectionsByDay.putAll(nextProjections)

        val labels = if (
            viewPreference == CalendarViewPreference.DAY ||
            viewPreference == CalendarViewPreference.THREE_DAY
        ) {
            timelineHourLabels ?: calendarTimelineHourLabels(dateTimeFormatter).also {
                timelineHourLabels = it
            }
        } else {
            emptyList()
        }
        return CalendarRenderState(
            viewPreference = viewPreference,
            listDisplayModePreference = listDisplayModePreference,
            today = today,
            taskDayKeys = visibleInputs.keys.toSet(),
            calendarDaysByDay = nextProjections,
            selectedDayProjection = selectedDayKey?.let(nextProjections::get)
                ?: EMPTY_CALENDAR_DAY_PROJECTION,
            timelineHourLabels = labels,
        )
    }

    private fun removeChangedAndMissingTasks(dayInputs: Map<Long, List<Task>>) {
        val currentTasks = dayInputs.values.flatten().associateBy { it.id }
        sourceInputsByDay.keys.toList().forEach { day ->
            if (sourceInputsByDay[day].orEmpty().any { currentTasks[it.id] != it }) {
                sourceInputsByDay.remove(day)
                projectionsByDay.remove(day)
            }
        }
    }

    private fun removeChangedAndMissingEntries(dayInputs: Map<Long, List<Task>>) {
        sourceInputsByDay.keys.toList().forEach { day ->
            if (dayInputs[day] != sourceInputsByDay[day]) {
                sourceInputsByDay.remove(day)
                projectionsByDay.remove(day)
            }
        }
    }
}

fun calendarTimelineHourLabels(
    dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
): List<String> = (0..23).map(dateTimeFormatter::formatHour)

/**
 * Returns tasks for a specific day, sorted by deadline.
 * Filters from the full task list by matching dayKey.
 */
fun tasksForDay(
    tasks: List<Task>,
    targetDayKey: Long,
): List<Task> = tasks.filter { task ->
    task.deadline?.let { dayKey(it) == targetDayKey } ?: false
}.sortedBy { task -> task.deadline }

/** Splits tasks into all-day and timed tasks using the shared calendar ordering. */
fun splitAllDayAndTimed(tasks: List<Task>): Pair<List<Task>, List<Task>> =
    sortCalendarTasksForDay(tasks).partition { task -> task.isCalendarAllDay() }

/**
 * Builds the immutable calendar projection consumed by all calendar views.
 * Formatting and fixed previews stay here so Compose only chooses layout.
 */
fun projectCalendarDay(
    tasks: List<Task>,
    targetDayKey: Long,
    todayDayKey: Long,
    dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
    clipToDay: Boolean = false,
): CalendarDayProjection {
    val rows = sortCalendarTasksForDay(tasks).map {
        it.toCalendarTaskRowProjection(dateTimeFormatter, targetDayKey, clipToDay)
    }
    val (allDayRows, unpositionedRows) = rows.partition { it.isAllDay }
    val positionedById = assignCalendarOverlapLanes(unpositionedRows).associateBy { it.task.id }
    val timedRows = unpositionedRows.map { positionedById[it.task.id] ?: it }
    val positionedRows = rows.map { positionedById[it.task.id] ?: it }
    return CalendarDayProjection(
        dayKey = targetDayKey,
        formattedDate = dateTimeFormatter.formatDateLabel(dayKeyToMillis(targetDayKey)),
        monthDateText = dateTimeFormatter.formatShortDate(dayKeyToMillis(targetDayKey)).uppercase(),
        isToday = targetDayKey == todayDayKey,
        rows = positionedRows,
        allDayRows = allDayRows,
        timedRows = timedRows,
        allDayPreview = calendarTaskPrefix(allDayRows, maxVisible = 3),
        monthPreview = calendarTaskPrefix(positionedRows, maxVisible = 5),
    )
}

/** Compatibility transform retained for non-calendar projection callers. */
fun splitCalendarDayTasks(
    tasks: List<Task>,
    dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
): CalendarDayTasks {
    val (allDayTasks, timedTasks) = splitAllDayAndTimed(tasks)
    return CalendarDayTasks(
        allDayTasks = allDayTasks,
        timedTasks = timedTasks,
        timedTaskStartMinutes = timedTasks.associate { task ->
            val deadline = task.deadline
            task.id to if (deadline == null) 0 else extractHour(deadline) * 60 + extractMinute(deadline)
        },
        timedTaskTimeText = timedTasks.mapNotNull { task ->
            val deadline = task.deadline ?: return@mapNotNull null
            val hour = extractHour(deadline)
            val minute = extractMinute(deadline)
            task.id.takeIf { hour != 0 || minute != 0 }
                ?.let { it to dateTimeFormatter.formatTime(deadline) }
        }.toMap(),
    )
}

/**
 * Returns the visible prefix and overflow count for a projected row list.
 * If there is only one extra row, keep it visible instead of showing "+1".
 */
fun calendarTaskPrefix(
    rows: List<CalendarTaskRowProjection>,
    maxVisible: Int,
): CalendarTaskPrefix {
    val safeLimit = maxVisible.coerceAtLeast(0)
    val visible = if (rows.size <= safeLimit + 1) rows else rows.take(safeLimit)
    return CalendarTaskPrefix(rows = visible, overflowCount = rows.size - visible.size)
}

private fun Task.toCalendarTaskRowProjection(
    dateTimeFormatter: DateTimeTextFormatter,
    targetDayKey: Long,
    clipToDay: Boolean,
): CalendarTaskRowProjection {
    val deadline = deadline
    val isAllDay = isCalendarAllDay()
    val dayStart = dayKeyToMillis(targetDayKey)
    val segmentStart = if (clipToDay) deadline?.coerceAtLeast(dayStart) else deadline
    val layoutStartMinutes = segmentStart?.let { ((it - dayStart) / MILLIS_PER_MINUTE).toInt().coerceIn(0, 1439) } ?: 0
    val endMinutes = endDeadline?.let { ((it - dayStart) / MILLIS_PER_MINUTE).toInt().coerceIn(layoutStartMinutes + 1, 1440) }
        ?: (layoutStartMinutes + 30).coerceAtMost(1440)
    return CalendarTaskRowProjection(
        task = this,
        isAllDay = isAllDay,
        startMinutes = deadline?.let { extractHour(it) * 60 + extractMinute(it) } ?: 0,
        layoutStartMinutes = layoutStartMinutes,
        endMinutes = endMinutes,
        timelineTimeText = segmentStart?.takeUnless { isAllDay }?.let(dateTimeFormatter::formatTime),
        cardDateText = segmentStart?.let(dateTimeFormatter::formatShortDate).orEmpty(),
        cardTimeText = segmentStart?.takeUnless { clipToDay && isAllDay }?.let(dateTimeFormatter::formatTime).orEmpty(),
    )
}

private val calendarDayComparator: Comparator<Task> = Comparator { first, second ->
    compareValuesBy(
        first,
        second,
        { if (it.isCalendarAllDay()) 0 else 1 },
        { it.deadline ?: Long.MAX_VALUE },
        { if (it.isCountdownItem) 1 else 0 },
    ).takeIf { it != 0 }
        ?: first.title.compareTo(second.title, ignoreCase = true).takeIf { it != 0 }
        ?: first.id.compareTo(second.id)
}

fun sortCalendarTasksForDay(tasks: List<Task>): List<Task> = tasks.sortedWith(calendarDayComparator)

private fun Task.isCalendarAllDay(): Boolean =
    isAllDay || (deadline != null && extractHour(deadline) == 0 && extractMinute(deadline) == 0)


/** Touching intervals do not overlap; connected interval groups share their maximum lane count. */
internal fun assignCalendarOverlapLanes(rows: List<CalendarTaskRowProjection>): List<CalendarTaskRowProjection> {
    val ordered = rows.sortedWith(compareBy({ it.layoutStartMinutes }, { it.endMinutes }, { it.task.id }))
    val result = mutableListOf<CalendarTaskRowProjection>()
    var group = mutableListOf<CalendarTaskRowProjection>()
    val laneEnds = mutableListOf<Int>()
    var groupEnd = -1
    fun flush() {
        val count = laneEnds.size.coerceAtLeast(1)
        result.addAll(group.map { it.copy(laneCount = count) })
        group = mutableListOf()
        laneEnds.clear()
    }
    ordered.forEach { row ->
        if (group.isNotEmpty() && row.layoutStartMinutes >= groupEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= row.layoutStartMinutes }
        if (lane < 0) { lane = laneEnds.size; laneEnds.add(row.endMinutes) } else laneEnds[lane] = row.endMinutes
        group.add(row.copy(laneIndex = lane))
        groupEnd = if (group.size == 1) row.endMinutes else maxOf(groupEnd, row.endMinutes)
    }
    flush()
    return result
}
