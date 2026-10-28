package com.udnahc.opentasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
    val matrixTaskTitle: TextStyle = TextStyle.Default,
    val matrixTaskMeta: TextStyle = TextStyle.Default,
    val appBarTitle: TextStyle = Typography().titleLarge,
)

fun openTasksExtendedTypography(
    preset: ResponsiveTextSizePreset = ResponsiveTextSizePreset.MEDIUM,
    userScale: Float = 1.0f,
): OpenTasksExtendedTypography {
    val scale = preset.scale * userScale
    return OpenTasksExtendedTypography(
        appBarTitle = Typography().titleLarge.scaledBy(userScale),
        matrixTaskTitle = TextStyle(fontSize = 12.sp * userScale, lineHeight = 16.sp * userScale),
        matrixTaskMeta = TextStyle(fontSize = 11.sp * userScale, lineHeight = 14.sp * userScale),
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

internal fun Typography.withSansSerif(): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = labelSmall.copy(fontFamily = FontFamily.SansSerif),
)

internal fun OpenTasksExtendedTypography.withSansSerif(): OpenTasksExtendedTypography = copy(
    appBarTitle = appBarTitle.copy(fontFamily = FontFamily.SansSerif),
    calendarDayNumber = calendarDayNumber.copy(fontFamily = FontFamily.SansSerif),
    calendarEventTitle = calendarEventTitle.copy(fontFamily = FontFamily.SansSerif),
    calendarEventOverflow = calendarEventOverflow.copy(fontFamily = FontFamily.SansSerif),
    quadrantBadge = quadrantBadge.copy(fontFamily = FontFamily.SansSerif),
    quadrantBadgeSmall = quadrantBadgeSmall.copy(fontFamily = FontFamily.SansSerif),
    matrixTaskTitle = matrixTaskTitle.copy(fontFamily = FontFamily.SansSerif),
    matrixTaskMeta = matrixTaskMeta.copy(fontFamily = FontFamily.SansSerif),
)
