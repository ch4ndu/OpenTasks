package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.dao.AppSettingsDao
import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase.Companion.KEY_THEME_PREFERENCE

class SaveThemePreferenceAction(private val appSettingsDao: AppSettingsDao) {
    suspend operator fun invoke(mode: ThemeMode) {
        appSettingsDao.setValue(AppSettings(KEY_THEME_PREFERENCE, mode.name))
    }
}
