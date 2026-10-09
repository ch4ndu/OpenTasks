package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.TaskCompletion
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.usecase.task.ObserveCompletionsUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

enum class LogbookPeriod { WEEK, MONTH, ALL }
data class LogbookRow(val entry: TaskCompletion, val dateText: String, val taskAvailable: Boolean)
data class LogbookCategory(val id: String, val name: String)
data class LogbookState(
    val period: LogbookPeriod = LogbookPeriod.WEEK,
    val categoryId: String? = null,
    val categoryName: String = "",
    val categories: List<LogbookCategory> = emptyList(),
    val rows: List<LogbookRow> = emptyList(),
)

class LogbookViewModel(
    completions: ObserveCompletionsUseCase,
    tasks: ObserveAllTasksUseCase,
    day: LocalDaySignal,
    formatter: DateTimeTextFormatter,
) : ViewModel() {
    private val period = MutableStateFlow(LogbookPeriod.WEEK)
    private val categoryId = MutableStateFlow<String?>(null)
    val state = combine(completions(), tasks(), day.dates, period, categoryId) { history, active, today, selectedPeriod, selectedCategory ->
        val first = today.minus(if (selectedPeriod == LogbookPeriod.WEEK) 6 else 29, DateTimeUnit.DAY)
        val from = if (selectedPeriod == LogbookPeriod.ALL) Long.MIN_VALUE else startOfDayLocalMillis(first.year, first.monthNumber, first.dayOfMonth)
        val ids = active.mapTo(mutableSetOf()) { it.id }
        val categories = history.distinctBy { it.categoryId }.map { LogbookCategory(it.categoryId, it.categoryName) }.sortedBy { it.name }
        LogbookState(selectedPeriod, selectedCategory, categories.firstOrNull { it.id == selectedCategory }?.name.orEmpty(), categories,
            history.filter { it.completedAt >= from && (selectedCategory == null || it.categoryId == selectedCategory) }
                .sortedWith(compareByDescending<TaskCompletion> { it.completedAt }.thenBy { it.id })
                .map { LogbookRow(it, formatter.formatDateWithYear(it.completedAt) + " · " + formatter.formatTime(it.completedAt), it.taskId in ids) })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogbookState())
    fun setPeriod(value: LogbookPeriod) { period.value = value }
    fun setCategory(value: String?) { categoryId.value = value }
}
