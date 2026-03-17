package com.udnahc.opentasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class TextSizePreset { SMALL, NORMAL, LARGE }

fun openTasksTypography(preset: TextSizePreset = TextSizePreset.NORMAL): Typography {
    val scale = when (preset) {
        TextSizePreset.SMALL -> 0.85f
        TextSizePreset.NORMAL -> 1.0f
        TextSizePreset.LARGE -> 1.15f
    }
    return Typography(
        headlineMedium = TextStyle(
            fontSize = 28.sp * scale,
            fontWeight = FontWeight.Bold,
        ),
        titleLarge = TextStyle(
            fontSize = 20.sp * scale,
        ),
        titleMedium = TextStyle(
            fontSize = 18.sp * scale,
        ),
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

@Immutable
data class OpenTasksExtendedTypography(
    val calendarDayNumber: TextStyle,
    val calendarEventTitle: TextStyle,
    val calendarEventOverflow: TextStyle,
    val quadrantBadge: TextStyle,
    val quadrantBadgeSmall: TextStyle,
)

fun openTasksExtendedTypography(
    preset: TextSizePreset = TextSizePreset.NORMAL,
): OpenTasksExtendedTypography {
    val scale = when (preset) {
        TextSizePreset.SMALL -> 0.85f
        TextSizePreset.NORMAL -> 1.0f
        TextSizePreset.LARGE -> 1.15f
    }
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
