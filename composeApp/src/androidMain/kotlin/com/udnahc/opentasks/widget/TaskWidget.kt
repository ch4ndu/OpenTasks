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
import com.udnahc.opentasks.data.model.Category
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.widget_filter_all
import opentasks.composeapp.generated.resources.widget_filter_next_7_days
import opentasks.composeapp.generated.resources.widget_filter_today
import opentasks.composeapp.generated.resources.widget_filter_tomorrow
import org.jetbrains.compose.resources.getString
import org.lighthousegames.logging.logging

private val log = logging("TaskWidget")

private val REFRESH_TRIGGER_KEY = longPreferencesKey("refresh_trigger")

private sealed class WidgetData {
    data object Loading : WidgetData()
    data class Ready(
        val tasks: List<WidgetTask>,
        val filterLabel: String,
        val prefs: WidgetPreferences,
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

    companion object {
        val instance = TaskWidget()

        suspend fun refreshWidget(context: Context, appWidgetId: Int) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIds(TaskWidget::class.java)
                    .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                    }
                }
                instance.update(context, glanceId)
            } catch (e: Exception) {
                log.e { "Failed to refresh widget $appWidgetId: ${e.message}" }
            }
        }

        suspend fun refreshAllWidgets(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                manager.getGlanceIds(TaskWidget::class.java).forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                        }
                    }
                    instance.update(context, glanceId)
                }
            } catch (e: Exception) {
                log.e { "Failed to refresh all widgets: ${e.message}" }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            log.e { "Failed to get widget ID: ${e.message}" }
            0
        }

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val refreshTrigger = glancePrefs[REFRESH_TRIGGER_KEY] ?: 0L

            val data = produceState<WidgetData>(WidgetData.Loading, refreshTrigger) {
                value = try {
                    val prefs = WidgetPreferences.load(context, appWidgetId)
                    val provider = WidgetDataProvider()
                    val tasks = provider.getWidgetTasks(prefs)
                    val categories = provider.getCategories()
                    val filterLabel = resolveFilterLabel(prefs, categories)
                    log.v { "Widget $appWidgetId: ${tasks.size} tasks, filter=$filterLabel, trigger=$refreshTrigger" }
                    WidgetData.Ready(tasks, filterLabel, prefs)
                } catch (e: Exception) {
                    log.e { "Widget data fetch failed: ${e.message}" }
                    WidgetData.Ready(
                        emptyList(),
                        getString(Res.string.widget_filter_all),
                        WidgetPreferences(appWidgetId),
                    )
                }
            }

            when (val d = data.value) {
                is WidgetData.Loading -> TaskWidgetContent(
                    emptyList(), "...", WidgetPreferences(appWidgetId), appWidgetId,
                )
                is WidgetData.Ready -> TaskWidgetContent(
                    d.tasks, d.filterLabel, d.prefs, appWidgetId,
                )
            }
        }
    }
}
