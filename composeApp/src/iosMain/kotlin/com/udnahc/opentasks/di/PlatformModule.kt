package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.database.getDatabaseBuilder
import com.udnahc.opentasks.data.auth.AuthTokenStore
import com.udnahc.opentasks.data.auth.IosKeychainAuthTokenStore
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentFileLeaseRecorder
import com.udnahc.opentasks.data.attachment.PlatformAttachmentFileStorage
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.calendar.IosCalendarProvider
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.LocalizedDateTimeFormatter
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder()
    }
    single<CalendarProvider> { IosCalendarProvider() }
    single<DateTimeTextFormatter> { LocalizedDateTimeFormatter() }
    single { NotificationScheduler(get()) }
    single { NotificationPermissionChecker() }
    single<AttachmentFileStorage> {
        PlatformAttachmentFileStorage(leaseRecorder = get<AttachmentFileLeaseRecorder>())
    }
    single<AuthTokenStore> { IosKeychainAuthTokenStore() }
}
