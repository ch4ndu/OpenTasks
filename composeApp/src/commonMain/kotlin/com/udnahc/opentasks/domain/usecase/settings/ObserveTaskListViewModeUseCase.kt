package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.AppConstants.SettingsKeys
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveTaskListViewModeUseCase(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke(): Flow<TaskListViewMode> =
        repository.observeValue(SettingsKeys.TASK_LIST_VIEW_MODE)
            .map { value ->
                TaskListViewMode.entries.firstOrNull { it.name == value } ?: TaskListViewMode.LIST
            }
            .flowOn(Dispatchers.Default)
}
