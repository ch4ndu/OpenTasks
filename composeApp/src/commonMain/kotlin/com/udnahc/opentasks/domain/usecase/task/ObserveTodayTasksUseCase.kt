package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

data class TodayTasks(
    val overdue: List<Task>,
    val today: List<Task>,
    val completedToday: List<Task>,
)

class ObserveTodayTasksUseCase(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<TodayTasks> = repository.getAllTasks()
        .map { tasks ->
            val today = todayLocal()
            val startOfToday =
                startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            val startOfTomorrow =
                startOfDayLocalMillis(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth)

            val active = tasks.filter { it.status != TaskStatus.DONE }
            TodayTasks(
                overdue = active.filter { it.deadline != null && it.deadline < startOfToday }
                    .sortedBy { it.deadline },
                today = active.filter { it.deadline != null && it.deadline >= startOfToday && it.deadline < startOfTomorrow }
                    .sortedBy { it.deadline },
                completedToday = tasks.filter { it.status == TaskStatus.DONE && it.updatedAt >= startOfToday && it.updatedAt < startOfTomorrow }
                    .sortedByDescending { it.updatedAt },
            )
        }
        .flowOn(Dispatchers.Default)
}
