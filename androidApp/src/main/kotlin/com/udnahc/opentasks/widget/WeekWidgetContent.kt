package com.udnahc.opentasks.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.udnahc.opentasks.ACTION_VIEW_CALENDAR
import com.udnahc.opentasks.EXTRA_WIDGET_ACTION
import com.udnahc.opentasks.EXTRA_WIDGET_CALENDAR_DAY
import com.udnahc.opentasks.EXTRA_WIDGET_CALENDAR_MONTH
import com.udnahc.opentasks.EXTRA_WIDGET_CALENDAR_YEAR
import com.udnahc.opentasks.EXTRA_ACCOUNT_ID
import com.udnahc.opentasks.EXTRA_BOUNDARY_EPOCH
import com.udnahc.opentasks.R
import android.content.Context as AndroidContext

private const val PKG = "com.udnahc.opentasks"
private const val MAIN_ACTIVITY = "$PKG.MainActivity"

private fun weekDayIntent(
    year: Int,
    month: Int,
    day: Int,
    accountId: String? = null,
    boundaryEpoch: Long = 0L,
): Intent =
    Intent().apply {
        component = ComponentName(PKG, MAIN_ACTIVITY)
        putExtra(EXTRA_WIDGET_ACTION, ACTION_VIEW_CALENDAR)
        putExtra(EXTRA_WIDGET_CALENDAR_YEAR, year)
        putExtra(EXTRA_WIDGET_CALENDAR_MONTH, month)
        putExtra(EXTRA_WIDGET_CALENDAR_DAY, day)
        if (accountId != null) putExtra(EXTRA_ACCOUNT_ID, accountId)
        putExtra(EXTRA_BOUNDARY_EPOCH, boundaryEpoch)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun weekSettingsIntent(appWidgetId: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, "$PKG.widget.WeekWidgetSettingsActivity")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

// ActionCallback for week navigation (prev/next) — avoids a separate Activity
class WeekNavPrevCallback : ActionCallback {
    override suspend fun onAction(
        context: AndroidContext,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WeekWidget.navigateWeek(context, appWidgetId, -1)
    }
}

class WeekNavNextCallback : ActionCallback {
    override suspend fun onAction(
        context: AndroidContext,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WeekWidget.navigateWeek(context, appWidgetId, 1)
    }
}

@Composable
fun WeekWidgetContent(
    weekLabel: String,
    days: List<WeekDay>,
    todayDayOfMonth: Int,
    todayMonth: Int,
    prefs: CalendarWidgetPreferences,
    appWidgetId: Int,
    accountId: String? = null,
    boundaryEpoch: Long = 0L,
) {
    val themeColors = widgetThemeColors(prefs.theme)
    val bgColor = widgetResourceColor(themeColors.background)
    val textColor = widgetResourceColor(themeColors.text)
    val headerColor = widgetResourceColor(R.color.widget_text_gray)
    val todayBgColor = widgetResourceColor(R.color.calendar_widget_today_bg)
    val todayTextColor = widgetResourceColor(R.color.calendar_widget_today_text)

    val titleFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 12.sp
        WidgetFontSize.NORMAL -> 14.sp
        WidgetFontSize.LARGE -> 16.sp
    }
    val dayFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 11.sp
        WidgetFontSize.LARGE -> 13.sp
    }
    val headerFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 6.sp
        WidgetFontSize.NORMAL -> 7.sp
        WidgetFontSize.LARGE -> 9.sp
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
            .padding(2.dp),
    ) {
        // Header: [spacer] ◂ Week Label ▸ [spacer] [↻] [⚙]
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left spacer to center the nav group
            Box(modifier = GlanceModifier.defaultWeight()) {}

            // Nav group: chevrons tight around label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\u276E",
                    style = TextStyle(color = textColor, fontSize = 16.sp),
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp)
                        .clickable(actionRunCallback<WeekNavPrevCallback>()),
                )
                Text(
                    text = weekLabel,
                    style = TextStyle(
                        color = textColor,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "\u276F",
                    style = TextStyle(color = textColor, fontSize = 16.sp),
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp)
                        .clickable(actionRunCallback<WeekNavNextCallback>()),
                )
            }

            // Right spacer to balance centering
            Box(modifier = GlanceModifier.defaultWeight()) {}

            // Buttons pinned right
            Text(
                text = "\u21BB",
                style = TextStyle(
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .clickable(actionRunCallback<WeekRefreshCallback>()),
            )
            Text(
                text = "\u2699",
                style = TextStyle(
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .clickable(actionStartActivity(weekSettingsIntent(appWidgetId))),
            )
        }

        // Day columns: 7 equal-width columns
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        ) {
            for (day in days) {
                val isToday = day.dayOfMonth == todayDayOfMonth && day.month == todayMonth

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(
                            actionStartActivity(
                                weekDayIntent(
                                    day.year,
                                    day.month,
                                    day.dayOfMonth,
                                    accountId,
                                    boundaryEpoch,
                                )
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Day-of-week abbreviation (always plain)
                    Text(
                        text = day.dayOfWeekLabel,
                        style = TextStyle(
                            color = if (isToday) todayBgColor else headerColor,
                            fontSize = headerFontSize,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                    )

                    // Day number (circle highlight on today only)
                    if (isToday) {
                        Box(
                            modifier = GlanceModifier
                                .size(22.dp)
                                .background(todayBgColor)
                                .cornerRadius(11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${day.dayOfMonth}",
                                style = TextStyle(
                                    color = todayTextColor,
                                    fontSize = dayFontSize,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    } else {
                        Text(
                            text = "${day.dayOfMonth}",
                            style = TextStyle(
                                color = textColor,
                                fontSize = dayFontSize,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }

                    // Task labels (max 1 per day)
                    for (task in day.tasks) {
                        Text(
                            text = task.title,
                            style = TextStyle(
                                color = widgetResourceColor(priorityTextColorRes(task.priority)),
                                fontSize = taskFontSize,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier
                                .background(widgetResourceColor(priorityBgColorRes(task.priority)))
                                .cornerRadius(2.dp)
                                .padding(horizontal = 1.dp),
                        )
                    }
                }
            }

            // If days list is empty (loading), fill with 7 empty placeholders
            if (days.isEmpty()) {
                for (i in 0 until 7) {
                    Box(modifier = GlanceModifier.defaultWeight()) {}
                }
            }
        }
    }
}
