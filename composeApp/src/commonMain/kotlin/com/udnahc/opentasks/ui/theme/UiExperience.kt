package com.udnahc.opentasks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.udnahc.opentasks.data.model.UiExperience

val LocalUiExperience = staticCompositionLocalOf { UiExperience.CLASSIC }

val isReimagined: Boolean
    @Composable get() = LocalUiExperience.current == UiExperience.REIMAGINED
