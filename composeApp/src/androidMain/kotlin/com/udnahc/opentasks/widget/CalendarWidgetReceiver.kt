package com.udnahc.opentasks.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.lighthousegames.logging.logging

private val log = logging("CalendarWidgetReceiver")

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget.instance

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log.d { "Calendar widget deleted: ${appWidgetIds.toList()}" }
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { CalendarWidgetPreferences.delete(context, it) }
    }
}
