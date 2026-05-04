package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveCalendarListDisplayModePreferenceUseCase(private val appSettingsRepository: AppSettingsRepository) {
    operator fun invoke(): Flow<CalendarListDisplayModePreference> =
        appSettingsRepository.observeValue(KEY_CALENDAR_LIST_DISPLAY_MODE_PREFERENCE)
            .map { CalendarListDisplayModePreference.fromString(it) }
            .flowOn(Dispatchers.Default)

    companion object {
        const val KEY_CALENDAR_LIST_DISPLAY_MODE_PREFERENCE =
            "calendar_list_display_mode_preference"
    }
}
