package com.udnahc.opentasks.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnDarkBackground,
    secondary = AccentOrange,
    onSecondary = OnDarkBackground,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    error = PriorityHigh,
    onError = OnDarkBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = LightBackground,
    secondary = AccentOrange,
    onSecondary = LightBackground,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightSurface,
    onSurface = OnLightBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    error = PriorityHigh,
    onError = LightBackground,
)

@Composable
fun OpenTasksTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    uiExperience: UiExperience = UiExperience.CLASSIC,
    textSizePreference: TextSizePreference = TextSizePreference.SMALL,
    content: @Composable () -> Unit,
) {
    val inheritedContentColor = LocalContentColor.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    BoxWithConstraints {
        val sizeCategory = when {
            maxWidth < 600.dp -> WindowSizeCategory.COMPACT
            maxWidth < 840.dp -> WindowSizeCategory.MEDIUM
            else -> WindowSizeCategory.EXPANDED
        }
        val textSizePreset = when (sizeCategory) {
            WindowSizeCategory.COMPACT -> ResponsiveTextSizePreset.COMPACT
            WindowSizeCategory.MEDIUM -> ResponsiveTextSizePreset.MEDIUM
            WindowSizeCategory.EXPANDED -> ResponsiveTextSizePreset.EXPANDED
        }
        val typography = remember(textSizePreset, textSizePreference, uiExperience) {
            openTasksTypography(textSizePreset, textSizePreference.scale).let { if (uiExperience == UiExperience.REIMAGINED) it.withSansSerif() else it }
        }
        val extendedTypography = remember(textSizePreset, textSizePreference, uiExperience) {
            openTasksExtendedTypography(textSizePreset, textSizePreference.scale).let { if (uiExperience == UiExperience.REIMAGINED) it.withSansSerif() else it }
        }
        val dimensions = remember(sizeCategory) {
            when (sizeCategory) {
                WindowSizeCategory.COMPACT -> compactDimensions()
                WindowSizeCategory.MEDIUM -> mediumDimensions()
                WindowSizeCategory.EXPANDED -> expandedDimensions()
            }
        }

        CompositionLocalProvider(
            LocalUiExperience provides uiExperience,
            LocalOpenTasksTypography provides extendedTypography,
            LocalOpenTasksDimensions provides dimensions,
            LocalWindowSizeCategory provides sizeCategory,
        ) {
            MaterialTheme(
                colorScheme = when {
                    uiExperience == UiExperience.CLASSIC -> if (darkTheme) DarkColorScheme else LightColorScheme
                    darkTheme -> DarkColorScheme.copy(primary = Color(0xFFB6C7FF), onPrimary = Color(0xFF122858), primaryContainer = Color(0xFF27365C), onPrimaryContainer = Color(0xFFDDE5FF), secondaryContainer = Color(0xFF27365C), onSecondaryContainer = Color(0xFFDDE5FF), background = Color(0xFF111318), surface = Color(0xFF191C23), surfaceVariant = Color(0xFF222734), onSurface = Color(0xFFE5E8F2), onBackground = Color(0xFFE5E8F2), onSurfaceVariant = Color(0xFFAEB7CA))
                    else -> LightColorScheme.copy(primary = Color(0xFF3555AB), primaryContainer = Color(0xFFE5EBFF), onPrimaryContainer = Color(0xFF2D4685), secondaryContainer = Color(0xFFE5EBFF), onSecondaryContainer = Color(0xFF2D4685), background = Color(0xFFF6F7FB), surface = Color.White, surfaceVariant = Color(0xFFEDF0F8), onSurface = Color(0xFF202534), onBackground = Color(0xFF202534), onSurfaceVariant = Color(0xFF596174))
                },
                typography = typography,
                content = {
                    CompositionLocalProvider(
                        LocalContentColor provides if (uiExperience == UiExperience.REIMAGINED) MaterialTheme.colorScheme.onBackground else inheritedContentColor,
                        content = content,
                    )
                },
            )
        }
    }
}

object OpenTasksTheme {
    val typography: OpenTasksExtendedTypography
        @Composable
        get() = LocalOpenTasksTypography.current

    val dimens: OpenTasksDimensions
        @Composable
        get() = LocalOpenTasksDimensions.current

    val windowSizeCategory: WindowSizeCategory
        @Composable
        get() = LocalWindowSizeCategory.current
}
