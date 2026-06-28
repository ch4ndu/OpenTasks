package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.database.getDatabaseBuilder
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.PlatformAttachmentFileStorage
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.calendar.IosCalendarProvider
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.ui.util.FileSaver
import com.udnahc.opentasks.ui.util.IosFileSaver
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder()
    }
    single<CalendarProvider> { IosCalendarProvider() }
    single { NotificationScheduler() }
    single { NotificationPermissionChecker() }
    single<FileSaver> { IosFileSaver() }
    single<AttachmentFileStorage> { PlatformAttachmentFileStorage() }
}
