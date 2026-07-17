package com.udnahc.opentasks.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
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
    textSizePreference: TextSizePreference = TextSizePreference.SMALL,
    content: @Composable () -> Unit,
) {
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
        val typography = remember(textSizePreset, textSizePreference) {
            openTasksTypography(textSizePreset, textSizePreference.scale)
        }
        val extendedTypography = remember(textSizePreset, textSizePreference) {
            openTasksExtendedTypography(textSizePreset, textSizePreference.scale)
        }
        val dimensions = remember(sizeCategory) {
            when (sizeCategory) {
                WindowSizeCategory.COMPACT -> compactDimensions()
                WindowSizeCategory.MEDIUM -> mediumDimensions()
                WindowSizeCategory.EXPANDED -> expandedDimensions()
            }
        }

        CompositionLocalProvider(
            LocalOpenTasksTypography provides extendedTypography,
            LocalOpenTasksDimensions provides dimensions,
            LocalWindowSizeCategory provides sizeCategory,
        ) {
            MaterialTheme(
                colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
                typography = typography,
                content = content,
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
