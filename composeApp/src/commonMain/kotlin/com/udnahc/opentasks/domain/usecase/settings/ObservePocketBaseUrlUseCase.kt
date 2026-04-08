package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

class ObservePocketBaseUrlUseCase(private val appSettingsRepository: AppSettingsRepository) {
    operator fun invoke(): Flow<String?> =
        appSettingsRepository.observeValue(KEY_POCKETBASE_URL)

    companion object {
        const val KEY_POCKETBASE_URL = "pocketbase_url"
    }
}
