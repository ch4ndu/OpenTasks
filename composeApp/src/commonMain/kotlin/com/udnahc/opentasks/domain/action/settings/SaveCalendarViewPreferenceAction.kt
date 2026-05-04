package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase.Companion.KEY_CALENDAR_VIEW_PREFERENCE

class SaveCalendarViewPreferenceAction(private val appSettingsRepository: AppSettingsRepository) {
    suspend operator fun invoke(preference: CalendarViewPreference) {
        appSettingsRepository.setValue(KEY_CALENDAR_VIEW_PREFERENCE, preference.name)
    }
}
