package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.domain.usecase.settings.ObserveUiExperienceUseCase

class SaveUiExperienceAction(private val settings: AppSettingsRepository) {
    suspend operator fun invoke(experience: UiExperience) {
        settings.setValue(ObserveUiExperienceUseCase.KEY, experience.name)
    }
}
