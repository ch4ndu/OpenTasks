package com.udnahc.opentasks.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("WidgetRefreshCallbacks")

class TaskRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Task widget refresh triggered" }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Refreshing\u2026", Toast.LENGTH_SHORT).show()
        }
        try {
            triggerSyncAction()
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            TaskWidget.refreshWidget(context, appWidgetId)
            log.d { "Task widget refreshed" }
        } catch (e: Exception) {
            log.e { "Task widget refresh failed: ${e.message}" }
        }
    }
}

class CalendarRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Calendar widget refresh triggered" }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Refreshing\u2026", Toast.LENGTH_SHORT).show()
        }
        try {
            triggerSyncAction()
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            CalendarWidget.refreshWidget(context, appWidgetId)
            log.d { "Calendar widget refreshed" }
        } catch (e: Exception) {
            log.e { "Calendar widget refresh failed: ${e.message}" }
        }
    }
}

class WeekRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Week widget refresh triggered" }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Refreshing\u2026", Toast.LENGTH_SHORT).show()
        }
        try {
            triggerSyncAction()
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            WeekWidget.refreshWidget(context, appWidgetId)
            log.d { "Week widget refreshed" }
        } catch (e: Exception) {
            log.e { "Week widget refresh failed: ${e.message}" }
        }
    }
}
