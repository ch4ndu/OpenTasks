package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.AppConstants.SettingsKeys
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveTaskSortOptionUseCase(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke(): Flow<TaskSortOption> = repository.observeValue(SettingsKeys.TASK_LIST_SORT_OPTION)
        .map { value ->
            TaskSortOption.entries.firstOrNull { it.name == value } ?: TaskSortOption.RECENTLY_UPDATED
        }
        .flowOn(Dispatchers.Default)
}
