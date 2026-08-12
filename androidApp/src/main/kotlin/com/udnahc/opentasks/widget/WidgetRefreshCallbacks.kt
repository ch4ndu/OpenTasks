package com.udnahc.opentasks.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.widget_refreshing
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("WidgetRefreshCallbacks")

class TaskRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()
    private val widgetAccountGate: WidgetAccountGate by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Task widget refresh triggered" }
        try {
            val refreshed = widgetAccountGate.withActiveCacheBoundary { boundary ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(Res.string.widget_refreshing), Toast.LENGTH_SHORT).show()
                }
                triggerSyncAction()
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
                TaskWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                log.d { "Task widget refreshed" }
            }
            if (refreshed == null) log.d { "Skipped task widget action without an active cache boundary" }
        } catch (e: Exception) {
            log.e(e) { "Task widget refresh failed" }
        }
    }
}

class CalendarRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()
    private val widgetAccountGate: WidgetAccountGate by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Calendar widget refresh triggered" }
        try {
            val refreshed = widgetAccountGate.withActiveCacheBoundary { boundary ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(Res.string.widget_refreshing), Toast.LENGTH_SHORT).show()
                }
                triggerSyncAction()
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
                CalendarWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                log.d { "Calendar widget refreshed" }
            }
            if (refreshed == null) log.d { "Skipped calendar widget action without an active cache boundary" }
        } catch (e: Exception) {
            log.e(e) { "Calendar widget refresh failed" }
        }
    }
}

class WeekRefreshCallback : ActionCallback, KoinComponent {

    private val triggerSyncAction: TriggerSyncAction by inject()
    private val widgetAccountGate: WidgetAccountGate by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        log.d { "Week widget refresh triggered" }
        try {
            val refreshed = widgetAccountGate.withActiveCacheBoundary { boundary ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(Res.string.widget_refreshing), Toast.LENGTH_SHORT).show()
                }
                triggerSyncAction()
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
                WeekWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                log.d { "Week widget refreshed" }
            }
            if (refreshed == null) log.d { "Skipped week widget action without an active cache boundary" }
        } catch (e: Exception) {
            log.e(e) { "Week widget refresh failed" }
        }
    }
}
