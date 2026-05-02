package com.udnahc.opentasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

enum class ResponsiveTextSizePreset { COMPACT, MEDIUM, EXPANDED }

fun openTasksTypography(
    preset: ResponsiveTextSizePreset = ResponsiveTextSizePreset.MEDIUM,
    userScale: Float = 1.0f,
): Typography {
    val scale = preset.scale * userScale
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.scaledBy(scale),
        displayMedium = default.displayMedium.scaledBy(scale),
        displaySmall = default.displaySmall.scaledBy(scale),
        headlineLarge = default.headlineLarge.scaledBy(scale),
        headlineMedium = TextStyle(
            fontSize = 28.sp * scale,
            fontWeight = FontWeight.Bold,
        ),
        headlineSmall = default.headlineSmall.scaledBy(scale),
        titleLarge = TextStyle(
            fontSize = 20.sp * scale,
        ),
        titleMedium = TextStyle(
            fontSize = 18.sp * scale,
        ),
        titleSmall = default.titleSmall.scaledBy(scale),
        bodyLarge = TextStyle(
            fontSize = 16.sp * scale,
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp * scale,
        ),
        bodySmall = TextStyle(
            fontSize = 14.sp * scale,
        ),
        labelLarge = TextStyle(
            fontSize = 13.sp * scale,
        ),
        labelMedium = TextStyle(
            fontSize = 12.sp * scale,
        ),
        labelSmall = TextStyle(
            fontSize = 11.sp * scale,
        ),
    )
}

data class OpenTasksExtendedTypography(
    val calendarDayNumber: TextStyle,
    val calendarEventTitle: TextStyle,
    val calendarEventOverflow: TextStyle,
    val quadrantBadge: TextStyle,
    val quadrantBadgeSmall: TextStyle,
)

fun openTasksExtendedTypography(
    preset: ResponsiveTextSizePreset = ResponsiveTextSizePreset.MEDIUM,
    userScale: Float = 1.0f,
): OpenTasksExtendedTypography {
    val scale = preset.scale * userScale
    return OpenTasksExtendedTypography(
        calendarDayNumber = TextStyle(
            fontSize = 13.sp * scale,
        ),
        calendarEventTitle = TextStyle(
            fontSize = 9.sp * scale,
            fontWeight = FontWeight.Medium,
            lineHeight = 10.sp * scale,
        ),
        calendarEventOverflow = TextStyle(
            fontSize = 8.sp * scale,
        ),
        quadrantBadge = TextStyle(
            fontSize = 9.sp * scale,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp * scale,
        ),
        quadrantBadgeSmall = TextStyle(
            fontSize = 7.sp * scale,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp * scale,
        ),
    )
}

val LocalOpenTasksTypography = staticCompositionLocalOf {
    openTasksExtendedTypography()
}

private val ResponsiveTextSizePreset.scale: Float
    get() = when (this) {
        ResponsiveTextSizePreset.COMPACT -> 0.85f
        ResponsiveTextSizePreset.MEDIUM -> 1.0f
        ResponsiveTextSizePreset.EXPANDED -> 1.15f
    }

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(
        fontSize = fontSize * scale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
