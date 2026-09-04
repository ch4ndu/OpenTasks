package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppearanceViewModel(
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeTextSizePreference: ObserveTextSizePreferenceUseCase,
) : ViewModel() {

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val textSizePreference: StateFlow<TextSizePreference> = observeTextSizePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSizePreference.SMALL)
}
