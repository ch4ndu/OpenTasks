package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.domain.usecase.settings.ObserveUiExperienceUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppearanceViewModel(
    observeUiExperience: ObserveUiExperienceUseCase,
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeTextSizePreference: ObserveTextSizePreferenceUseCase,
) : ViewModel() {

    private var hasLoadedUiExperience = false

    val uiExperience: StateFlow<UiExperience?> = observeUiExperience()
        .onEach { hasLoadedUiExperience = true }
        .catch { error ->
            if (error is CancellationException) throw error
            if (!hasLoadedUiExperience) emit(UiExperience.CLASSIC)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val textSizePreference: StateFlow<TextSizePreference> = observeTextSizePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSizePreference.SMALL)
}
