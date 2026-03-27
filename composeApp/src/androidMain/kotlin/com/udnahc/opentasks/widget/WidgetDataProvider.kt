package com.udnahc.opentasks.widget

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.localMillisToUtcMillis
import com.udnahc.opentasks.data.extensions.nowUtcMillis
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.extensions.utcMillisToLocalMillis
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class WidgetTask(
    val id: String,
    val title: String,
    val dateLabel: String?,
    val isOverdue: Boolean,
)

data class CalendarDayTask(
    val title: String,
    val priority: TaskPriority,
)

class WidgetDataProvider : KoinComponent {
    private val taskDao: TaskDao by inject()
    private val categoryDao: CategoryDao by inject()

    suspend fun getCategories(): List<Category> = categoryDao.getAllCategoriesOnce()

    suspend fun getWidgetTasks(prefs: WidgetPreferences): List<WidgetTask> {
        val tasks = fetchTasks(prefs)
        val sorted = sortTasks(tasks, prefs.sortBy)
        return sorted.take(15).map { it.toWidgetTask() }
    }

    private suspend fun fetchTasks(prefs: WidgetPreferences): List<Task> {
        return when (prefs.filterType) {
            WidgetFilterType.ALL -> taskDao.getActiveTasksOnce()
            WidgetFilterType.TODAY -> getTasksForDayRange(0, 1)
            WidgetFilterType.TOMORROW -> getTasksForDayRange(1, 2)
            WidgetFilterType.NEXT_7_DAYS -> getTasksForDayRange(0, 7)
            WidgetFilterType.CATEGORY -> {
                val catId = prefs.filterCategoryId ?: return taskDao.getActiveTasksOnce()
                taskDao.getActiveTasksOnce().filter { it.categoryId == catId }
            }
        }
    }

    private suspend fun getTasksForDayRange(
        startDaysFromToday: Int,
        endDaysFromToday: Int,
    ): List<Task> {
        val today = todayLocal()
        val startDate = today.plus(startDaysFromToday, DateTimeUnit.DAY)
        val endDate = today.plus(endDaysFromToday, DateTimeUnit.DAY)
        val startUtc = localMillisToUtcMillis(
            startOfDayLocalMillis(startDate.year, startDate.monthNumber, startDate.dayOfMonth)
        )
        val endUtc = localMillisToUtcMillis(
            startOfDayLocalMillis(endDate.year, endDate.monthNumber, endDate.dayOfMonth)
        )
        return taskDao.getTasksInDateRange(startUtc, endUtc)
    }

    private fun sortTasks(tasks: List<Task>, sortBy: WidgetSortBy): List<Task> {
        return when (sortBy) {
            WidgetSortBy.DATE -> tasks.sortedWith(compareBy(nullsLast()) { it.deadline })
            WidgetSortBy.PRIORITY -> tasks.sortedBy { it.priority.ordinal }
            WidgetSortBy.NAME -> tasks.sortedBy { it.title.lowercase() }
        }
    }

    suspend fun getTasksByDayForMonth(
        year: Int,
        month: Int,
        maxPerDay: Int = MAX_TASKS_PER_DAY,
    ): Map<Int, List<CalendarDayTask>> {
        val startLocalMillis = startOfDayLocalMillis(year, month, 1)
        val endDate = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
        val endLocalMillis = startOfDayLocalMillis(endDate.year, endDate.monthNumber, endDate.dayOfMonth)
        val startUtc = localMillisToUtcMillis(startLocalMillis)
        val endUtc = localMillisToUtcMillis(endLocalMillis)
        val tasks = taskDao.getTasksInDateRange(startUtc, endUtc)
        val result = mutableMapOf<Int, MutableList<CalendarDayTask>>()
        for (task in tasks) {
            val deadline = task.deadline ?: continue
            val localMillis = utcMillisToLocalMillis(deadline)
            val day = extractDay(localMillis)
            val list = result.getOrPut(day) { mutableListOf() }
            if (list.size < maxPerDay) {
                list.add(CalendarDayTask(title = task.title, priority = task.priority))
            }
        }
        return result
    }

    companion object {
        const val MAX_TASKS_PER_DAY = 2
    }

    private fun Task.toWidgetTask(): WidgetTask {
        val today = todayLocal()
        val deadlineLocal = deadline?.let { utcMillisToLocalMillis(it) }
        val dateLabel = if (deadlineLocal != null) {
            val deadlineDate = localMillisToLocalDate(deadlineLocal)
            when {
                deadlineDate == today -> "Today"
                deadlineDate == today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
                else -> formatDateShort(deadlineLocal)
            }
        } else {
            null
        }
        val isOverdue = deadline != null && deadline < nowUtcMillis()
        return WidgetTask(id = id, title = title, dateLabel = dateLabel, isOverdue = isOverdue)
    }
}
