package com.udnahc.opentasks.domain.usecase.settings

import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveUiExperienceUseCase(private val settings: AppSettingsRepository) {
    operator fun invoke(): Flow<UiExperience> = settings.observeValue(KEY)
        .map { UiExperience.fromString(it) }
        .flowOn(Dispatchers.Default)

    companion object { const val KEY = "ui_experience" }
}
