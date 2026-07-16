package com.udnahc.opentasks.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.lighthousegames.logging.logging

private val log = logging("TaskWidgetReceiver")

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget.instance

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log.d { "Widget deleted: ${appWidgetIds.toList()}" }
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferences.delete(context, it) }
    }
}
