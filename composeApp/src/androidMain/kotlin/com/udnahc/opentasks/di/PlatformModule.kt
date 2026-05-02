package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.database.getDatabaseBuilder
import com.udnahc.opentasks.data.calendar.AndroidCalendarProvider
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.ui.util.AndroidFileSaver
import com.udnahc.opentasks.ui.util.FileSaver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder(androidContext())
    }
    single<CalendarProvider> { AndroidCalendarProvider(androidContext()) }
    single { NotificationScheduler(androidContext()) }
    single { NotificationPermissionChecker(androidContext()) }
    single<FileSaver> { AndroidFileSaver(androidContext()) }
}
