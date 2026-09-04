package com.udnahc.opentasks.widget

import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.dao.CountdownDao
import com.udnahc.opentasks.data.dao.TaskDao
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.localMillisToUtcMillis
import com.udnahc.opentasks.data.extensions.nowUtcMillis
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.extensions.utcMillisToLocalMillis
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownOccurrence
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.today
import opentasks.composeapp.generated.resources.widget_filter_tomorrow
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.WidgetAccountGate

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
    private val countdownDao: CountdownDao by inject()
    private val widgetAccountGate: WidgetAccountGate by inject()

    suspend fun activeBoundary(): AccountBoundary =
        widgetAccountGate.currentBoundary()
            ?: throw IllegalStateException("Widget data requires an active account boundary")

    suspend fun <T> withActiveCacheBoundary(
        block: suspend (AccountBoundary) -> T,
    ): T? = widgetAccountGate.withActiveCacheBoundary(block)

    suspend fun getCategories(): List<Category> =
        widgetAccountGate.withActiveCacheBoundary {
            getCategoriesWithinBoundary()
        }.orEmpty()

    suspend fun getWidgetTasks(
        prefs: WidgetPreferences,
        dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
    ): List<WidgetTask> =
        widgetAccountGate.withActiveCacheBoundary {
            getWidgetTasksWithinBoundary(prefs, dateTimeFormatter)
        }.orEmpty()

    internal suspend fun getCategoriesWithinBoundary(): List<Category> =
        categoryDao.getAllCategoriesOnce().filter { !it.isDeleted }

    internal suspend fun getWidgetTasksWithinBoundary(
        prefs: WidgetPreferences,
        dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
    ): List<WidgetTask> {
        val tasks = fetchTasks(prefs)
        val sorted = sortTasks(tasks, prefs.sortBy)
        val todayLabel = getString(Res.string.today)
        val tomorrowLabel = getString(Res.string.widget_filter_tomorrow)
        return sorted.take(15).map {
            it.toWidgetTask(todayLabel, tomorrowLabel, dateTimeFormatter)
        }
    }

    private suspend fun fetchTasks(prefs: WidgetPreferences): List<Task> {
        return when (prefs.filterType) {
            WidgetFilterType.ALL -> taskDao.getIncompleteTasksOnce()
            WidgetFilterType.TODAY -> getTasksForDayRange(0, 1)
            WidgetFilterType.TOMORROW -> getTasksForDayRange(1, 2)
            WidgetFilterType.NEXT_7_DAYS -> getTasksForDayRange(0, 7)
            WidgetFilterType.CATEGORY -> {
                val catId = prefs.filterCategoryId ?: return taskDao.getIncompleteTasksOnce()
                if (getCategoriesWithinBoundary().none { it.id == catId }) {
                    return taskDao.getIncompleteTasksOnce()
                }
                taskDao.getIncompleteTasksOnce().filter { it.categoryId == catId }
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
            WidgetSortBy.NAME -> tasks.sortedWith { first, second ->
                first.title.compareTo(second.title, ignoreCase = true)
            }
        }
    }

    suspend fun getTasksByDayForMonth(
        year: Int,
        month: Int,
        maxPerDay: Int = MAX_TASKS_PER_DAY,
    ): Map<Int, List<CalendarDayTask>> =
        widgetAccountGate.withActiveCacheBoundary {
            getTasksByDayForMonthWithinBoundary(year, month, maxPerDay)
        }.orEmpty()

    internal suspend fun getTasksByDayForMonthWithinBoundary(
        year: Int,
        month: Int,
        maxPerDay: Int,
    ): Map<Int, List<CalendarDayTask>> {
        val startLocalMillis = startOfDayLocalMillis(year, month, 1)
        val endDate = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
        val endLocalMillis = startOfDayLocalMillis(endDate.year, endDate.monthNumber, endDate.dayOfMonth)
        val startUtc = localMillisToUtcMillis(startLocalMillis)
        val endUtc = localMillisToUtcMillis(endLocalMillis)
        val tasks = taskDao.getTasksInDateRangeIncludingCompleted(startUtc, endUtc)
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
        // Merge countdown items into the same map
        val countdowns = countdownDao.getAllCountdownsOnce().filter { !it.isDeleted }
        for (countdown in countdowns) {
            val localMillis = effectiveCountdownTargetLocalMillis(countdown, todayLocal())
            if (localMillis < startLocalMillis || localMillis >= endLocalMillis) continue
            val day = extractDay(localMillis)
            val list = result.getOrPut(day) { mutableListOf() }
            if (list.size < maxPerDay) {
                list.add(CalendarDayTask(title = countdown.title, priority = countdownTypeToPriority(countdown.countdownType)))
            }
        }
        return result
    }

    suspend fun getTasksByDayForWeek(
        weekStartLocalMillis: Long,
        maxPerDay: Int = MAX_TASKS_PER_WEEK_DAY,
    ): Map<Int, List<CalendarDayTask>> =
        widgetAccountGate.withActiveCacheBoundary {
            getTasksByDayForWeekWithinBoundary(weekStartLocalMillis, maxPerDay)
        }.orEmpty()

    internal suspend fun getTasksByDayForWeekWithinBoundary(
        weekStartLocalMillis: Long,
        maxPerDay: Int,
    ): Map<Int, List<CalendarDayTask>> {
        val endLocalMillis = weekStartLocalMillis + 7 * MILLIS_PER_DAY
        val startUtc = localMillisToUtcMillis(weekStartLocalMillis)
        val endUtc = localMillisToUtcMillis(endLocalMillis)
        val tasks = taskDao.getTasksInDateRangeIncludingCompleted(startUtc, endUtc)
        val result = mutableMapOf<Int, MutableList<CalendarDayTask>>()
        for (task in tasks) {
            val deadline = task.deadline ?: continue
            val localMillis = utcMillisToLocalMillis(deadline)
            // Key by day-of-week index (0=Sun..6=Sat) relative to week start
            val dayIndex = ((localMillis - weekStartLocalMillis) / MILLIS_PER_DAY).toInt()
            if (dayIndex !in 0..6) continue
            val list = result.getOrPut(dayIndex) { mutableListOf() }
            if (list.size < maxPerDay) {
                list.add(CalendarDayTask(title = task.title, priority = task.priority))
            }
        }
        // Merge countdown items into the same map
        val countdowns = countdownDao.getAllCountdownsOnce().filter { !it.isDeleted }
        for (countdown in countdowns) {
            val localMillis = effectiveCountdownTargetLocalMillis(countdown, todayLocal())
            val dayIndex = ((localMillis - weekStartLocalMillis) / MILLIS_PER_DAY).toInt()
            if (dayIndex !in 0..6) continue
            val list = result.getOrPut(dayIndex) { mutableListOf() }
            if (list.size < maxPerDay) {
                list.add(CalendarDayTask(title = countdown.title, priority = countdownTypeToPriority(countdown.countdownType)))
            }
        }
        return result
    }

    private fun countdownTypeToPriority(type: CountdownType): TaskPriority = when (type) {
        CountdownType.HOLIDAY -> TaskPriority.NONE        // green
        CountdownType.BIRTHDAY -> TaskPriority.HIGH       // red
        CountdownType.ANNIVERSARY -> TaskPriority.LOW     // blue
        CountdownType.COUNTDOWN -> TaskPriority.MEDIUM    // amber
    }


    companion object {
        const val MAX_TASKS_PER_DAY = 2
        const val MAX_TASKS_PER_WEEK_DAY = 1
    }

    private fun Task.toWidgetTask(
        todayLabel: String,
        tomorrowLabel: String,
        dateTimeFormatter: DateTimeTextFormatter,
    ): WidgetTask {
        val today = todayLocal()
        val deadlineUtcMillis = deadline
        val deadlineLocal = deadlineUtcMillis?.let { utcMillisToLocalMillis(it) }
        val dateLabel = if (deadlineLocal != null) {
            val deadlineDate = localMillisToLocalDate(deadlineLocal)
            when {
                deadlineDate == today -> todayLabel
                deadlineDate == today.plus(1, DateTimeUnit.DAY) -> tomorrowLabel
                else -> dateTimeFormatter.formatShortDate(deadlineLocal)
            }
        } else {
            null
        }
        val isOverdue = deadlineUtcMillis != null && deadlineUtcMillis < nowUtcMillis()
        return WidgetTask(id = id, title = title, dateLabel = dateLabel, isOverdue = isOverdue)
    }
}

/** Widget queries receive raw UTC Room rows, while occurrence projection operates on local civil millis. */
internal fun effectiveCountdownTargetLocalMillis(
    countdown: com.udnahc.opentasks.data.model.Countdown,
    today: LocalDate,
): Long = projectCountdownOccurrence(
    countdown.copy(targetDate = utcMillisToLocalMillis(countdown.targetDate)),
    today,
).effectiveTargetDate
