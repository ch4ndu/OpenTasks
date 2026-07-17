package com.udnahc.opentasks.widget

import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.model.TaskPriority

internal data class WidgetThemeColors(
    val background: Int,
    val text: Int,
    val dimmed: Int,
)

internal fun widgetThemeColors(theme: WidgetTheme): WidgetThemeColors = when (theme) {
    WidgetTheme.DARK -> WidgetThemeColors(
        background = R.color.widget_bg_dark,
        text = R.color.widget_text_white,
        dimmed = R.color.calendar_widget_day_dimmed,
    )
    WidgetTheme.LIGHT -> WidgetThemeColors(
        background = R.color.widget_bg_light,
        text = R.color.widget_text_black,
        dimmed = R.color.calendar_widget_day_dimmed_light,
    )
    WidgetTheme.SYSTEM -> WidgetThemeColors(
        background = R.color.widget_bg_system,
        text = R.color.widget_text_system,
        dimmed = R.color.calendar_widget_day_dimmed_system,
    )
}

internal fun priorityBgColorRes(priority: TaskPriority): Int = when (priority) {
    TaskPriority.HIGH -> R.color.widget_priority_high
    TaskPriority.MEDIUM -> R.color.widget_priority_medium
    TaskPriority.LOW -> R.color.widget_priority_low
    TaskPriority.NONE -> R.color.widget_priority_none
}

internal fun priorityTextColorRes(priority: TaskPriority): Int = when (priority) {
    TaskPriority.HIGH -> R.color.widget_priority_high_text
    TaskPriority.MEDIUM -> R.color.widget_priority_medium_text
    TaskPriority.LOW -> R.color.widget_priority_low_text
    TaskPriority.NONE -> R.color.widget_priority_none_text
}
