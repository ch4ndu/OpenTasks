package com.udnahc.opentasks.data.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface TaskListFilter {
    @Serializable
    data class Category(val id: String) : TaskListFilter
    @Serializable
    data object Starred : TaskListFilter
    @Serializable
    data object Today : TaskListFilter
    @Serializable
    data object Overdue : TaskListFilter
    @Serializable
    data object NoDate : TaskListFilter
    @Serializable
    data object HighPriority : TaskListFilter
    @Serializable
    data object DueThisWeek : TaskListFilter
}
