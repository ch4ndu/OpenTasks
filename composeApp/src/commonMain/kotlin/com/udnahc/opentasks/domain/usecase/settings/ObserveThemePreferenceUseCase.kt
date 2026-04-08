package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveThemePreferenceUseCase(private val appSettingsRepository: AppSettingsRepository) {
    operator fun invoke(): Flow<ThemeMode> =
        appSettingsRepository.observeValue(KEY_THEME_PREFERENCE)
            .map { ThemeMode.fromString(it) }

    companion object {
        const val KEY_THEME_PREFERENCE = "theme_preference"
    }
}
