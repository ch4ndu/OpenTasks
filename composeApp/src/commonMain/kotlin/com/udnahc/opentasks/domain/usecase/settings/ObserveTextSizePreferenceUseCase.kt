package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveTextSizePreferenceUseCase(private val appSettingsRepository: AppSettingsRepository) {
    operator fun invoke(): Flow<TextSizePreference> =
        appSettingsRepository.observeValue(KEY_TEXT_SIZE_PREFERENCE)
            .map { TextSizePreference.fromString(it) }
            .flowOn(Dispatchers.Default)

    companion object {
        const val KEY_TEXT_SIZE_PREFERENCE = "text_size_preference"
    }
}
