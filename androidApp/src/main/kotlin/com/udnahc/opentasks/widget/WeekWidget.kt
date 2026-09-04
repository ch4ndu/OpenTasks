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
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.domain.time.LocalizedDateTimeFormatter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging
import kotlin.coroutines.cancellation.CancellationException

private val log = logging("WeekWidget")

private val WEEK_REFRESH_TRIGGER_KEY = longPreferencesKey("week_refresh_trigger")
private val WEEK_OFFSET_KEY = intPreferencesKey("week_offset")

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
        val boundary: AccountBoundary,
    ) : WeekWidgetData()
}

class WeekWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object : KoinComponent {
        val instance = WeekWidget()
        private val widgetAccountGate: WidgetAccountGate by inject()

        suspend fun refreshWidget(context: Context, appWidgetId: Int) {
            try {
                val refreshed = widgetAccountGate.withActiveCacheBoundary { boundary ->
                    refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                }
                if (refreshed == null) log.d { "Skipped week widget refresh without an active cache boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh week widget $appWidgetId" }
            }
        }

        internal suspend fun refreshWidgetWithinBoundary(
            context: Context,
            appWidgetId: Int,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceId = manager.getGlanceIds(WeekWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    WidgetBoundaryMarker.write(this, boundary)
                    this[WEEK_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                        this[WEEK_REFRESH_TRIGGER_KEY] ?: 0L,
                        System.currentTimeMillis(),
                    )
                }
            }
            instance.update(context, glanceId)
        }

        suspend fun refreshAllWidgets(context: Context) {
            try {
                val refreshed = widgetAccountGate.withActiveCacheBoundary { boundary ->
                    refreshAllWidgetsWithinBoundary(context, boundary)
                }
                if (refreshed == null) log.d { "Skipped week widget refresh without an active cache boundary" }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                log.e { "Week widget family refresh failed" }
            }
        }

        internal suspend fun refreshAllWidgetsWithinBoundary(
            context: Context,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(WeekWidget::class.java).forEach { glanceId ->
                try {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            WidgetBoundaryMarker.write(this, boundary)
                            this[WEEK_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                                this[WEEK_REFRESH_TRIGGER_KEY] ?: 0L,
                                System.currentTimeMillis(),
                            )
                        }
                    }
                    instance.update(context, glanceId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    log.e { "Week widget instance refresh failed" }
                }
            }
        }

        internal suspend fun blankAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(WeekWidget::class.java).forEach { glanceId ->
                try {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            WidgetBoundaryMarker.clear(this)
                            this[WEEK_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                                this[WEEK_REFRESH_TRIGGER_KEY] ?: 0L,
                                System.currentTimeMillis(),
                            )
                        }
                    }
                    instance.update(context, glanceId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    log.e { "Week widget instance blanking failed" }
                }
            }
        }

        suspend fun navigateWeek(context: Context, appWidgetId: Int, delta: Int) {
            try {
                val navigated = widgetAccountGate.withActiveCacheBoundary {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceId = manager.getGlanceIds(WeekWidget::class.java)
                        .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return@withActiveCacheBoundary
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        val currentOffset = prefs[WEEK_OFFSET_KEY] ?: 0
                        prefs.toMutablePreferences().apply {
                            this[WEEK_OFFSET_KEY] = currentOffset + delta
                            this[WEEK_REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                        }
                    }
                    instance.update(context, glanceId)
                }
                if (navigated == null) log.d { "Skipped week navigation without an active cache boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to navigate week for widget $appWidgetId" }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dateTimeFormatter = LocalizedDateTimeFormatter(context)
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            log.e(e) { "Failed to get widget ID" }
            0
        }

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val refreshTrigger = glancePrefs[WEEK_REFRESH_TRIGGER_KEY] ?: 0L
            val marker = WidgetBoundaryMarker.read(glancePrefs)
            val weekOffset = glancePrefs[WEEK_OFFSET_KEY] ?: 0

            val data = produceState<WeekWidgetData>(
                WeekWidgetData.Loading,
                refreshTrigger,
                marker.accountId,
                marker.boundaryEpoch,
                weekOffset,
            ) {
                value = if (marker.accountId.isNullOrBlank() || marker.boundaryEpoch == null) {
                    WeekWidgetData.Loading
                } else {
                    try {
                        val provider = WidgetDataProvider()
                        provider.withActiveCacheBoundary { boundary ->
                            val prefs = CalendarWidgetPreferences.load(context, appWidgetId)
                            val today = todayLocal()
                            val todayLocalMillis = startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
                            val targetLocalMillis = todayLocalMillis + weekOffset * 7 * MILLIS_PER_DAY
                            val weekStartMillis = startOfWeekLocalMillis(targetLocalMillis)
                            val tasksByDayIndex = provider.getTasksByDayForWeekWithinBoundary(
                                weekStartMillis,
                                WidgetDataProvider.MAX_TASKS_PER_WEEK_DAY,
                            )

                            val days = (0 until 7).map { i ->
                                val dayMillis = weekStartMillis + i * MILLIS_PER_DAY
                                WeekDay(
                                    year = extractYear(dayMillis),
                                    month = extractMonth(dayMillis),
                                    dayOfMonth = extractDay(dayMillis),
                                    dayOfWeekLabel = dateTimeFormatter.formatShortWeekday(dayMillis),
                                    tasks = tasksByDayIndex[i].orEmpty(),
                                )
                            }

                            val weekLabel = dateTimeFormatter.formatWeekRange(
                                weekStartMillis,
                                weekStartMillis + 6 * MILLIS_PER_DAY,
                            )

                            log.v { "Week widget $appWidgetId: $weekLabel, offset=$weekOffset" }
                            WeekWidgetData.Ready(
                                weekLabel = weekLabel,
                                days = days,
                                todayDayOfMonth = today.dayOfMonth,
                                todayMonth = today.monthNumber,
                                prefs = prefs,
                                boundary = boundary,
                            )
                        } ?: WeekWidgetData.Loading
                    } catch (e: Exception) {
                        log.e(e) { "Week widget data fetch failed" }
                        WeekWidgetData.Loading
                    }
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
                is WeekWidgetData.Ready -> if (WidgetBoundaryMarker.matches(marker, d.boundary)) {
                    WeekWidgetContent(
                        weekLabel = d.weekLabel,
                        days = d.days,
                        todayDayOfMonth = d.todayDayOfMonth,
                        todayMonth = d.todayMonth,
                        prefs = d.prefs,
                        appWidgetId = appWidgetId,
                        accountId = d.boundary.accountId,
                        boundaryEpoch = d.boundary.boundaryEpoch,
                    )
                } else {
                    WeekWidgetContent(
                        weekLabel = "...",
                        days = emptyList(),
                        todayDayOfMonth = 0,
                        todayMonth = 0,
                        prefs = CalendarWidgetPreferences(appWidgetId),
                        appWidgetId = appWidgetId,
                    )
                }
            }
        }
    }
}
