package com.udnahc.opentasks.widget

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.udnahc.opentasks.R
import com.udnahc.opentasks.data.model.TaskPriority

private const val PKG = "com.udnahc.opentasks"
private const val MAIN_ACTIVITY = "$PKG.MainActivity"
private const val WEEKS_TO_DISPLAY = 6
private const val DAYS_PER_WEEK = 7

private val DAY_HEADERS = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private fun calendarDayIntent(year: Int, month: Int, day: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, MAIN_ACTIVITY)
        putExtra("widget_action", "view_calendar")
        putExtra("calendar_year", year)
        putExtra("calendar_month", month)
        putExtra("calendar_day", day)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun navigationIntent(appWidgetId: Int, delta: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, "$PKG.widget.CalendarWidgetNavigationActivity")
        putExtra("appWidgetId", appWidgetId)
        putExtra("delta", delta)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun calendarMenuIntent(appWidgetId: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, "$PKG.widget.CalendarWidgetMenuActivity")
        putExtra("appWidgetId", appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun priorityBgColorRes(priority: TaskPriority): Int = when (priority) {
    TaskPriority.HIGH -> R.color.widget_priority_high
    TaskPriority.MEDIUM -> R.color.widget_priority_medium
    TaskPriority.LOW -> R.color.widget_priority_low
    TaskPriority.NONE -> R.color.widget_priority_none
}

private fun priorityTextColorRes(priority: TaskPriority): Int = when (priority) {
    TaskPriority.HIGH -> R.color.widget_priority_high_text
    TaskPriority.MEDIUM -> R.color.widget_priority_medium_text
    TaskPriority.LOW -> R.color.widget_priority_low_text
    TaskPriority.NONE -> R.color.widget_priority_none_text
}

@Composable
fun CalendarWidgetContent(
    year: Int,
    month: Int,
    monthLabel: String,
    daysInMonth: Int,
    firstDayOfWeekOffset: Int,
    tasksByDay: Map<Int, List<CalendarDayTask>>,
    todayDay: Int,
    prefs: CalendarWidgetPreferences,
    appWidgetId: Int,
) {
    val isDark = prefs.theme != WidgetTheme.LIGHT
    val bgColor = ColorProvider(if (isDark) R.color.widget_bg_dark else R.color.widget_bg_light)
    val textColor = ColorProvider(if (isDark) R.color.widget_text_white else R.color.widget_text_black)
    val headerColor = ColorProvider(R.color.widget_text_gray)
    val todayBgColor = ColorProvider(R.color.calendar_widget_today_bg)
    val todayTextColor = ColorProvider(R.color.calendar_widget_today_text)
    val dimmedColor = ColorProvider(
        if (isDark) R.color.calendar_widget_day_dimmed else R.color.calendar_widget_day_dimmed_light
    )

    val titleFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 11.sp
        WidgetFontSize.NORMAL -> 13.sp
        WidgetFontSize.LARGE -> 15.sp
    }
    val dayFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 11.sp
        WidgetFontSize.LARGE -> 13.sp
    }
    val headerFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 8.sp
        WidgetFontSize.NORMAL -> 9.sp
        WidgetFontSize.LARGE -> 11.sp
    }
    val taskFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 7.sp
        WidgetFontSize.NORMAL -> 8.sp
        WidgetFontSize.LARGE -> 10.sp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.cornerRadius(16.dp) else it }
            .padding(6.dp),
    ) {
        // Header: [spacer]  ◂ Month ▸  [+] [⋮]
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.width(40.dp)) {}

            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "\u25C2",
                    style = TextStyle(color = textColor, fontSize = 22.sp),
                    modifier = GlanceModifier
                        .padding(horizontal = 6.dp)
                        .clickable(actionStartActivity(navigationIntent(appWidgetId, -1))),
                )
                Text(
                    text = monthLabel,
                    style = TextStyle(
                        color = textColor,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier
                        .clickable(actionStartActivity(calendarDayIntent(year, month, 1))),
                )
                Text(
                    text = "\u25B8",
                    style = TextStyle(color = textColor, fontSize = 22.sp),
                    modifier = GlanceModifier
                        .padding(horizontal = 6.dp)
                        .clickable(actionStartActivity(navigationIntent(appWidgetId, 1))),
                )
            }

            Text(
                text = "\u22EE",
                style = TextStyle(color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .width(40.dp)
                    .clickable(actionStartActivity(calendarMenuIntent(appWidgetId))),
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Day-of-week headers (Sun Mon Tue...)
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (header in DAY_HEADERS) {
                Text(
                    text = header,
                    style = TextStyle(
                        color = headerColor,
                        fontSize = headerFontSize,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Calendar grid: 6 week rows with task labels
        for (week in 0 until WEEKS_TO_DISPLAY) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            ) {
                for (dayOfWeek in 0 until DAYS_PER_WEEK) {
                    val cellIndex = week * DAYS_PER_WEEK + dayOfWeek
                    val day = cellIndex - firstDayOfWeekOffset + 1

                    if (day in 1..daysInMonth) {
                        val isToday = day == todayDay
                        val tasks = tasksByDay[day].orEmpty()

                        // Each day cell: Column with day number + task labels
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(1.dp)
                                .clickable(actionStartActivity(calendarDayIntent(year, month, day))),
                        ) {
                            // Day number
                            if (isToday) {
                                Box(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(20.dp)
                                            .background(todayBgColor)
                                            .cornerRadius(10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$day",
                                            style = TextStyle(
                                                color = todayTextColor,
                                                fontSize = dayFontSize,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "$day",
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = dayFontSize,
                                    ),
                                )
                            }

                            // Task labels (max 2 per day)
                            for (task in tasks) {
                                Text(
                                    text = task.title,
                                    style = TextStyle(
                                        color = ColorProvider(priorityTextColorRes(task.priority)),
                                        fontSize = taskFontSize,
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .background(ColorProvider(priorityBgColorRes(task.priority)))
                                        .cornerRadius(2.dp)
                                        .padding(horizontal = 2.dp),
                                )
                            }
                        }
                    } else {
                        // Empty / adjacent month cell
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(1.dp),
                        ) {
                            val adjacentDay = if (day < 1) {
                                // Previous month: compute what day it is
                                val prevMonthDays = if (month == 1) {
                                    com.udnahc.opentasks.data.extensions.daysInMonth(year - 1, 12)
                                } else {
                                    com.udnahc.opentasks.data.extensions.daysInMonth(year, month - 1)
                                }
                                prevMonthDays + day
                            } else {
                                day - daysInMonth
                            }
                            Text(
                                text = "$adjacentDay",
                                style = TextStyle(
                                    color = dimmedColor,
                                    fontSize = dayFontSize,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
