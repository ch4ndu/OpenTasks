package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.AppConstants.SettingsKeys
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.repository.AppSettingsRepository

class SaveTaskSortOptionAction(
    private val repository: AppSettingsRepository,
) {
    suspend operator fun invoke(option: TaskSortOption) {
        repository.setValue(SettingsKeys.TASK_LIST_SORT_OPTION, option.name)
    }
}
