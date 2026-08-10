package com.udnahc.opentasks.widget

import android.content.Context
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.auth.AccountBoundary
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.widget_filter_all
import opentasks.composeapp.generated.resources.widget_filter_next_7_days
import opentasks.composeapp.generated.resources.widget_filter_today
import opentasks.composeapp.generated.resources.widget_filter_tomorrow
import opentasks.composeapp.generated.resources.widget_empty_tasks
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("TaskWidget")

private val REFRESH_TRIGGER_KEY = longPreferencesKey("refresh_trigger")

private sealed class WidgetData {
    data object Loading : WidgetData()
    data class Ready(
        val tasks: List<WidgetTask>,
        val filterLabel: String,
        val prefs: WidgetPreferences,
        val emptyMessage: String,
        val boundary: AccountBoundary,
    ) : WidgetData()
}

private suspend fun resolveFilterLabel(prefs: WidgetPreferences, categories: List<Category>): String =
    when (prefs.filterType) {
        WidgetFilterType.ALL -> getString(Res.string.widget_filter_all)
        WidgetFilterType.TODAY -> getString(Res.string.widget_filter_today)
        WidgetFilterType.TOMORROW -> getString(Res.string.widget_filter_tomorrow)
        WidgetFilterType.NEXT_7_DAYS -> getString(Res.string.widget_filter_next_7_days)
        WidgetFilterType.CATEGORY ->
            categories.find { it.id == prefs.filterCategoryId }?.name
                ?: getString(Res.string.widget_filter_all)
    }

class TaskWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object : KoinComponent {
        val instance = TaskWidget()
        private val widgetAccountGate: WidgetAccountGate by inject()

        suspend fun refreshWidget(context: Context, appWidgetId: Int) {
            try {
                val refreshed = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                    refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                }
                if (refreshed == null) log.d { "Skipped task widget refresh without an authenticated boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh widget $appWidgetId" }
            }
        }

        internal suspend fun refreshWidgetWithinBoundary(
            context: Context,
            appWidgetId: Int,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceId = manager.getGlanceIds(TaskWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    WidgetBoundaryMarker.write(this, boundary)
                    this[REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                        this[REFRESH_TRIGGER_KEY] ?: 0L,
                        System.currentTimeMillis(),
                    )
                }
            }
            instance.update(context, glanceId)
        }

        suspend fun refreshAllWidgets(context: Context) {
            try {
                val refreshed = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                    refreshAllWidgetsWithinBoundary(context, boundary)
                }
                if (refreshed == null) log.d { "Skipped task widget refresh without an authenticated boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh all widgets" }
            }
        }

        internal suspend fun refreshAllWidgetsWithinBoundary(
            context: Context,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TaskWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetBoundaryMarker.write(this, boundary)
                        this[REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                            this[REFRESH_TRIGGER_KEY] ?: 0L,
                            System.currentTimeMillis(),
                        )
                    }
                }
                instance.update(context, glanceId)
            }
        }

        internal suspend fun blankAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TaskWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetBoundaryMarker.clear(this)
                        this[REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                            this[REFRESH_TRIGGER_KEY] ?: 0L,
                            System.currentTimeMillis(),
                        )
                    }
                }
                instance.update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            log.e(e) { "Failed to get widget ID" }
            0
        }

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val refreshTrigger = glancePrefs[REFRESH_TRIGGER_KEY] ?: 0L
            val marker = WidgetBoundaryMarker.read(glancePrefs)

            val data = produceState<WidgetData>(
                WidgetData.Loading,
                refreshTrigger,
                marker.accountId,
                marker.boundaryEpoch,
            ) {
                value = if (marker.accountId.isNullOrBlank() || marker.boundaryEpoch == null) {
                    WidgetData.Loading
                } else {
                    try {
                        val provider = WidgetDataProvider()
                        provider.withAuthenticatedBoundary { boundary ->
                            val prefs = WidgetPreferences.load(context, appWidgetId)
                            val tasks = provider.getWidgetTasksWithinBoundary(prefs)
                            val categories = provider.getCategoriesWithinBoundary()
                            val filterLabel = resolveFilterLabel(prefs, categories)
                            log.v { "Widget $appWidgetId: ${tasks.size} tasks, filter=$filterLabel, trigger=$refreshTrigger" }
                            WidgetData.Ready(
                                tasks,
                                filterLabel,
                                prefs,
                                getString(Res.string.widget_empty_tasks),
                                boundary,
                            )
                        } ?: WidgetData.Loading
                    } catch (e: Exception) {
                        log.e(e) { "Widget data fetch failed" }
                        WidgetData.Loading
                    }
                }
            }

            when (val d = data.value) {
                is WidgetData.Loading -> TaskWidgetContent(
                    emptyList(), "...", WidgetPreferences(appWidgetId), appWidgetId, null,
                )
                is WidgetData.Ready -> if (WidgetBoundaryMarker.matches(marker, d.boundary)) {
                    TaskWidgetContent(
                        d.tasks, d.filterLabel, d.prefs, appWidgetId, d.emptyMessage,
                        d.boundary.accountId, d.boundary.boundaryEpoch,
                    )
                } else {
                    TaskWidgetContent(
                        emptyList(), "...", WidgetPreferences(appWidgetId), appWidgetId, null,
                    )
                }
            }
        }
    }
}
