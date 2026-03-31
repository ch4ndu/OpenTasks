package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.extractHour
import com.udnahc.opentasks.data.extensions.extractMinute
import com.udnahc.opentasks.data.model.Task

/**
 * Returns tasks for a specific day, sorted by deadline.
 * Filters from the full task list by matching dayKey.
 */
fun tasksForDay(tasks: List<Task>, targetDayKey: Long): List<Task> =
    tasks.filter { task -> task.deadline?.let { dayKey(it) == targetDayKey } ?: false }
        .sortedBy { it.deadline }

/**
 * Splits tasks into all-day (midnight: hour=0, minute=0) and timed tasks.
 * Returns (allDayTasks, timedTasks).
 */
fun splitAllDayAndTimed(tasks: List<Task>): Pair<List<Task>, List<Task>> =
    tasks.partition { task ->
        task.isAllDay || (task.deadline != null && extractHour(task.deadline) == 0 && extractMinute(task.deadline) == 0)
    }

/**
 * Truncates a task list for display, returning the visible portion and overflow count.
 * If the list has at most maxVisible+1 items, shows all (avoids "+1" overflow).
 * Returns (visibleTasks, overflowCount).
 */
fun truncateWithOverflow(tasks: List<Task>, maxVisible: Int): Pair<List<Task>, Int> {
    val visible = if (tasks.size <= maxVisible + 1) tasks else tasks.take(maxVisible)
    return visible to (tasks.size - visible.size)
}
