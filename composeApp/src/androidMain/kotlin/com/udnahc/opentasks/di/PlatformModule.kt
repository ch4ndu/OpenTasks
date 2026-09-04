package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import com.udnahc.opentasks.data.auth.AccountBoundaryGuard
import com.udnahc.opentasks.data.auth.AccountMutationGate
import com.udnahc.opentasks.data.auth.AndroidKeystoreAuthTokenStore
import com.udnahc.opentasks.data.auth.AuthTokenStore
import com.udnahc.opentasks.data.attachment.AttachmentFileStorage
import com.udnahc.opentasks.data.attachment.AttachmentFileLeaseRecorder
import com.udnahc.opentasks.data.attachment.PlatformAttachmentFileStorage
import com.udnahc.opentasks.data.calendar.AndroidCalendarProvider
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.database.getDatabaseBuilder
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.LocalizedDateTimeFormatter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder(androidContext())
    }
    single<CalendarProvider> { AndroidCalendarProvider(androidContext()) }
    single<DateTimeTextFormatter> { LocalizedDateTimeFormatter(androidContext()) }
    single {
        NotificationScheduler(
            context = androidContext(),
            mutationGate = get<AccountMutationGate>(),
            boundaryGuard = get<AccountBoundaryGuard>(),
        )
    }
    single { NotificationPermissionChecker(androidContext()) }
    single<AttachmentFileStorage> {
        PlatformAttachmentFileStorage(androidContext(), leaseRecorder = get<AttachmentFileLeaseRecorder>())
    }
    single<AuthTokenStore> { AndroidKeystoreAuthTokenStore(androidContext()) }
}
