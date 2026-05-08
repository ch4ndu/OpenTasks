package com.udnahc.opentasks.di

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.udnahc.opentasks.data.database.AppDatabase
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.database.MIGRATION_2_3
import com.udnahc.opentasks.data.database.MIGRATION_3_4
import com.udnahc.opentasks.data.database.MIGRATION_4_5
import com.udnahc.opentasks.data.database.MIGRATION_5_6
import com.udnahc.opentasks.data.database.MIGRATION_6_7
import com.udnahc.opentasks.data.database.MIGRATION_7_8
import com.udnahc.opentasks.data.database.MIGRATION_8_9
import com.udnahc.opentasks.data.database.MIGRATION_9_10
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.notification.AllDayNotificationDismissalStore
import com.udnahc.opentasks.data.repository.CategoryRepositoryImpl
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.data.repository.NoteRepositoryImpl
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.TaskRepositoryImpl
import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.data.repository.TagRepositoryImpl
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.repository.AppSettingsRepositoryImpl
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.data.repository.CountdownRepositoryImpl
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.countdown.AddCountdownAction
import com.udnahc.opentasks.domain.action.countdown.DeleteCountdownAction
import com.udnahc.opentasks.domain.action.countdown.RescheduleAllCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.ScheduleCountdownRemindersAction
import com.udnahc.opentasks.domain.action.countdown.UpdateCountdownAction
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.domain.action.tag.TagTaskAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.DeleteTaskAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateSectionAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.ClearPocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.InitializeSyncAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.settings.SaveTextSizePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveCountdownByIdUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTodayTasksUseCase
import com.udnahc.opentasks.domain.usecase.tag.ObserveTagsForTaskUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForPriorityUseCase
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.FetchCalendarEventsUseCase
import com.udnahc.opentasks.domain.action.task.GenerateCsvExportAction
import com.udnahc.opentasks.domain.action.task.GenerateIcsExportAction
import com.udnahc.opentasks.domain.usecase.task.ParseCsvUseCase
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.data.sync.PocketBaseClientProvider
import com.udnahc.opentasks.data.sync.PocketBaseConnectionVerifier
import com.udnahc.opentasks.data.sync.SyncService
import com.udnahc.opentasks.data.sync.SyncTrigger
import com.udnahc.opentasks.data.sync.adapters.CategorySyncAdapter
import com.udnahc.opentasks.data.sync.adapters.CountdownSyncAdapter
import com.udnahc.opentasks.data.sync.adapters.NoteSyncAdapter
import com.udnahc.opentasks.data.sync.adapters.TagSyncAdapter
import com.udnahc.opentasks.data.sync.adapters.TaskTagSyncAdapter
import com.udnahc.opentasks.data.sync.adapters.TaskSyncAdapter
import com.udnahc.opentasks.viewmodel.AppViewModel
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.CalendarViewModel
import com.udnahc.opentasks.viewmodel.TaskFormViewModel
import com.udnahc.opentasks.domain.usecase.task.ObserveTaskByIdUseCase
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportCsvViewModel
import com.udnahc.opentasks.viewmodel.ImportIcsViewModel
import com.udnahc.opentasks.viewmodel.MatrixViewModel
import com.udnahc.opentasks.viewmodel.CountdownFormViewModel
import com.udnahc.opentasks.viewmodel.CountdownViewModel
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.viewmodel.TaskListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single<AppDatabase> {
        get<androidx.room.RoomDatabase.Builder<AppDatabase>>()
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .setDriver(BundledSQLiteDriver())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) {
                    super.onCreate(connection)
                    // Stable default category name used for import/export and sync lookup.
                    connection.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`id`, `name`, `icon`, `sortOrder`, `isSynced`, `isDeleted`, `createdAt`, `updatedAt`) VALUES ('${AppConstants.DEFAULT_INBOX_ID}', 'Inbox', 'inbox', 0, 0, 0, 0, 0)"
                    )
                }
            })
            .build()
    }
    single { get<AppDatabase>().taskDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().noteDao() }
    single<TaskRepository> { TaskRepositoryImpl(get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get()) }
    single<NoteRepository> { NoteRepositoryImpl(get(), get()) }
    single { get<AppDatabase>().tagDao() }
    single { get<AppDatabase>().appSettingsDao() }
    single { get<AppDatabase>().countdownDao() }
    single<CountdownRepository> { CountdownRepositoryImpl(get(), get()) }
    single<TagRepository> { TagRepositoryImpl(get(), get()) }
    single<AppSettingsRepository> { AppSettingsRepositoryImpl(get()) }
    single { AllDayNotificationDismissalStore(get()) }

    // UseCases
    single { ObserveAllTasksUseCase(get()) }
    single { ObserveTasksByPriorityUseCase(get()) }
    single { ObserveTasksByDayUseCase(get()) }
    factory { ObserveTasksForCategoryUseCase(get()) }
    factory { ObserveTasksForPriorityUseCase(get()) }
    single { ObserveAllCategoriesUseCase(get()) }
    single { ObserveAllNotesUseCase(get()) }
    factory { ObserveNoteByIdUseCase(get()) }
    single { ObserveAllCountdownsUseCase(get()) }
    factory { ObserveCountdownByIdUseCase(get()) }
    factory { ObserveTagsForTaskUseCase(get()) }
    factory { ObserveTaskByIdUseCase(get()) }
    single { ObservePocketBaseUrlUseCase(get()) }
    single { ObserveTodayTasksUseCase(get()) }
    single { ObserveTaskSortOptionUseCase(get()) }
    single { ObserveTaskListViewModeUseCase(get()) }
    single { ObserveThemePreferenceUseCase(get()) }
    single { ObserveTextSizePreferenceUseCase(get()) }
    single { ObserveCalendarViewPreferenceUseCase(get()) }
    single { ObserveCalendarListDisplayModePreferenceUseCase(get()) }
    single { CheckNotificationPermissionUseCase(get()) }
    single { CheckCalendarPermissionUseCase(get()) }
    single { FetchCalendarEventsUseCase(get()) }
    single { ParseCsvUseCase() }
    single { ParseIcsUseCase() }
    single { GenerateCsvExportAction(get(), get()) }
    single { GenerateIcsExportAction(get()) }

    // Actions
    single { AddTaskAction(get(), get()) }
    single { UpdateTaskAction(get(), get()) }
    single { DeleteTaskAction(get(), get()) }
    single { ToggleTaskCompleteAction(get(), get()) }
    single { ToggleTaskStarredAction(get()) }
    single { UpdateSectionAction(get()) }
    single { AddCategoryAction(get()) }
    single { AddNoteAction(get()) }
    single { AddCountdownAction(get(), get()) }
    single { UpdateCountdownAction(get(), get()) }
    single { DeleteCountdownAction(get(), get()) }
    single { UpdateNoteAction(get()) }
    single { DeleteNoteAction(get()) }
    single { AddTagAction(get()) }
    single { TagTaskAction(get()) }
    single { ImportCalendarEventsAction(get(), get(), get(), get(), get()) }
    single { ImportCsvTasksAction(get(), get(), get()) }
    single { ScheduleTaskRemindersAction(get(), get(), get()) }
    single { RescheduleAllRemindersAction(get(), get()) }
    single { ScheduleCountdownRemindersAction(get(), get()) }
    single { RescheduleAllCountdownRemindersAction(get(), get()) }
    single { SavePocketBaseUrlAction(get(), get(), get(), get()) }
    single { ClearPocketBaseUrlAction(get(), get()) }
    single { TriggerSyncAction(get(), get()) }
    single<SyncTrigger> { get<TriggerSyncAction>() }
    single { InitializeSyncAction(get(), get(), get()) }
    single { SaveTaskSortOptionAction(get()) }
    single { SaveTaskListViewModeAction(get()) }
    single { UpdateTaskStatusAction(get(), get()) }
    single { SaveThemePreferenceAction(get()) }
    single { SaveTextSizePreferenceAction(get()) }
    single { SaveCalendarViewPreferenceAction(get()) }
    single { SaveCalendarListDisplayModePreferenceAction(get()) }
    single { ClearLocalDataAction(get(), get(), get(), get(), get()) }

    // Sync
    single { PocketBaseClientProvider() }
    single { TaskSyncAdapter(get()) }
    single { CategorySyncAdapter(get()) }
    single { TagSyncAdapter(get()) }
    single { TaskTagSyncAdapter(get()) }
    single { NoteSyncAdapter(get()) }
    single { CountdownSyncAdapter(get()) }
    single {
        PocketBaseConnectionVerifier(
            get(),
            listOf(
                get<CategorySyncAdapter>(),
                get<TagSyncAdapter>(),
                get<TaskSyncAdapter>(),
                get<TaskTagSyncAdapter>(),
                get<NoteSyncAdapter>(),
                get<CountdownSyncAdapter>(),
            ),
        )
    }
    single {
        SyncService(
            get(),
            listOf(
                get<CategorySyncAdapter>(),
                get<TagSyncAdapter>(),
                get<TaskSyncAdapter>(),
                get<TaskTagSyncAdapter>(),
                get<NoteSyncAdapter>(),
                get<CountdownSyncAdapter>(),
            ),
        )
    }

    // ViewModels
    viewModel { TaskFormViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { MatrixViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { TaskListViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { CalendarViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { NoteViewModel(get(), get(), get(), get(), get()) }
    viewModel { ImportCalendarViewModel(get(), get(), get()) }
    viewModel { ImportIcsViewModel(get(), get()) }
    viewModel { ImportCsvViewModel(get(), get()) }
    viewModel { CountdownViewModel(get(), get()) }
    viewModel { CountdownFormViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AppViewModel(get()) }
}
