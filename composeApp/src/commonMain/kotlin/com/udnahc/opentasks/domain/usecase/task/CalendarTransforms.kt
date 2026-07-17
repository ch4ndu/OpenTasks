package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem

data class CalendarDayTasks(
    val allDayTasks: List<Task> = emptyList(),
    val timedTasks: List<Task> = emptyList(),
    val timedTaskStartMinutes: Map<String, Int> = emptyMap(),
    val timedTaskTimeText: Map<String, String> = emptyMap(),
)

/**
 * Returns tasks for a specific day, sorted by deadline.
 * Filters from the full task list by matching dayKey.
 */
fun tasksForDay(
    tasks: List<Task>,
    targetDayKey: Long
): List<Task> =
    tasks.filter { task -> task.deadline?.let { dayKey(it) == targetDayKey } ?: false }
        .sortedBy { it.deadline }

/**
 * Splits tasks into all-day (midnight: hour=0, minute=0) and timed tasks.
 * Returns (allDayTasks, timedTasks).
 */
fun splitAllDayAndTimed(tasks: List<Task>): Pair<List<Task>, List<Task>> =
    sortCalendarTasksForDay(tasks).partition { task -> task.isCalendarAllDay() }

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

fun sortCalendarTasksForDay(tasks: List<Task>): List<Task> =
    tasks.sortedWith(calendarDayComparator)

private fun Task.isCalendarAllDay(): Boolean =
    isAllDay || (deadline != null && extractHour(deadline) == 0 && extractMinute(deadline) == 0)

/**
 * Truncates a task list for display, returning the visible portion and overflow count.
 * If the list has at most maxVisible+1 items, shows all (avoids "+1" overflow).
 * Returns (visibleTasks, overflowCount).
 */
fun truncateWithOverflow(
    tasks: List<Task>,
    maxVisible: Int
): Pair<List<Task>, Int> {
    val visible = if (tasks.size <= maxVisible + 1) tasks else tasks.take(maxVisible)
    return visible to (tasks.size - visible.size)
}
