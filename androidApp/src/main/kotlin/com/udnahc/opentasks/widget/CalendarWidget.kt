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
import com.udnahc.opentasks.data.extensions.daysInMonth
import com.udnahc.opentasks.data.extensions.dayOfWeekIndex
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("CalendarWidget")

internal val CAL_REFRESH_TRIGGER_KEY = longPreferencesKey("cal_refresh_trigger")
internal val CAL_DISPLAYED_YEAR_KEY = intPreferencesKey("cal_displayed_year")
internal val CAL_DISPLAYED_MONTH_KEY = intPreferencesKey("cal_displayed_month")

private val MONTH_NAMES_FULL = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private sealed class CalendarWidgetData {
    data object Loading : CalendarWidgetData()
    data class Ready(
        val year: Int,
        val month: Int,
        val monthLabel: String,
        val daysInMonth: Int,
        val firstDayOfWeekOffset: Int,
        val tasksByDay: Map<Int, List<CalendarDayTask>>,
        val todayDay: Int,
        val prefs: CalendarWidgetPreferences,
        val boundary: AccountBoundary,
    ) : CalendarWidgetData()
}

class CalendarWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object : KoinComponent {
        val instance = CalendarWidget()
        private val widgetAccountGate: WidgetAccountGate by inject()

        suspend fun refreshWidget(context: Context, appWidgetId: Int) {
            try {
                val refreshed = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                    refreshWidgetWithinBoundary(context, appWidgetId, boundary)
                }
                if (refreshed == null) log.d { "Skipped calendar widget refresh without an authenticated boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh calendar widget $appWidgetId" }
            }
        }

        internal suspend fun refreshWidgetWithinBoundary(
            context: Context,
            appWidgetId: Int,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceId = manager.getGlanceIds(CalendarWidget::class.java)
                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    WidgetBoundaryMarker.write(this, boundary)
                    this[CAL_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                        this[CAL_REFRESH_TRIGGER_KEY] ?: 0L,
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
                if (refreshed == null) log.d { "Skipped calendar widget refresh without an authenticated boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to refresh all calendar widgets" }
            }
        }

        internal suspend fun refreshAllWidgetsWithinBoundary(
            context: Context,
            boundary: AccountBoundary,
        ) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(CalendarWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetBoundaryMarker.write(this, boundary)
                        this[CAL_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                            this[CAL_REFRESH_TRIGGER_KEY] ?: 0L,
                            System.currentTimeMillis(),
                        )
                    }
                }
                instance.update(context, glanceId)
            }
        }

        internal suspend fun blankAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(CalendarWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetBoundaryMarker.clear(this)
                        this[CAL_REFRESH_TRIGGER_KEY] = WidgetBoundaryMarker.nextTrigger(
                            this[CAL_REFRESH_TRIGGER_KEY] ?: 0L,
                            System.currentTimeMillis(),
                        )
                    }
                }
                instance.update(context, glanceId)
            }
        }

        suspend fun navigateMonth(context: Context, appWidgetId: Int, delta: Int) {
            try {
                val navigated = widgetAccountGate.withAuthenticatedBoundary {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceId = manager.getGlanceIds(CalendarWidget::class.java)
                        .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return@withAuthenticatedBoundary
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        val today = todayLocal()
                        val currentYear = prefs[CAL_DISPLAYED_YEAR_KEY] ?: today.year
                        val currentMonth = prefs[CAL_DISPLAYED_MONTH_KEY] ?: today.monthNumber
                        var newMonth = currentMonth + delta
                        var newYear = currentYear
                        if (newMonth < 1) {
                            newMonth = 12
                            newYear--
                        } else if (newMonth > 12) {
                            newMonth = 1
                            newYear++
                        }
                        prefs.toMutablePreferences().apply {
                            this[CAL_DISPLAYED_YEAR_KEY] = newYear
                            this[CAL_DISPLAYED_MONTH_KEY] = newMonth
                            this[CAL_REFRESH_TRIGGER_KEY] = System.currentTimeMillis()
                        }
                    }
                    instance.update(context, glanceId)
                }
                if (navigated == null) log.d { "Skipped calendar navigation without an authenticated boundary" }
            } catch (e: Exception) {
                log.e(e) { "Failed to navigate month for widget $appWidgetId" }
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
            val refreshTrigger = glancePrefs[CAL_REFRESH_TRIGGER_KEY] ?: 0L
            val marker = WidgetBoundaryMarker.read(glancePrefs)
            val today = todayLocal()
            val displayedYear = glancePrefs[CAL_DISPLAYED_YEAR_KEY] ?: today.year
            val displayedMonth = glancePrefs[CAL_DISPLAYED_MONTH_KEY] ?: today.monthNumber

            val data = produceState<CalendarWidgetData>(
                CalendarWidgetData.Loading,
                refreshTrigger,
                marker.accountId,
                marker.boundaryEpoch,
                displayedYear,
                displayedMonth,
            ) {
                value = if (marker.accountId.isNullOrBlank() || marker.boundaryEpoch == null) {
                    CalendarWidgetData.Loading
                } else {
                    try {
                        val provider = WidgetDataProvider()
                        provider.withAuthenticatedBoundary { boundary ->
                            val prefs = CalendarWidgetPreferences.load(context, appWidgetId)
                            val tasksByDay = provider.getTasksByDayForMonthWithinBoundary(
                                displayedYear,
                                displayedMonth,
                                WidgetDataProvider.MAX_TASKS_PER_DAY,
                            )
                            val days = daysInMonth(displayedYear, displayedMonth)
                            val firstDayOffset = dayOfWeekIndex(displayedYear, displayedMonth, 1)
                            val monthLabel = "${MONTH_NAMES_FULL[displayedMonth - 1]} $displayedYear"
                            val todayDay = if (displayedYear == today.year && displayedMonth == today.monthNumber) {
                                today.dayOfMonth
                            } else {
                                0
                            }
                            log.v { "Calendar widget $appWidgetId: $monthLabel, ${tasksByDay.size} days with tasks" }
                            CalendarWidgetData.Ready(
                                year = displayedYear,
                                month = displayedMonth,
                                monthLabel = monthLabel,
                                daysInMonth = days,
                                firstDayOfWeekOffset = firstDayOffset,
                                tasksByDay = tasksByDay,
                                todayDay = todayDay,
                                prefs = prefs,
                                boundary = boundary,
                            )
                        } ?: CalendarWidgetData.Loading
                    } catch (e: Exception) {
                        log.e(e) { "Calendar widget data fetch failed" }
                        CalendarWidgetData.Loading
                    }
                }
            }

            when (val d = data.value) {
                is CalendarWidgetData.Loading -> {
                    val prefs = CalendarWidgetPreferences(appWidgetId)
                    val days = daysInMonth(displayedYear, displayedMonth)
                    val firstDayOffset = dayOfWeekIndex(displayedYear, displayedMonth, 1)
                    val monthLabel = "${MONTH_NAMES_FULL[displayedMonth - 1]} $displayedYear"
                    CalendarWidgetContent(
                        year = displayedYear,
                        month = displayedMonth,
                        monthLabel = monthLabel,
                        daysInMonth = days,
                        firstDayOfWeekOffset = firstDayOffset,
                        tasksByDay = emptyMap(),
                        todayDay = 0,
                        prefs = prefs,
                        appWidgetId = appWidgetId,
                    )
                }
                is CalendarWidgetData.Ready -> if (WidgetBoundaryMarker.matches(marker, d.boundary)) {
                    CalendarWidgetContent(
                        year = d.year,
                        month = d.month,
                        monthLabel = d.monthLabel,
                        daysInMonth = d.daysInMonth,
                        firstDayOfWeekOffset = d.firstDayOfWeekOffset,
                        tasksByDay = d.tasksByDay,
                        todayDay = d.todayDay,
                        prefs = d.prefs,
                        appWidgetId = appWidgetId,
                        accountId = d.boundary.accountId,
                        boundaryEpoch = d.boundary.boundaryEpoch,
                    )
                } else {
                    val prefs = CalendarWidgetPreferences(appWidgetId)
                    val days = daysInMonth(displayedYear, displayedMonth)
                    val firstDayOffset = dayOfWeekIndex(displayedYear, displayedMonth, 1)
                    val monthLabel = "${MONTH_NAMES_FULL[displayedMonth - 1]} $displayedYear"
                    CalendarWidgetContent(
                        year = displayedYear,
                        month = displayedMonth,
                        monthLabel = monthLabel,
                        daysInMonth = days,
                        firstDayOfWeekOffset = firstDayOffset,
                        tasksByDay = emptyMap(),
                        todayDay = 0,
                        prefs = prefs,
                        appWidgetId = appWidgetId,
                    )
                }
            }
        }
    }
}
