package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveCalendarViewPreferenceUseCase(private val appSettingsRepository: AppSettingsRepository) {
    operator fun invoke(): Flow<CalendarViewPreference> =
        appSettingsRepository.observeValue(KEY_CALENDAR_VIEW_PREFERENCE)
            .map { CalendarViewPreference.fromString(it) }
            .flowOn(Dispatchers.Default)

    companion object {
        const val KEY_CALENDAR_VIEW_PREFERENCE = "calendar_view_preference"
    }
}
