package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayKeyToMillis
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.formatDateLabel
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.formatTime12Hr
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem

data class CalendarDayTasks(
    val allDayTasks: List<Task> = emptyList(),
    val timedTasks: List<Task> = emptyList(),
    val timedTaskStartMinutes: Map<String, Int> = emptyMap(),
    val timedTaskTimeText: Map<String, String> = emptyMap(),
)

private val CALENDAR_TIMELINE_HOUR_LABELS = (0..23).map { hour ->
    formatTime12Hr(hour, 0).replace(":00 ", " ")
}

data class CalendarTaskRowProjection(
    val task: Task,
    val isAllDay: Boolean,
    val startMinutes: Int,
    val timelineTimeText: String?,
    val cardDateText: String,
    val cardTimeText: String,
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
    val timelineHourLabels: List<String> = CALENDAR_TIMELINE_HOUR_LABELS,
)

internal val EMPTY_CALENDAR_DAY_PROJECTION = CalendarDayProjection()

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
): CalendarDayProjection {
    val rows = sortCalendarTasksForDay(tasks).map { it.toCalendarTaskRowProjection() }
    val (allDayRows, timedRows) = rows.partition { it.isAllDay }
    return CalendarDayProjection(
        dayKey = targetDayKey,
        formattedDate = formatDateLabel(dayKeyToMillis(targetDayKey)),
        monthDateText = formatDateShort(dayKeyToMillis(targetDayKey)).uppercase(),
        isToday = targetDayKey == todayDayKey,
        rows = rows,
        allDayRows = allDayRows,
        timedRows = timedRows,
        allDayPreview = calendarTaskPrefix(allDayRows, maxVisible = 3),
        monthPreview = calendarTaskPrefix(rows, maxVisible = 5),
        timelineHourLabels = CALENDAR_TIMELINE_HOUR_LABELS,
    )
}

/** Compatibility transform retained for non-calendar projection callers. */
fun splitCalendarDayTasks(tasks: List<Task>): CalendarDayTasks {
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
            task.id.takeIf { hour != 0 || minute != 0 }?.let { it to formatTimeFromLocalMillis(deadline) }
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

private fun Task.toCalendarTaskRowProjection(): CalendarTaskRowProjection {
    val deadline = deadline
    val isAllDay = isCalendarAllDay()
    val startMinutes = deadline?.let { extractHour(it) * 60 + extractMinute(it) } ?: 0
    return CalendarTaskRowProjection(
        task = this,
        isAllDay = isAllDay,
        startMinutes = startMinutes,
        timelineTimeText = deadline?.takeUnless { isAllDay }?.let(::formatTimeFromLocalMillis),
        cardDateText = deadline?.let(::formatDateShort).orEmpty(),
        cardTimeText = deadline?.let(::formatTimeFromLocalMillis).orEmpty(),
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
