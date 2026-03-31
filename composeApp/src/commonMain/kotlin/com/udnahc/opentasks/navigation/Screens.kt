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
        val categoryId: String = "00000000-0000-0000-0000-000000000001",
        val day: Int = 0,
        val month: Int = 0,
        val year: Int = 0,
        val title: String = "",
    ) : Screen()
    @Serializable data class EditTask(val taskId: String) : Screen()
    @Serializable data object Settings : Screen()
    @Serializable data object Countdown : Screen()
    @Serializable data class CreateCountdown(val typeOrdinal: Int = 3) : Screen()
    @Serializable data class CountdownDetail(val countdownId: String) : Screen()
    @Serializable data class EditCountdown(val countdownId: String) : Screen()
}
