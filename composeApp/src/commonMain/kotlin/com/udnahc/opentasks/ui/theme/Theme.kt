package com.udnahc.opentasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    textSizePreset: TextSizePreset = TextSizePreset.NORMAL,
    content: @Composable () -> Unit,
) {
    val typography = remember(textSizePreset) { openTasksTypography(textSizePreset) }
    val extendedTypography = remember(textSizePreset) { openTasksExtendedTypography(textSizePreset) }

    BoxWithConstraints {
        val sizeCategory = when {
            maxWidth < 600.dp -> WindowSizeCategory.COMPACT
            maxWidth < 840.dp -> WindowSizeCategory.MEDIUM
            else -> WindowSizeCategory.EXPANDED
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
}
