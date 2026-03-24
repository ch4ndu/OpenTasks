package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveThemePreferenceUseCase(private val appSettingsDao: AppSettingsDao) {
    operator fun invoke(): Flow<ThemeMode> =
        appSettingsDao.observeValue(KEY_THEME_PREFERENCE)
            .map { ThemeMode.fromString(it) }

    companion object {
        const val KEY_THEME_PREFERENCE = "theme_preference"
    }
}
