package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase.Companion.KEY_THEME_PREFERENCE

class SaveThemePreferenceAction(private val appSettingsRepository: AppSettingsRepository) {
    suspend operator fun invoke(mode: ThemeMode) {
        appSettingsRepository.setValue(KEY_THEME_PREFERENCE, mode.name)
    }
}
