package com.udnahc.opentasks.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.lighthousegames.logging.logging

private val log = logging("WeekWidgetReceiver")

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget.instance

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log.d { "Week widget deleted: ${appWidgetIds.toList()}" }
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { CalendarWidgetPreferences.delete(context, it) }
    }
}
