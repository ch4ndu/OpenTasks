package com.udnahc.opentasks.data.model

sealed interface TaskListFilter {
    data class Category(val id: String) : TaskListFilter
    data object Starred : TaskListFilter
    data object Today : TaskListFilter
    data object Overdue : TaskListFilter
    data object NoDate : TaskListFilter
    data object HighPriority : TaskListFilter
    data object DueThisWeek : TaskListFilter
}
