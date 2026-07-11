package com.udnahc.opentasks.widget

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.appwidget.AppWidgetManager
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.udnahc.opentasks.R

private const val PKG = "com.udnahc.opentasks"
private const val MAIN_ACTIVITY = "$PKG.MainActivity"

private fun mainIntent(action: String, taskId: String? = null): Intent =
    Intent().apply {
        component = ComponentName(PKG, MAIN_ACTIVITY)
        putExtra("widget_action", action)
        if (taskId != null) putExtra("task_id", taskId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun filterPickerIntent(appWidgetId: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, "$PKG.widget.WidgetFilterPickerActivity")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun taskSettingsIntent(appWidgetId: Int): Intent =
    Intent().apply {
        component = ComponentName(PKG, "$PKG.widget.WidgetSettingsActivity")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

@Composable
fun TaskWidgetContent(
    tasks: List<WidgetTask>,
    filterLabel: String,
    prefs: WidgetPreferences,
    appWidgetId: Int,
    emptyMessage: String?,
) {
    val isDark = prefs.theme != WidgetTheme.LIGHT
    val bgColor = widgetResourceColor(if (isDark) R.color.widget_bg_dark else R.color.widget_bg_light)
    val textColor = widgetResourceColor(if (isDark) R.color.widget_text_white else R.color.widget_text_black)
    val dateColor = widgetResourceColor(R.color.widget_date_red)
    val grayColor = widgetResourceColor(R.color.widget_text_gray)

    val fontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 12.sp
        WidgetFontSize.NORMAL -> 14.sp
        WidgetFontSize.LARGE -> 16.sp
    }
    val contentFontSize = when (prefs.fontSize) {
        WidgetFontSize.SMALL -> 10.sp
        WidgetFontSize.NORMAL -> 12.sp
        WidgetFontSize.LARGE -> 14.sp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.cornerRadius(16.dp) else it }
            .padding(12.dp),
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$filterLabel \u25BE",
                style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(5.dp)
                    .defaultWeight()
                    .clickable(actionStartActivity(filterPickerIntent(appWidgetId))),
            )
            Text(
                text = "+",
                style = TextStyle(color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp)
                    .clickable(actionStartActivity(mainIntent("create_task"))),
            )
            Text(
                text = "\u21BB",
                style = TextStyle(color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp)
                    .clickable(actionRunCallback<TaskRefreshCallback>()),
            )
            Text(
                text = "\u2699",
                style = TextStyle(color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(start = 4.dp)
                    .clickable(actionStartActivity(taskSettingsIntent(appWidgetId))),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Task list
        if (tasks.isEmpty() && emptyMessage != null) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMessage,
                    style = TextStyle(color = grayColor, fontSize = fontSize),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    val clickAction = if (prefs.onClickAction == WidgetClickAction.OPEN_TASK) {
                        actionStartActivity(mainIntent("view_task", task.id))
                    } else {
                        actionStartActivity(mainIntent("view_list"))
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(clickAction),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = task.title,
                            style = TextStyle(color = textColor, fontSize = contentFontSize),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1,
                        )
                        if (!prefs.hideDueDate && task.dateLabel != null) {
                            Text(
                                text = task.dateLabel,
                                style = TextStyle(color = dateColor, fontSize = contentFontSize),
                                modifier = GlanceModifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
