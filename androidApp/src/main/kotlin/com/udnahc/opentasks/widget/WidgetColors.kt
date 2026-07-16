package com.udnahc.opentasks.widget

import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.model.TaskPriority

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
