package com.udnahc.opentasks.widget

import android.content.Context
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.extractDay
import com.udnahc.opentasks.data.extensions.extractMonth
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.startOfWeekLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import org.lighthousegames.logging.logging

private val log = logging("WeekWidget")

private val WEEK_REFRESH_TRIGGER_KEY = longPreferencesKey("week_refresh_trigger")
private val WEEK_OFFSET_KEY = intPreferencesKey("week_offset")

private val MONTH_NAMES_SHORT = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val DAY_OF_WEEK_NAMES = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class WeekDay(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val dayOfWeekLabel: String,
    val tasks: List<CalendarDayTask>,
)

private sealed class WeekWidgetData {
    data object Loading : WeekWidgetData()
    data class Ready(
        val weekLabel: String,
        val days: List<WeekDay>,
        val todayDayOfMonth: Int,
        val todayMonth: Int,
        val prefs: CalendarWidgetPreferences,
    ) : WeekWidgetData()
}

class WeekWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val instance = WeekWidget()

        suspend fun refreshWidget(context: Context, appWidgetId: Int) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIds(WeekWidget::class.java)
                    .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WEEK_REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                    }
                }
                instance.update(context, glanceId)
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh week widget $appWidgetId" }
            }
        }

        suspend fun refreshAllWidgets(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                manager.getGlanceIds(WeekWidget::class.java).forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WEEK_REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                        }
                    }
                    instance.update(context, glanceId)
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh all week widgets" }
            }
        }

        suspend fun navigateWeek(context: Context, appWidgetId: Int, delta: Int) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIds(WeekWidget::class.java)
                    .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val currentOffset = prefs[WEEK_OFFSET_KEY] ?: 0
                    prefs.toMutablePreferences().apply {
                        this[WEEK_OFFSET_KEY] = currentOffset + delta
                        this[WEEK_REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                    }
                }
                instance.update(context, glanceId)
            } catch (e: Exception) {
                log.e(e) { "Failed to navigate week for widget $appWidgetId" }
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
            val refreshTrigger = glancePrefs[WEEK_REFRESH_TRIGGER_KEY] ?: 0L
            val weekOffset = glancePrefs[WEEK_OFFSET_KEY] ?: 0

            val data = produceState<WeekWidgetData>(
                WeekWidgetData.Loading,
                refreshTrigger, weekOffset,
            ) {
                value = try {
                    val prefs = CalendarWidgetPreferences.load(context, appWidgetId)
                    val today = todayLocal()
                    val todayLocalMillis = startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
                    val targetLocalMillis = todayLocalMillis + weekOffset * 7 * MILLIS_PER_DAY
                    val weekStartMillis = startOfWeekLocalMillis(targetLocalMillis)

                    val provider = WidgetDataProvider()
                    val tasksByDayIndex = provider.getTasksByDayForWeek(weekStartMillis)

                    val days = (0 until 7).map { i ->
                        val dayMillis = weekStartMillis + i * MILLIS_PER_DAY
                        WeekDay(
                            year = extractYear(dayMillis),
                            month = extractMonth(dayMillis),
                            dayOfMonth = extractDay(dayMillis),
                            dayOfWeekLabel = DAY_OF_WEEK_NAMES[i],
                            tasks = tasksByDayIndex[i].orEmpty(),
                        )
                    }

                    val startDay = days.first()
                    val endDay = days.last()
                    val weekLabel = formatWeekLabel(startDay, endDay)

                    log.v { "Week widget $appWidgetId: $weekLabel, offset=$weekOffset" }
                    WeekWidgetData.Ready(
                        weekLabel = weekLabel,
                        days = days,
                        todayDayOfMonth = today.dayOfMonth,
                        todayMonth = today.monthNumber,
                        prefs = prefs,
                    )
                } catch (e: Exception) {
                    log.e(e) { "Week widget data fetch failed" }
                    val prefs = CalendarWidgetPreferences(appWidgetId)
                    WeekWidgetData.Ready(
                        weekLabel = "This Week",
                        days = emptyList(),
                        todayDayOfMonth = 0,
                        todayMonth = 0,
                        prefs = prefs,
                    )
                }
            }

            when (val d = data.value) {
                is WeekWidgetData.Loading -> WeekWidgetContent(
                    weekLabel = "...",
                    days = emptyList(),
                    todayDayOfMonth = 0,
                    todayMonth = 0,
                    prefs = CalendarWidgetPreferences(appWidgetId),
                    appWidgetId = appWidgetId,
                )
                is WeekWidgetData.Ready -> WeekWidgetContent(
                    weekLabel = d.weekLabel,
                    days = d.days,
                    todayDayOfMonth = d.todayDayOfMonth,
                    todayMonth = d.todayMonth,
                    prefs = d.prefs,
                    appWidgetId = appWidgetId,
                )
            }
        }
    }
}

private fun formatWeekLabel(start: WeekDay, end: WeekDay): String {
    val startMonth = MONTH_NAMES_SHORT[start.month - 1]
    return if (start.month == end.month) {
        "$startMonth ${start.dayOfMonth} - ${end.dayOfMonth}"
    } else {
        val endMonth = MONTH_NAMES_SHORT[end.month - 1]
        "$startMonth ${start.dayOfMonth} - $endMonth ${end.dayOfMonth}"
    }
}
