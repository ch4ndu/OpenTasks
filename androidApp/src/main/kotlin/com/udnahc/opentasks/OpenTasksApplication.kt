package com.udnahc.opentasks

import android.app.Application
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.di.initKoin
import com.udnahc.opentasks.widget.CalendarWidget
import com.udnahc.opentasks.widget.TaskWidget
import com.udnahc.opentasks.widget.WeekWidget
import com.udnahc.opentasks.util.initializeAndroidDebugBuild
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.java.KoinJavaComponent.get

class OpenTasksApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAndroidDebugBuild(BuildConfig.DEBUG)
        initKoin {
            androidContext(this@OpenTasksApplication)
        }
        // Pre-warm Room database and refresh widgets on background thread
        CoroutineScope(Dispatchers.IO).launch {
            val widgetAccountGate = get<WidgetAccountGate>(WidgetAccountGate::class.java)
            val refreshed = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                get<AppDatabase>(AppDatabase::class.java)
                // Refresh all widget types after reinstall/process restart
                TaskWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                CalendarWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                WeekWidget.refreshAllWidgetsWithinBoundary(this@OpenTasksApplication, boundary)
                true
            }
            if (refreshed != true) {
                TaskWidget.blankAllWidgets(this@OpenTasksApplication)
                CalendarWidget.blankAllWidgets(this@OpenTasksApplication)
                WeekWidget.blankAllWidgets(this@OpenTasksApplication)
            }
        }
    }
}
