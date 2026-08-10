package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.database.getDatabaseBuilder
import com.udnahc.opentasks.data.auth.AndroidKeystoreAuthTokenStore
import com.udnahc.opentasks.data.auth.AuthTokenStore
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.PlatformAttachmentFileStorage
import com.udnahc.opentasks.data.calendar.AndroidCalendarProvider
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.notification.NotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder(androidContext())
    }
    single<CalendarProvider> { AndroidCalendarProvider(androidContext()) }
    single { NotificationScheduler(androidContext(), get()) }
    single { NotificationPermissionChecker(androidContext()) }
    single<AttachmentFileStorage> { PlatformAttachmentFileStorage(androidContext()) }
    single<AuthTokenStore> { AndroidKeystoreAuthTokenStore(androidContext()) }
}
