package com.udnahc.opentasks.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen : NavClass() {
    @Serializable data object Matrix : Screen()
    @Serializable data object TaskList : Screen()
    @Serializable data object Calendar : Screen()
    @Serializable data object Notes : Screen()
    @Serializable data class QuadrantDetail(val priorityOrdinal: Int) : Screen()
    @Serializable data class CreateTask(
        val priorityOrdinal: Int = 0,
        val categoryId: Long = 1L,
        val day: Int = 0,
        val month: Int = 0,
        val year: Int = 0,
    ) : Screen()
    @Serializable data class EditTask(val taskId: Long) : Screen()
}
