package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase.Companion.KEY_TEXT_SIZE_PREFERENCE

class SaveTextSizePreferenceAction(private val appSettingsRepository: AppSettingsRepository) {
    suspend operator fun invoke(preference: TextSizePreference) {
        appSettingsRepository.setValue(KEY_TEXT_SIZE_PREFERENCE, preference.name)
    }
}
