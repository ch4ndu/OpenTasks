package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase.Companion.KEY_CALENDAR_LIST_DISPLAY_MODE_PREFERENCE

class SaveCalendarListDisplayModePreferenceAction(private val appSettingsRepository: AppSettingsRepository) {
    suspend operator fun invoke(preference: CalendarListDisplayModePreference) {
        appSettingsRepository.setValue(KEY_CALENDAR_LIST_DISPLAY_MODE_PREFERENCE, preference.name)
    }
}
