package com.udnahc.opentasks

import android.app.Application
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.notification.refreshNotificationWidgetsIndependently
import com.udnahc.opentasks.di.initKoin
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import com.udnahc.opentasks.util.initializeAndroidDebugBuild
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.java.KoinJavaComponent.get
import org.lighthousegames.logging.logging

private val log = logging("OpenTasksApplication")

class OpenTasksApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeAndroidDebugBuild(BuildConfig.DEBUG)
        initKoin {
            androidContext(this@OpenTasksApplication)
        }
        launchStartupWork("Database prewarm failed") {
            get<AppDatabase>(AppDatabase::class.java)
        }
        launchStartupWork("Widget startup maintenance failed") {
            val refreshed = try {
                val widgetAccountGate = get<WidgetAccountGate>(WidgetAccountGate::class.java)
                widgetAccountGate.withActiveCacheBoundary { boundary ->
                    refreshNotificationWidgetsIndependently(
                        refreshTaskWidget = {
                            TaskWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                        },
                        refreshCalendarWidget = {
                            CalendarWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                        },
                        refreshWeekWidget = {
                            WeekWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                log.e { "Active cache restoration failed during widget startup" }
                null
            }
            if (refreshed == null) {
                refreshNotificationWidgetsIndependently(
                    refreshTaskWidget = { TaskWidget.blankAllWidgets(this@OpenTasksApplication) },
                    refreshCalendarWidget = { CalendarWidget.blankAllWidgets(this@OpenTasksApplication) },
                    refreshWeekWidget = { WeekWidget.blankAllWidgets(this@OpenTasksApplication) },
                )
            }
        }
    }

    private fun launchStartupWork(
        failureMessage: String,
        block: suspend () -> Unit,
    ) {
        applicationScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                log.e { failureMessage }
            }
        }
    }
}
