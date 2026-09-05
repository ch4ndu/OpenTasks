package com.udnahc.opentasks.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.sync.runScheduledSyncMaintenance
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
            val refreshed = refreshWidgetAfterSync(
                context,
                glanceId,
                triggerSyncAction,
                widgetAccountGate,
            ) { appWidgetId, boundary ->
                TaskWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
            }
            if (refreshed) log.d { "Task widget refreshed" }
            else log.d { "Skipped task widget action without an active cache boundary" }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            log.e { "Task widget refresh failed" }
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
            val refreshed = refreshWidgetAfterSync(
                context,
                glanceId,
                triggerSyncAction,
                widgetAccountGate,
            ) { appWidgetId, boundary ->
                CalendarWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
            }
            if (refreshed) log.d { "Calendar widget refreshed" }
            else log.d { "Skipped calendar widget action without an active cache boundary" }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            log.e { "Calendar widget refresh failed" }
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
            val refreshed = refreshWidgetAfterSync(
                context,
                glanceId,
                triggerSyncAction,
                widgetAccountGate,
            ) { appWidgetId, boundary ->
                WeekWidget.refreshWidgetWithinBoundary(context, appWidgetId, boundary)
            }
            if (refreshed) log.d { "Week widget refreshed" }
            else log.d { "Skipped week widget action without an active cache boundary" }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            log.e { "Week widget refresh failed" }
        }
    }
}

private suspend fun refreshWidgetAfterSync(
    context: Context,
    glanceId: GlanceId,
    triggerSyncAction: TriggerSyncAction,
    widgetAccountGate: WidgetAccountGate,
    refreshWidget: suspend (Int, AccountBoundary) -> Unit,
): Boolean {
    val capturedBoundary = widgetAccountGate.withActiveCacheBoundary { boundary -> boundary }
        ?: return false
    withContext(Dispatchers.Main) {
        Toast.makeText(context, getString(Res.string.widget_refreshing), Toast.LENGTH_SHORT).show()
    }
    runScheduledSyncMaintenance(
        capturedBoundary = capturedBoundary,
        syncNetwork = { triggerSyncAction.syncNow() },
        withRevalidatedBoundary = { expected, block ->
            widgetAccountGate.withForegroundBoundary(expected, block)
        },
        maintenanceSteps = listOf(
            { boundary ->
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
                refreshWidget(appWidgetId, boundary)
            },
        ),
    )
    return true
}
