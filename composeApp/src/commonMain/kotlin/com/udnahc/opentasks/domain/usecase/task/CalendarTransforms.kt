package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem

data class CalendarDayTasks(
    val allDayTasks: List<Task> = emptyList(),
    val timedTasks: List<Task> = emptyList(),
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
    )
}

private val calendarDayComparator: Comparator<Task> = compareBy(
    { if (it.isCalendarAllDay()) 0 else 1 },
    { it.deadline ?: Long.MAX_VALUE },
    { if (it.isCountdownItem) 1 else 0 },
    { it.title.lowercase() },
    { it.id },
)

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
