package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.AppConstants.SettingsKeys
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.repository.AppSettingsRepository

class SaveTaskListViewModeAction(
    private val repository: AppSettingsRepository,
) {
    suspend operator fun invoke(mode: TaskListViewMode) {
        repository.setValue(SettingsKeys.TASK_LIST_VIEW_MODE, mode.name)
    }
}
